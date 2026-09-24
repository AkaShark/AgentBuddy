//! Persistent SSH host-key fingerprint trust store.
//!
//! Storage itself is platform-owned (iOS Keychain / Android EncryptedSharedPreferences).
//! Rust owns the policy and the lookup-during-connect; platforms implement
//! [`TerminalSshTrustBackend`] to read/write the encrypted store.
//!
//! Every SSH connect path (terminal, guided / non-guided connect, SSH bridge
//! sessions, reconnect) runs [`SshHostKeyPolicy`]. The russh host-key
//! callback accepts a key only when:
//!
//! - a pin exists and the remote fingerprint matches it, OR
//! - no pin exists *and* `accept_unknown_host` is true.
//!
//! A pin store that cannot be read ([`SshTrustLookup::Unavailable`]) refuses
//! the key outright, so a lost or unreadable pin is never silently replaced by
//! whatever key the server presents (no trust-on-first-use in that case). The
//! user can explicitly forget that pin ([`TerminalSshTrustStore::unpin`]) and
//! retry, which then treats the host as new.
//!
//! When the callback rejects, [`SshHostKeyPolicy::surface_rejection`] turns the
//! resulting [`SshError::HostKeyVerification`] into a typed
//! [`SshError::HostKeyRejected`]. Each connect path maps that to a typed
//! [`AppSshHostKeyMismatch`] on its UniFFI boundary (`ClientError`,
//! `TerminalError`, or `AppConnectionProgressSnapshot::host_key_mismatch`), so
//! platforms never parse error strings. To trust a key the user approved,
//! platforms call [`TerminalSshTrustStore::pin`] with the mismatch's exact
//! host, port and fingerprint and retry; the retry then succeeds only if the
//! server still presents that key. For
//! [`AppSshHostKeyMismatchKind::TrustStoreUnavailable`] they offer to forget
//! the unreadable pin ([`TerminalSshTrustStore::unpin`]) and retry instead.
//!
//! Paths without a per-session store consult the process-wide store
//! registered once at startup through [`set_ssh_trust_store`]; when no store
//! is registered they keep the legacy behavior of trusting whatever
//! `accept_unknown_host` says.

use std::sync::{Arc, Mutex, RwLock};

use futures::future::BoxFuture;
use tracing::warn;

use crate::ssh::{SshError, SshHostKeyRejection};

static REGISTERED_TRUST_STORE: RwLock<Option<Arc<TerminalSshTrustStore>>> = RwLock::new(None);

/// Register the process-wide host-key trust store consulted by every SSH
/// connect path. Platforms call this once at startup; passing `None`
/// restores the legacy "no pinning" behavior.
#[uniffi::export]
pub fn set_ssh_trust_store(store: Option<Arc<TerminalSshTrustStore>>) {
    *REGISTERED_TRUST_STORE
        .write()
        .unwrap_or_else(|poisoned| poisoned.into_inner()) = store;
}

pub(crate) fn registered_ssh_trust_store() -> Option<Arc<TerminalSshTrustStore>> {
    REGISTERED_TRUST_STORE
        .read()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .clone()
}

/// Result of reading the pinned host key for one `host:port`.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum SshTrustLookup {
    /// `fingerprint` is pinned for the host.
    Pinned { fingerprint: String },
    /// Nothing is pinned for the host.
    NotPinned,
    /// The store could not be read. Connects to the host are refused rather
    /// than treating it as new and pinning whatever key it presents.
    Unavailable { detail: String },
}

/// Which host-key check a refused SSH connect failed.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AppSshHostKeyMismatchKind {
    /// A key is pinned for the host and the server presented a different one.
    Changed,
    /// Nothing is pinned for the host and the connect did not allow unknown hosts.
    Unknown,
    /// The saved pin for the host could not be read, so the key could not be
    /// checked. Not trustable as-is: platforms offer to forget the saved pin
    /// ([`TerminalSshTrustStore::unpin`]) and retry.
    TrustStoreUnavailable,
}

