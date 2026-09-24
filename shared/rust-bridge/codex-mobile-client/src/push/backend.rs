//! Production [`PushBackend`]: alleycat push ops over the shared iroh
//! endpoint, the device key that endpoint is bound to, the system clock,
//! target sealing to the built-in Worker key, and the best-effort direct
//! Worker revoke over HTTPS.

use std::sync::{Arc, Mutex as StdMutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use async_trait::async_trait;
use iroh::SecretKey;

use super::manager::{DirectRevokeRequest, PushBackend};
use super::seal::{self, SealError, SealTarget};
use super::signing;
use crate::alleycat::{
    AlleycatPushError, ParsedPairPayload, PushSubscribeArgs, PushSubscribeOutcome,
    PushUnsubscribeScope,
};

const DIRECT_REVOKE_TIMEOUT: Duration = Duration::from_secs(10);
const DIRECT_REVOKE_PATH: &str = "/v2/subscriptions/revoke";

pub(crate) struct IrohPushBackend {
    /// The app-wide alleycat endpoint cell owned by `MobileClient`. Push
    /// work only happens for connected alleycat hosts, so the endpoint is
    /// already bound by then; it is never bound from here.
    endpoint: Arc<tokio::sync::OnceCell<iroh::Endpoint>>,
    /// The persisted device key the platform handed over at launch
    /// (`set_alleycat_secret_key`). Used when the endpoint is not bound
    /// yet, e.g. unpairing a host right after a cold start.
    persisted_secret_key: Arc<StdMutex<Option<[u8; 32]>>>,
}

impl IrohPushBackend {
    pub(crate) fn new(
        endpoint: Arc<tokio::sync::OnceCell<iroh::Endpoint>>,
        persisted_secret_key: Arc<StdMutex<Option<[u8; 32]>>>,
    ) -> Self {
        Self {
            endpoint,
            persisted_secret_key,
        }
    }

    fn endpoint(&self) -> Result<iroh::Endpoint, AlleycatPushError> {
        self.endpoint
            .get()
            .cloned()
            .ok_or_else(|| AlleycatPushError::Transport("alleycat endpoint not bound".into()))
    }
}

/// Only HTTPS Worker URLs are used (plain HTTP is allowed for loopback
/// debugging), so a device-signed revoke never leaves the device in clear.
fn direct_revoke_url(worker_base_url: &str) -> Result<url::Url, String> {
    let base = signing::parse_worker_url(worker_base_url)?;
    let joined = format!(
        "{}{}",
        base.as_str().trim_end_matches('/'),
        DIRECT_REVOKE_PATH
    );
    url::Url::parse(&joined).map_err(|error| format!("invalid worker URL: {error}"))
}

#[async_trait]
impl PushBackend for IrohPushBackend {
    fn device_secret_key(&self) -> Option<SecretKey> {
        if let Some(endpoint) = self.endpoint.get() {
            return Some(endpoint.secret_key().clone());
        }
        let persisted = match self.persisted_secret_key.lock() {
            Ok(guard) => *guard,
            Err(error) => *error.into_inner(),
        };
        persisted.map(|bytes| SecretKey::from_bytes(&bytes))
    }

    fn now_unix_secs(&self) -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|duration| duration.as_secs())
            .unwrap_or(0)
    }

    fn new_nonce(&self) -> String {
        signing::random_nonce_hex()
    }

    fn seal_target(&self, target: &SealTarget<'_>) -> Result<String, SealError> {
        seal::seal_target(&seal::PRODUCTION_SEAL_KEY, target)
    }

    async fn subscribe(
        &self,
        host: &ParsedPairPayload,
        args: PushSubscribeArgs,
    ) -> Result<PushSubscribeOutcome, AlleycatPushError> {
        let endpoint = self.endpoint()?;
        crate::alleycat::push_subscribe(&endpoint, host, args).await
    }

    async fn unsubscribe(
        &self,
        host: &ParsedPairPayload,
        scope: PushUnsubscribeScope,
    ) -> Result<(), AlleycatPushError> {
        let endpoint = self.endpoint()?;
        crate::alleycat::push_unsubscribe(&endpoint, host, scope).await
    }

    async fn revoke_direct(
        &self,
        worker_base_url: &str,
        request: DirectRevokeRequest,
    ) -> Result<(), String> {
        let url = direct_revoke_url(worker_base_url)?;
        // §13: never follow redirects; a redirect could move the signed
        // revoke off the HTTPS Worker origin it was signed for.
        let client = reqwest::Client::builder()
            .timeout(DIRECT_REVOKE_TIMEOUT)
            .redirect(reqwest::redirect::Policy::none())
            .build()
            .map_err(|error| format!("building HTTP client: {error}"))?;
        let response = client
            .post(url)
            .json(&request)
            .send()
            .await
            .map_err(|error| format!("sending revoke: {error}"))?;
        let status = response.status();
        if status.is_success() {
            Ok(())
        } else {
            Err(format!("worker answered HTTP {}", status.as_u16()))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn direct_revoke_url_joins_path() {
        assert_eq!(
            direct_revoke_url("https://push.example.workers.dev")
                .unwrap()
                .as_str(),
            "https://push.example.workers.dev/v2/subscriptions/revoke"
        );
        assert_eq!(
            direct_revoke_url("https://push.example.workers.dev/")
                .unwrap()
                .as_str(),
            "https://push.example.workers.dev/v2/subscriptions/revoke"
        );
        assert_eq!(
            direct_revoke_url("http://127.0.0.1:8787").unwrap().as_str(),
            "http://127.0.0.1:8787/v2/subscriptions/revoke"
        );
    }

    #[test]
    fn direct_revoke_url_rejects_plain_http_and_garbage() {
        assert!(direct_revoke_url("http://push.example.com").is_err());
        assert!(direct_revoke_url("ftp://push.example.com").is_err());
        assert!(direct_revoke_url("not a url").is_err());
    }

    fn unbound_backend() -> IrohPushBackend {
        IrohPushBackend::new(
            Arc::new(tokio::sync::OnceCell::new()),
            Arc::new(StdMutex::new(None)),
        )
    }

    #[test]
    fn unbound_endpoint_has_no_device_key_and_fails_ops() {
        let backend = unbound_backend();
        assert!(backend.device_secret_key().is_none());
        assert!(matches!(
            backend.endpoint(),
            Err(AlleycatPushError::Transport(_))
        ));
    }

    #[test]
    fn unbound_endpoint_falls_back_to_persisted_device_key() {
        let backend = unbound_backend();
        *backend.persisted_secret_key.lock().unwrap() = Some([2u8; 32]);
        let key = backend.device_secret_key().expect("persisted key");
        assert_eq!(
            signing::device_id(&key),
            signing::device_id(&SecretKey::from_bytes(&[2u8; 32]))
        );
    }

    fn revoke_request() -> DirectRevokeRequest {
        DirectRevokeRequest {
            host_id: "h".into(),
            device_id: "d".into(),
            scope: "all".into(),
            timestamp: 1,
            nonce: "n".into(),
            signature: "s".into(),
        }
    }

    /// Minimal HTTP/1.1 server: records each request line and answers every
    /// request with `status_line` plus `extra_headers`, closing afterwards.
    async fn spawn_recording_server(
        status_line: &'static str,
        extra_headers: impl Fn(u16) -> String + Send + 'static,
    ) -> (u16, Arc<StdMutex<Vec<String>>>) {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let seen = Arc::new(StdMutex::new(Vec::new()));
        let recorded = Arc::clone(&seen);
        tokio::spawn(async move {
            while let Ok((mut socket, _)) = listener.accept().await {
                let mut buf = Vec::new();
                let mut chunk = [0u8; 1024];
                let header_end = loop {
                    let Ok(n) = socket.read(&mut chunk).await else {
                        break None;
                    };
                    if n == 0 {
                        break None;
                    }
                    buf.extend_from_slice(&chunk[..n]);
                    if let Some(pos) = buf.windows(4).position(|w| w == b"\r\n\r\n") {
                        break Some(pos + 4);
                    }
                };
                let Some(header_end) = header_end else {
                    continue;
                };
                let head = String::from_utf8_lossy(&buf[..header_end]).to_string();
                let content_length = head
                    .lines()
                    .find_map(|line| {
                        let (name, value) = line.split_once(':')?;
                        name.eq_ignore_ascii_case("content-length")
                            .then(|| value.trim().parse::<usize>().ok())?
                    })
                    .unwrap_or(0);
                while buf.len() < header_end + content_length {
                    match socket.read(&mut chunk).await {
                        Ok(0) | Err(_) => break,
                        Ok(n) => buf.extend_from_slice(&chunk[..n]),
                    }
                }
                recorded
                    .lock()
                    .unwrap()
                    .push(head.lines().next().unwrap_or_default().to_string());
                let response = format!(
                    "{status_line}\r\n{}Content-Length: 0\r\nConnection: close\r\n\r\n",
                    extra_headers(port)
                );
                let _ = socket.write_all(response.as_bytes()).await;
                let _ = socket.shutdown().await;
            }
        });
        (port, seen)
    }

    #[tokio::test]
    async fn direct_revoke_does_not_follow_redirects() {
        let (port, seen) = spawn_recording_server("HTTP/1.1 307 Temporary Redirect", |port| {
            format!("Location: http://127.0.0.1:{port}/followed\r\n")
        })
        .await;
        let result = unbound_backend()
            .revoke_direct(&format!("http://127.0.0.1:{port}"), revoke_request())
            .await;
        let error = result.expect_err("a redirect is not success");
        assert!(error.contains("307"), "{error}");
        tokio::time::sleep(Duration::from_millis(50)).await;
        assert_eq!(
            *seen.lock().unwrap(),
            vec!["POST /v2/subscriptions/revoke HTTP/1.1".to_string()],
            "the redirect target must never be requested"
        );
    }

    #[tokio::test]
    async fn direct_revoke_succeeds_on_2xx() {
        let (port, seen) = spawn_recording_server("HTTP/1.1 200 OK", |_| String::new()).await;
        unbound_backend()
            .revoke_direct(&format!("http://127.0.0.1:{port}/"), revoke_request())
            .await
            .expect("2xx is success");
        assert_eq!(seen.lock().unwrap().len(), 1);
    }
}
