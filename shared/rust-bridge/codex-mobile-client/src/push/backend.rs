//! Production [`PushBackend`]: alleycat push ops over the shared iroh
//! endpoint, the device key that endpoint is bound to, the system clock,
//! and the best-effort direct Worker revoke over HTTPS.

use std::sync::Arc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use async_trait::async_trait;
use iroh::SecretKey;

use super::manager::{DirectRevokeRequest, PushBackend};
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
}

impl IrohPushBackend {
    pub(crate) fn new(endpoint: Arc<tokio::sync::OnceCell<iroh::Endpoint>>) -> Self {
        Self { endpoint }
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
    let base = url::Url::parse(worker_base_url.trim())
        .map_err(|error| format!("invalid worker URL: {error}"))?;
    let loopback = matches!(
        base.host_str(),
        Some("localhost") | Some("127.0.0.1") | Some("[::1]")
    );
    if base.scheme() != "https" && !(base.scheme() == "http" && loopback) {
        return Err(format!(
            "refusing non-HTTPS worker URL scheme {}",
            base.scheme()
        ));
    }
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
        self.endpoint
            .get()
            .map(|endpoint| endpoint.secret_key().clone())
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
        let client = reqwest::Client::builder()
            .timeout(DIRECT_REVOKE_TIMEOUT)
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

    #[test]
    fn unbound_endpoint_has_no_device_key_and_fails_ops() {
        let backend = IrohPushBackend::new(Arc::new(tokio::sync::OnceCell::new()));
        assert!(backend.device_secret_key().is_none());
        assert!(matches!(
            backend.endpoint(),
            Err(AlleycatPushError::Transport(_))
        ));
    }
}