/// A refused SSH connect whose host key the user may choose to trust
/// (`Changed` / `Unknown`): trusting pins exactly `fingerprint` for
/// `host`/`port` through [`TerminalSshTrustStore::pin`] and retries the
/// connect. For `TrustStoreUnavailable` the user may instead forget the
/// unreadable pin through [`TerminalSshTrustStore::unpin`] and retry.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AppSshHostKeyMismatch {
    pub kind: AppSshHostKeyMismatchKind,
    /// Normalized host, exactly as used for the pin lookup.
    pub host: String,
    pub port: u16,
    /// Fingerprint the server presented (`SHA256:...`).
    pub fingerprint: String,
}

impl AppSshHostKeyMismatch {
    /// The typed boundary mismatch behind `rejection`.
    pub(crate) fn from_rejection(rejection: &SshHostKeyRejection) -> Self {
        let (kind, host, port, fingerprint) = match rejection {
            SshHostKeyRejection::Changed {
                host,
                port,
                fingerprint,
            } => (AppSshHostKeyMismatchKind::Changed, host, port, fingerprint),
            SshHostKeyRejection::Unknown {
                host,
                port,
                fingerprint,
            } => (AppSshHostKeyMismatchKind::Unknown, host, port, fingerprint),
            SshHostKeyRejection::TrustStoreUnavailable {
                host,
                port,
                fingerprint,
                ..
            } => (
                AppSshHostKeyMismatchKind::TrustStoreUnavailable,
                host,
                port,
                fingerprint,
            ),
        };
        Self {
            kind,
            host: host.clone(),
            port: *port,
            fingerprint: fingerprint.clone(),
        }
    }
}

impl std::fmt::Display for AppSshHostKeyMismatch {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self.kind {
            AppSshHostKeyMismatchKind::Changed | AppSshHostKeyMismatchKind::Unknown => write!(
                f,
                "SSH host key mismatch for {}:{}: {}",
                self.host, self.port, self.fingerprint
            ),
            AppSshHostKeyMismatchKind::TrustStoreUnavailable => write!(
                f,
                "Saved SSH host key for {}:{} could not be read; refusing to connect",
                self.host, self.port
            ),
        }
    }
}

/// Russh host-key callback shape accepted by [`crate::ssh::SshClient::connect`].
pub(crate) type SshHostKeyCallback = Box<dyn Fn(&str) -> BoxFuture<'static, bool> + Send + Sync>;

/// Trust-on-first-use host-key check for one SSH connect attempt.
///
/// - A pinned fingerprint must match, even when `accept_unknown_host` is true.
/// - With nothing pinned, the key is accepted only when `accept_unknown_host`
///   is true; [`Self::pin_on_first_use`] then records it.
/// - An unreadable pin store refuses every key and never pins.
/// - Without a trust store the decision is just `accept_unknown_host`.
pub(crate) struct SshHostKeyPolicy {
    store: Option<Arc<TerminalSshTrustStore>>,
    host: String,
    port: u16,
    pin: SshTrustLookup,
    accept_unknown_host: bool,
    /// The key accepted for this connection. Later checks (russh re-verifies
    /// the host key on rekey) accept only this same key.
    session_key: Arc<Mutex<Option<String>>>,
}

impl SshHostKeyPolicy {
    /// Policy backed by the store registered via [`set_ssh_trust_store`].
    pub(crate) fn registered(host: &str, port: u16, accept_unknown_host: bool) -> Self {
        Self::new(
            registered_ssh_trust_store(),
            host,
            port,
            accept_unknown_host,
        )
    }

    pub(crate) fn new(
        store: Option<Arc<TerminalSshTrustStore>>,
        host: &str,
        port: u16,
        accept_unknown_host: bool,
    ) -> Self {
        let host = normalize_host(host);
        let pin = match store.as_ref() {
            Some(store) => store.lookup(&host, port),
            None => SshTrustLookup::NotPinned,
        };
        if let SshTrustLookup::Unavailable { detail } = &pin {
            warn!(
                "ssh host-key trust store unavailable host={} port={} detail={}; refusing connect",
                host, port, detail
            );
        }
        Self {
            store,
            host,
            port,
            pin,
            accept_unknown_host,
            session_key: Arc::new(Mutex::new(None)),
        }
    }

    /// Host-key callback for [`crate::ssh::SshClient::connect`]. The first
    /// accepted key becomes the session key: later checks on the same
    /// connection accept only that key, and a successful first connect pins it.
    pub(crate) fn callback(&self) -> SshHostKeyCallback {
        let pin = self.pin.clone();
        let accept_unknown_host = self.accept_unknown_host;
        let session_key = Arc::clone(&self.session_key);
        Box::new(move |fingerprint| {
            let mut session_key = session_key.lock().unwrap_or_else(|p| p.into_inner());
            let accepted = match session_key.as_deref() {
                Some(accepted) => accepted == fingerprint,
                None => {
                    let accepted = match &pin {
                        SshTrustLookup::Pinned {
                            fingerprint: expected,
                        } => expected == fingerprint,
                        SshTrustLookup::NotPinned => accept_unknown_host,
                        SshTrustLookup::Unavailable { .. } => false,
                    };
                    if accepted {
                        *session_key = Some(fingerprint.to_string());
                    }
                    accepted
                }
            };
            Box::pin(async move { accepted })
        })
    }

    /// Pin the key accepted during a successful handshake when the host had
    /// no pin yet and the caller allowed unknown hosts. The store is re-read
    /// first, so a pin written meanwhile (e.g. by a concurrent connect or a
    /// user's "Trust") is never overwritten.
    pub(crate) fn pin_on_first_use(&self) {
        if self.pin != SshTrustLookup::NotPinned || !self.accept_unknown_host {
            return;
        }
        let Some(store) = self.store.as_ref() else {
            return;
        };
        let accepted = self
            .session_key
            .lock()
            .unwrap_or_else(|p| p.into_inner())
            .clone();
        let Some(fingerprint) = accepted else {
            return;
        };
        match store.lookup(&self.host, self.port) {
            SshTrustLookup::NotPinned => store.pin(self.host.clone(), self.port, fingerprint),
            SshTrustLookup::Pinned {
                fingerprint: pinned,
            } if pinned == fingerprint => {}
            current => warn!(
                "ssh host-key pin for host={} port={} changed during connect ({:?}); not pinning",
                self.host, self.port, current
            ),
        }
    }

    /// Why the key `fingerprint` was refused, given this connect's pin state.
    pub(crate) fn rejection(&self, fingerprint: &str) -> SshHostKeyRejection {
        let host = self.host.clone();
        let port = self.port;
        let fingerprint = fingerprint.to_string();
        match &self.pin {
            SshTrustLookup::Pinned { .. } => SshHostKeyRejection::Changed {
                host,
                port,
                fingerprint,
            },
            SshTrustLookup::NotPinned => SshHostKeyRejection::Unknown {
                host,
                port,
                fingerprint,
            },
            SshTrustLookup::Unavailable { detail } => SshHostKeyRejection::TrustStoreUnavailable {
                host,
                port,
                fingerprint,
                detail: detail.clone(),
            },
        }
    }

    /// Classify a host-key rejection as [`SshError::HostKeyRejected`] so each
    /// connect path can map it to its typed boundary error.
    pub(crate) fn surface_rejection(&self, error: SshError) -> SshError {
        match error {
            SshError::HostKeyVerification { fingerprint } => {
                SshError::HostKeyRejected(self.rejection(&fingerprint))
            }
            other => other,
        }
    }
}

/// Platform-implemented persistent storage for pinned host fingerprints.
///
/// iOS implements this on top of the Keychain; Android on top of
/// `androidx.security:security-crypto` EncryptedSharedPreferences. The
/// backend MUST be synchronous and side-effect free with respect to other
/// terminal operations — the trust store consults it on every connect.
#[uniffi::export(callback_interface)]
pub trait TerminalSshTrustBackend: Send + Sync {
    /// Look up the pinned SHA-256 fingerprint for `host:port`. Return
    /// [`SshTrustLookup::NotPinned`] only when no pin is recorded, and
    /// [`SshTrustLookup::Unavailable`] when the store cannot be read.
    fn read(&self, host: String, port: u16) -> SshTrustLookup;
    /// Persist `fingerprint` as the pin for `host:port`, overwriting any
    /// previously stored fingerprint.
    fn write(&self, host: String, port: u16, fingerprint: String);
    /// Remove any pin for `host:port`. Idempotent.
    fn remove(&self, host: String, port: u16);
}

/// UniFFI object wrapping a platform [`TerminalSshTrustBackend`].
///
/// Held by the terminal backend at session-open time. Cheap to construct;
/// the actual storage round-trip lives in the platform-supplied backend.
#[derive(uniffi::Object)]
pub struct TerminalSshTrustStore {
    backend: Arc<dyn TerminalSshTrustBackend>,
}

#[uniffi::export]
impl TerminalSshTrustStore {
    #[uniffi::constructor]
    pub fn new(backend: Box<dyn TerminalSshTrustBackend>) -> Self {
        Self {
            backend: Arc::from(backend),
        }
    }

    /// Return the pinned SHA-256 fingerprint for the given host/port, if one
    /// can be read. Connect policy uses the full [`SshTrustLookup`] instead.
    pub fn pinned(&self, host: String, port: u16) -> Option<String> {
        match self.lookup(&host, port) {
            SshTrustLookup::Pinned { fingerprint } => Some(fingerprint),
            SshTrustLookup::NotPinned | SshTrustLookup::Unavailable { .. } => None,
        }
    }

    /// Record `fingerprint` as the trusted pin for the given host/port,
    /// replacing any previous pin.
    pub fn pin(&self, host: String, port: u16, fingerprint: String) {
        let host = normalize_host(&host);
        self.backend.write(host, port, fingerprint);
    }

    /// Remove any pin for the given host/port. Safe to call when no pin
    /// exists.
    pub fn unpin(&self, host: String, port: u16) {
        let host = normalize_host(&host);
        self.backend.remove(host, port);
    }
}

impl TerminalSshTrustStore {
    /// Internal accessor used by [`SshHostKeyPolicy`] to read a pin without
    /// going through the UniFFI surface.
    pub(crate) fn lookup(&self, host: &str, port: u16) -> SshTrustLookup {
        self.backend.read(normalize_host(host), port)
    }
}

/// Lowercase + strip bracket / scope-id noise so equality matches the way
/// `russh` normalizes the connect address.
pub(crate) fn normalize_host(host: &str) -> String {
    let mut value = host
        .trim()
        .trim_matches('[')
        .trim_matches(']')
        .to_string();
    value = value.replace("%25", "%");
    if !value.contains(':') {
        if let Some(idx) = value.find('%') {
            value.truncate(idx);
        }
    }
    value.to_lowercase()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use std::sync::Mutex;

    #[derive(Default)]
    struct InMemoryBackend {
        store: Mutex<HashMap<(String, u16), String>>,
    }

    impl TerminalSshTrustBackend for InMemoryBackend {
        fn read(&self, host: String, port: u16) -> SshTrustLookup {
            match self.store.lock().unwrap().get(&(host, port)) {
                Some(fingerprint) => SshTrustLookup::Pinned {
                    fingerprint: fingerprint.clone(),
                },
                None => SshTrustLookup::NotPinned,
            }
        }
        fn write(&self, host: String, port: u16, fingerprint: String) {
            self.store
                .lock()
                .unwrap()
                .insert((host, port), fingerprint);
        }
        fn remove(&self, host: String, port: u16) {
            self.store.lock().unwrap().remove(&(host, port));
        }
    }

    fn make_store() -> TerminalSshTrustStore {
        TerminalSshTrustStore::new(Box::new(InMemoryBackend::default()))
    }

    /// Backend whose reads always fail, recording any write attempt.
    #[derive(Default)]
    struct UnreadableBackend {
        writes: Arc<Mutex<Vec<(String, u16, String)>>>,
    }

    impl TerminalSshTrustBackend for UnreadableBackend {
        fn read(&self, _host: String, _port: u16) -> SshTrustLookup {
            SshTrustLookup::Unavailable {
                detail: "keychain locked".into(),
            }
        }
        fn write(&self, host: String, port: u16, fingerprint: String) {
            self.writes.lock().unwrap().push((host, port, fingerprint));
        }
        fn remove(&self, _host: String, _port: u16) {}
    }

    #[test]
    fn pin_then_lookup_returns_same_fingerprint() {
        let store = make_store();
        store.pin("example.com".into(), 22, "SHA256:abc".into());
        assert_eq!(
            store.pinned("example.com".into(), 22),
            Some("SHA256:abc".into())
        );
    }

    #[test]
    fn unpinned_host_returns_none() {
        let store = make_store();
        assert_eq!(store.pinned("missing.example".into(), 22), None);
    }

    #[test]
    fn unpin_clears_only_matching_entry() {
        let store = make_store();
        store.pin("example.com".into(), 22, "SHA256:abc".into());
        store.pin("other.example".into(), 22, "SHA256:def".into());
        store.unpin("example.com".into(), 22);
        assert_eq!(store.pinned("example.com".into(), 22), None);
        assert_eq!(
            store.pinned("other.example".into(), 22),
            Some("SHA256:def".into())
        );
    }

    #[test]
    fn pin_overwrites_previous_value() {
        let store = make_store();
        store.pin("example.com".into(), 22, "SHA256:abc".into());
        store.pin("example.com".into(), 22, "SHA256:xyz".into());
        assert_eq!(
            store.pinned("example.com".into(), 22),
            Some("SHA256:xyz".into())
        );
    }

    #[test]
    fn lookup_normalizes_host_casing_and_brackets() {
        let store = make_store();
        store.pin("Example.COM".into(), 22, "SHA256:abc".into());
        assert_eq!(
            store.pinned("[example.com]".into(), 22),
            Some("SHA256:abc".into())
        );
        assert_eq!(
            store.lookup("Example.COM", 22),
            SshTrustLookup::Pinned {
                fingerprint: "SHA256:abc".into()
            }
        );
    }

    #[test]
    fn unpin_is_idempotent() {
        let store = make_store();
        store.unpin("nothing.example".into(), 22);
        store.unpin("nothing.example".into(), 22);
    }

    /// Serializes the tests that touch the process-wide registered store.
    static REGISTRY_TEST_LOCK: Mutex<()> = Mutex::new(());

    fn make_shared_store() -> Arc<TerminalSshTrustStore> {
        Arc::new(make_store())
    }

    async fn run_callback(policy: &SshHostKeyPolicy, fingerprint: &str) -> bool {
        (policy.callback())(fingerprint).await
    }

    #[tokio::test]
    async fn policy_accepts_matching_pinned_key() {
        let store = make_shared_store();
        store.pin("example.com".into(), 22, "SHA256:abc".into());
        // Even a caller that refuses unknown hosts connects to a pinned match.
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "Example.com", 22, false);
        assert!(run_callback(&policy, "SHA256:abc").await);
        policy.pin_on_first_use();
        assert_eq!(
            store.pinned("example.com".into(), 22),
            Some("SHA256:abc".into())
        );
    }

    #[tokio::test]
    async fn policy_rejects_changed_key_even_when_accepting_unknown_hosts() {
        let store = make_shared_store();
        store.pin("example.com".into(), 2222, "SHA256:abc".into());
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "Example.com", 2222, true);
        assert!(!run_callback(&policy, "SHA256:xyz").await);
        let changed = SshHostKeyRejection::Changed {
            host: "example.com".into(),
            port: 2222,
            fingerprint: "SHA256:xyz".into(),
        };
        assert_eq!(policy.rejection("SHA256:xyz"), changed);
        match policy.surface_rejection(SshError::HostKeyVerification {
            fingerprint: "SHA256:xyz".into(),
        }) {
            SshError::HostKeyRejected(rejection) => assert_eq!(rejection, changed),
            other => panic!("expected HostKeyRejected, got {other:?}"),
        }
        assert_eq!(
            AppSshHostKeyMismatch::from_rejection(&changed),
            AppSshHostKeyMismatch {
                kind: AppSshHostKeyMismatchKind::Changed,
                host: "example.com".into(),
                port: 2222,
                fingerprint: "SHA256:xyz".into(),
            }
        );
        // Unrelated errors pass through untouched.
        assert!(matches!(
            policy.surface_rejection(SshError::Timeout),
            SshError::Timeout
        ));
        // A rejected connect must never overwrite the existing pin.
        policy.pin_on_first_use();
        assert_eq!(
            store.pinned("example.com".into(), 2222),
            Some("SHA256:abc".into())
        );
    }

    #[tokio::test]
    async fn first_use_pin_does_not_overwrite_pin_written_during_connect() {
        let store = make_shared_store();
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "race.example", 22, true);
        assert!(run_callback(&policy, "SHA256:first").await);
        // Another connect (or the user's "Trust") pins a key meanwhile.
        store.pin("race.example".into(), 22, "SHA256:other".into());
        policy.pin_on_first_use();
        assert_eq!(
            store.pinned("race.example".into(), 22),
            Some("SHA256:other".into())
        );
    }

    #[tokio::test]
    async fn policy_pins_first_seen_key_when_accepting_unknown_hosts() {
        let store = make_shared_store();
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "[new.example]", 22, true);
        assert!(run_callback(&policy, "SHA256:first").await);
        policy.pin_on_first_use();
        assert_eq!(
            store.pinned("new.example".into(), 22),
            Some("SHA256:first".into())
        );

        // The next connect to the same host now requires the pinned key.
        let next = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "new.example", 22, true);
        assert!(run_callback(&next, "SHA256:first").await);
        assert!(!run_callback(&next, "SHA256:other").await);
    }

    #[tokio::test]
    async fn policy_refuses_unpinned_host_when_not_accepting_unknown_hosts() {
        let store = make_shared_store();
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "new.example", 22, false);
        assert!(!run_callback(&policy, "SHA256:first").await);
        let unknown = policy.rejection("SHA256:first");
        assert_eq!(
            unknown,
            SshHostKeyRejection::Unknown {
                host: "new.example".into(),
                port: 22,
                fingerprint: "SHA256:first".into(),
            }
        );
        assert_eq!(
            AppSshHostKeyMismatch::from_rejection(&unknown).kind,
            AppSshHostKeyMismatchKind::Unknown
        );
        policy.pin_on_first_use();
        assert_eq!(store.pinned("new.example".into(), 22), None);
    }

    #[tokio::test]
    async fn policy_without_registered_store_keeps_legacy_behavior() {
        let _guard = REGISTRY_TEST_LOCK.lock().unwrap_or_else(|p| p.into_inner());
        set_ssh_trust_store(None);

        let accepting = SshHostKeyPolicy::registered("legacy.example", 22, true);
        assert!(run_callback(&accepting, "SHA256:any").await);
        accepting.pin_on_first_use();
        let accepting_other = SshHostKeyPolicy::registered("legacy.example", 22, true);
        assert!(run_callback(&accepting_other, "SHA256:other").await);

        let refusing = SshHostKeyPolicy::registered("legacy.example", 22, false);
        assert!(!run_callback(&refusing, "SHA256:any").await);
    }

    #[tokio::test]
    async fn registered_store_backs_connect_policy() {
        let _guard = REGISTRY_TEST_LOCK.lock().unwrap_or_else(|p| p.into_inner());
        let store = make_shared_store();
        set_ssh_trust_store(Some(Arc::clone(&store)));

        let first = SshHostKeyPolicy::registered("registered.example", 22, true);
        assert!(run_callback(&first, "SHA256:abc").await);
        first.pin_on_first_use();
        assert_eq!(
            store.pinned("registered.example".into(), 22),
            Some("SHA256:abc".into())
        );
        let changed = SshHostKeyPolicy::registered("registered.example", 22, true);
        assert!(!run_callback(&changed, "SHA256:xyz").await);

        set_ssh_trust_store(None);
        assert!(registered_ssh_trust_store().is_none());
    }

    #[tokio::test]
    async fn policy_refuses_and_never_pins_when_store_is_unreadable() {
        let backend = UnreadableBackend::default();
        let writes = Arc::clone(&backend.writes);
        let store = Arc::new(TerminalSshTrustStore::new(Box::new(backend)));
        // Even a caller that accepts unknown hosts must not trust-on-first-use
        // when it cannot tell whether a pin already exists.
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "lost.example", 22, true);
        assert!(!run_callback(&policy, "SHA256:any").await);
        policy.pin_on_first_use();
        assert!(writes.lock().unwrap().is_empty());
        let rejection = policy.rejection("SHA256:any");
        assert_eq!(
            rejection,
            SshHostKeyRejection::TrustStoreUnavailable {
                host: "lost.example".into(),
                port: 22,
                fingerprint: "SHA256:any".into(),
                detail: "keychain locked".into(),
            }
        );
        // Not something the user can "trust": surfaced with its own kind so
        // platforms offer to forget the unreadable pin instead.
        assert_eq!(
            AppSshHostKeyMismatch::from_rejection(&rejection).kind,
            AppSshHostKeyMismatchKind::TrustStoreUnavailable
        );
        assert_eq!(store.pinned("lost.example".into(), 22), None);
    }

    #[tokio::test]
    async fn trusting_approved_fingerprint_only_accepts_that_key() {
        let store = make_shared_store();
        store.pin("example.com".into(), 22, "SHA256:old".into());
        let refused = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "example.com", 22, true);
        assert!(!run_callback(&refused, "SHA256:approved").await);
        let mismatch = AppSshHostKeyMismatch::from_rejection(&refused.rejection("SHA256:approved"));
        assert_eq!(
            mismatch.kind,
            AppSshHostKeyMismatchKind::Changed,
            "changed key must be trustable"
        );

        // "Trust New Key" pins exactly the fingerprint shown to the user.
        store.pin(mismatch.host, mismatch.port, mismatch.fingerprint);

        let retry = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "example.com", 22, true);
        assert!(run_callback(&retry, "SHA256:approved").await);
        retry.pin_on_first_use();
        assert_eq!(
            store.pinned("example.com".into(), 22),
            Some("SHA256:approved".into())
        );

        // A server presenting anything else on retry is refused as changed
        // again, and the approved pin is kept.
        let swapped = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "example.com", 22, true);
        assert!(!run_callback(&swapped, "SHA256:attacker").await);
        assert!(matches!(
            swapped.rejection("SHA256:attacker"),
            SshHostKeyRejection::Changed { .. }
        ));
        swapped.pin_on_first_use();
        assert_eq!(
            store.pinned("example.com".into(), 22),
            Some("SHA256:approved".into())
        );
    }

    #[tokio::test]
    async fn callback_accepts_only_the_session_key_on_recheck() {
        let store = make_shared_store();
        let policy = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "rekey.example", 22, true);
        let callback = policy.callback();
        assert!(callback("SHA256:first").await);
        // A rekey presenting a different key on the same connection is refused.
        assert!(!callback("SHA256:second").await);
        assert!(callback("SHA256:first").await);
        policy.pin_on_first_use();
        assert_eq!(
            store.pinned("rekey.example".into(), 22),
            Some("SHA256:first".into())
        );
    }

    // Throwaway ed25519 host keys, generated for these tests only.
    const HOST_KEY_A: &str = "-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACA0IUvyZZ8Z7VjQshLhY3pPHN8SSGc1M0/KAmHbCUNXGAAAAIjd9XAC3fVw
AgAAAAtzc2gtZWQyNTUxOQAAACA0IUvyZZ8Z7VjQshLhY3pPHN8SSGc1M0/KAmHbCUNXGA
AAAEDdFNw9pzaAxg+a43DBZ7srtZuWANmnC99b6KI2G8u9vDQhS/JlnxntWNCyEuFjek8c
3xJIZzUzT8oCYdsJQ1cYAAAAAAECAwQF
-----END OPENSSH PRIVATE KEY-----
";
    const HOST_KEY_B: &str = "-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACBC57xEgPN7CZegy5x243k6JglFbojE9/qNj6ewbjiWigAAAIgETq1/BE6t
fwAAAAtzc2gtZWQyNTUxOQAAACBC57xEgPN7CZegy5x243k6JglFbojE9/qNj6ewbjiWig
AAAEBRmpCpS9dKnv2nlyTA2hZhz6VLDh4f3B+Y7RVm1+mCXULnvESA83sJl6DLnHbjeTom
CUVuiMT3+o2Pp7BuOJaKAAAAAAECAwQF
-----END OPENSSH PRIVATE KEY-----
";

    struct AcceptAnyPassword;

    impl russh::server::Handler for AcceptAnyPassword {
        type Error = russh::Error;

        async fn auth_password(
            &mut self,
            _user: &str,
            _password: &str,
        ) -> Result<russh::server::Auth, Self::Error> {
            Ok(russh::server::Auth::Accept)
        }
    }

    /// In-process SSH server on 127.0.0.1 presenting `host_key_pem`.
    async fn spawn_ssh_server(host_key_pem: &str) -> u16 {
        let key = russh::keys::decode_secret_key(host_key_pem, None).expect("decode host key");
        let config = Arc::new(russh::server::Config {
            keys: vec![key],
            inactivity_timeout: None,
            auth_rejection_time: std::time::Duration::from_millis(10),
            ..Default::default()
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
            .await
            .expect("bind ssh test listener");
        let port = listener.local_addr().expect("listener addr").port();
        tokio::spawn(async move {
            while let Ok((socket, _)) = listener.accept().await {
                let config = Arc::clone(&config);
                tokio::spawn(async move {
                    if let Ok(session) =
                        russh::server::run_stream(config, socket, AcceptAnyPassword).await
                    {
                        let _ = session.await;
                    }
                });
            }
        });
        port
    }

    async fn connect_with(
        policy: &SshHostKeyPolicy,
        port: u16,
    ) -> Result<crate::ssh::SshClient, SshError> {
        let credentials = crate::ssh::SshCredentials {
            host: "127.0.0.1".into(),
            port,
            username: "tester".into(),
            auth: crate::ssh::SshAuth::Password("secret".into()),
            unlock_macos_keychain: false,
        };
        let client = crate::ssh::SshClient::connect(credentials, policy.callback())
            .await
            .map_err(|error| policy.surface_rejection(error))?;
        policy.pin_on_first_use();
        Ok(client)
    }

    #[tokio::test]
    async fn ssh_connect_pins_first_key_and_reports_changed_key() {
        let store = make_shared_store();
        let port_a = spawn_ssh_server(HOST_KEY_A).await;

        // First contact: accepted and pinned.
        let first = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "127.0.0.1", port_a, true);
        connect_with(&first, port_a)
            .await
            .expect("first connect")
            .disconnect()
            .await;
        let pinned_a = store
            .pinned("127.0.0.1".into(), port_a)
            .expect("first connect pins the host key");
        assert!(pinned_a.starts_with("SHA256:"), "unexpected pin {pinned_a}");

        // Pinned match connects even without accepting unknown hosts.
        let again = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "127.0.0.1", port_a, false);
        connect_with(&again, port_a)
            .await
            .expect("pinned connect")
            .disconnect()
            .await;

        // Same host:port now presents a different key.
        let port_b = spawn_ssh_server(HOST_KEY_B).await;
        store.pin("127.0.0.1".into(), port_b, pinned_a.clone());
        let changed = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "127.0.0.1", port_b, true);
        let fingerprint_b = match connect_with(&changed, port_b).await {
            Err(SshError::HostKeyRejected(SshHostKeyRejection::Changed {
                host,
                port,
                fingerprint,
            })) => {
                assert_eq!(host, "127.0.0.1");
                assert_eq!(port, port_b);
                assert!(fingerprint.starts_with("SHA256:"));
                assert_ne!(fingerprint, pinned_a);
                fingerprint
            }
            Err(other) => panic!("expected a changed host key, got {other:?}"),
            Ok(_) => panic!("changed host key must be rejected"),
        };
        assert_eq!(
            store.pinned("127.0.0.1".into(), port_b),
            Some(pinned_a.clone())
        );

        // Trusting the displayed fingerprint lets the retry through.
        store.pin("127.0.0.1".into(), port_b, fingerprint_b.clone());
        let trusted = SshHostKeyPolicy::new(Some(Arc::clone(&store)), "127.0.0.1", port_b, true);
        connect_with(&trusted, port_b)
            .await
            .expect("connect after trusting the displayed key")
            .disconnect()
            .await;
        assert_eq!(
            store.pinned("127.0.0.1".into(), port_b),
            Some(fingerprint_b)
        );
    }

    #[tokio::test]
    async fn ssh_connect_refuses_unreadable_trust_store() {
        let backend = UnreadableBackend::default();
        let writes = Arc::clone(&backend.writes);
        let store = Arc::new(TerminalSshTrustStore::new(Box::new(backend)));
        let port = spawn_ssh_server(HOST_KEY_A).await;
        let policy = SshHostKeyPolicy::new(Some(store), "127.0.0.1", port, true);
        match connect_with(&policy, port).await {
            Err(SshError::HostKeyRejected(SshHostKeyRejection::TrustStoreUnavailable {
                ..
            })) => {}
            Err(other) => panic!("expected trust store unavailable, got {other:?}"),
            Ok(_) => panic!("unreadable trust store must refuse the connect"),
        }
        assert!(writes.lock().unwrap().is_empty());
    }
}
