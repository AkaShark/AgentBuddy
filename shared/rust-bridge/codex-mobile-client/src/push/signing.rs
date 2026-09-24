//! Device-side canonical strings and Ed25519 signatures for host push
//! subscriptions (host push design §5.1 grant, §5.3 revoke, §12 vectors).
//!
//! The device signs with its iroh `SecretKey` (the same key the alleycat
//! endpoint is bound to); the device id is that key's public half as 64
//! lowercase hex characters. Signatures are RFC 8032 Ed25519, encoded as
//! 128 lowercase hex characters.

use iroh::SecretKey;
use sha2::{Digest, Sha256};

use super::{AppApnsEnvironment, AppPushPlatform};

pub(crate) const GRANT_DOMAIN: &str = "agentbuddy-push-grant-v1";
pub(crate) const REVOKE_DOMAIN: &str = "agentbuddy-push-revoke-v1";
pub(crate) const REVOKE_SCOPE_ALL: &str = "all";

/// Fields of a device grant (§5.1), in canonical order.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct GrantFields<'a> {
    pub host_id: &'a str,
    pub device_id: &'a str,
    pub platform: AppPushPlatform,
    /// `None` renders as `none` (Android).
    pub environment: Option<AppApnsEnvironment>,
    pub push_token: &'a str,
    pub agent: &'a str,
    pub thread_id: &'a str,
    pub turn_id: &'a str,
    pub issued: u64,
    pub expires: u64,
    pub nonce: &'a str,
}

pub(crate) fn platform_wire(platform: AppPushPlatform) -> &'static str {
    match platform {
        AppPushPlatform::Ios => "ios",
        AppPushPlatform::Android => "android",
    }
}

pub(crate) fn environment_wire(environment: AppApnsEnvironment) -> &'static str {
    match environment {
        AppApnsEnvironment::Sandbox => "sandbox",
        AppApnsEnvironment::Production => "production",
    }
}

pub(crate) fn sha256_hex(bytes: &[u8]) -> String {
    hex::encode(Sha256::digest(bytes))
}

/// The device id (iroh node id) for `key`: 64 lowercase hex characters.
pub(crate) fn device_id(key: &SecretKey) -> String {
    hex::encode(key.public().as_bytes())
}

/// Ed25519-sign `message` and return the signature as 128 lowercase hex.
pub(crate) fn sign_hex(key: &SecretKey, message: &str) -> String {
    hex::encode(key.sign(message.as_bytes()).to_bytes())
}

/// Canonical grant string (§5.1): UTF-8, `\n`-separated, no trailing newline.
pub(crate) fn grant_canonical_string(fields: &GrantFields<'_>) -> String {
    let environment = fields.environment.map(environment_wire).unwrap_or("none");
    [
        GRANT_DOMAIN.to_string(),
        format!("host={}", fields.host_id),
        format!("device={}", fields.device_id),
        format!("platform={}", platform_wire(fields.platform)),
        format!("environment={environment}"),
        format!("token_sha256={}", sha256_hex(fields.push_token.as_bytes())),
        format!("agent={}", fields.agent),
        format!("thread={}", fields.thread_id),
        format!("turn={}", fields.turn_id),
        format!("issued={}", fields.issued),
        format!("expires={}", fields.expires),
        format!("nonce={}", fields.nonce),
    ]
    .join("\n")
}

/// Canonical `scope=all` revoke string (§5.3).
pub(crate) fn revoke_canonical_string(
    host_id: &str,
    device_id: &str,
    timestamp: u64,
    nonce: &str,
) -> String {
    [
        REVOKE_DOMAIN.to_string(),
        format!("host={host_id}"),
        format!("device={device_id}"),
        format!("scope={REVOKE_SCOPE_ALL}"),
        format!("timestamp={timestamp}"),
        format!("nonce={nonce}"),
    ]
    .join("\n")
}

pub(crate) fn sign_grant(key: &SecretKey, fields: &GrantFields<'_>) -> String {
    sign_hex(key, &grant_canonical_string(fields))
}

pub(crate) fn sign_revoke(key: &SecretKey, host_id: &str, timestamp: u64, nonce: &str) -> String {
    sign_hex(
        key,
        &revoke_canonical_string(host_id, &device_id(key), timestamp, nonce),
    )
}

/// Fresh 128-bit nonce as 32 lowercase hex characters.
pub(crate) fn random_nonce_hex() -> String {
    uuid::Uuid::new_v4().simple().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    // Host push design §12 vectors, shared with the alleycat host and the
    // Worker test suites.
    const HOST_ID: &str = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c";
    const DEVICE_ID: &str = "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";
    const PUSH_TOKEN: &str = "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1";
    const GRANT_STRING: &str = "agentbuddy-push-grant-v1\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nplatform=ios\nenvironment=production\ntoken_sha256=3490f80401886d12f3861ec190f5c16419b26345497a92bc4530e22f5d8b4295\nagent=codex\nthread=thread-1\nturn=turn-1\nissued=1790300000\nexpires=1790386400\nnonce=ffeeddccbbaa99887766554433221100";
    const GRANT_SIGNATURE: &str = "c0bb8f6643304b84ad574ee0797e698f53a6b3eb9abcb4a08d98f5bc691530f705e36f138f28dcf2f8df271c59372d475d8d17171ec03dda91b0e62cd721200e";
    const REVOKE_STRING: &str = "agentbuddy-push-revoke-v1\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nscope=all\ntimestamp=1790300000\nnonce=0f0e0d0c0b0a09080706050403020100";
    const REVOKE_SIGNATURE: &str = "8a989a6ca6e9d3cd37bdbfd3ff3b4aa94482e27c0613a03d51a27e51a13f072e1ae39c460e1651601846b43d4a4c6d1cc6923fddaac36ad776892e598b12bf03";

    fn host_key() -> SecretKey {
        SecretKey::from_bytes(&[1u8; 32])
    }

    fn device_key() -> SecretKey {
        SecretKey::from_bytes(&[2u8; 32])
    }

    fn vector_grant() -> GrantFields<'static> {
        GrantFields {
            host_id: HOST_ID,
            device_id: DEVICE_ID,
            platform: AppPushPlatform::Ios,
            environment: Some(AppApnsEnvironment::Production),
            push_token: PUSH_TOKEN,
            agent: "codex",
            thread_id: "thread-1",
            turn_id: "turn-1",
            issued: 1790300000,
            expires: 1790386400,
            nonce: "ffeeddccbbaa99887766554433221100",
        }
    }

    #[test]
    fn vector_seeds_derive_spec_ids() {
        assert_eq!(device_id(&host_key()), HOST_ID);
        assert_eq!(device_id(&device_key()), DEVICE_ID);
        // The iroh `Display` form is the same lowercase hex node id.
        assert_eq!(device_key().public().to_string(), DEVICE_ID);
    }

    #[test]
    fn vector_push_token_digest() {
        assert_eq!(
            sha256_hex(PUSH_TOKEN.as_bytes()),
            "3490f80401886d12f3861ec190f5c16419b26345497a92bc4530e22f5d8b4295"
        );
    }

    #[test]
    fn vector_grant_canonical_string_is_byte_identical() {
        let canonical = grant_canonical_string(&vector_grant());
        assert_eq!(canonical, GRANT_STRING);
        assert!(!canonical.ends_with('\n'));
    }

    #[test]
    fn vector_grant_signature_matches() {
        assert_eq!(sign_grant(&device_key(), &vector_grant()), GRANT_SIGNATURE);
        assert_eq!(sign_hex(&device_key(), GRANT_STRING), GRANT_SIGNATURE);
    }

    #[test]
    fn vector_grant_signature_verifies_with_device_id() {
        let signature_bytes: [u8; 64] = hex::decode(GRANT_SIGNATURE)
            .expect("hex")
            .try_into()
            .expect("64 bytes");
        let signature = iroh::Signature::from_bytes(&signature_bytes);
        let public = iroh::PublicKey::from_bytes(
            &hex::decode(DEVICE_ID)
                .expect("hex")
                .try_into()
                .expect("32 bytes"),
        )
        .expect("public key");
        public
            .verify(GRANT_STRING.as_bytes(), &signature)
            .expect("signature verifies");
    }

    #[test]
    fn vector_revoke_canonical_string_is_byte_identical() {
        let canonical = revoke_canonical_string(
            HOST_ID,
            DEVICE_ID,
            1790300000,
            "0f0e0d0c0b0a09080706050403020100",
        );
        assert_eq!(canonical, REVOKE_STRING);
        assert!(!canonical.ends_with('\n'));
    }

    #[test]
    fn vector_revoke_signature_matches() {
        assert_eq!(
            sign_revoke(
                &device_key(),
                HOST_ID,
                1790300000,
                "0f0e0d0c0b0a09080706050403020100"
            ),
            REVOKE_SIGNATURE
        );
    }

    /// Cross-check the shared sha256 + Ed25519 primitives against the §12
    /// host-request vector (the phone never signs host requests itself).
    #[test]
    fn vector_host_request_primitives_match() {
        let body = r#"{"eventId":"evt_0123456789abcdef0123456789abcdef","agent":"codex","threadId":"thread-1","turnId":"turn-1","type":"completed","reason":null,"occurredAt":1790300000}"#;
        let body_hash = sha256_hex(body.as_bytes());
        assert_eq!(
            body_hash,
            "b0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3"
        );
        let canonical = format!(
            "agentbuddy-push-host-v1\nPOST\n/v2/events\n1790300000\n00112233445566778899aabbccddeeff\n{body_hash}"
        );
        assert_eq!(
            sign_hex(&host_key(), &canonical),
            "d5ce9808b8bc3c1a19fefa8da077fd42b53180b2e9eb1e7d4672beb0cefeb169af22c08cc88a46ac9d57df17ca8b21a359390598ea96bd9d1f6fd1aad9077f03"
        );
    }

    #[test]
    fn android_grant_uses_none_environment() {
        let fields = GrantFields {
            platform: AppPushPlatform::Android,
            environment: None,
            ..vector_grant()
        };
        let canonical = grant_canonical_string(&fields);
        assert!(canonical.contains("\nplatform=android\nenvironment=none\n"));
    }

    #[test]
    fn random_nonce_is_32_lowercase_hex() {
        let nonce = random_nonce_hex();
        assert_eq!(nonce.len(), 32);
        assert!(
            nonce
                .chars()
                .all(|c| c.is_ascii_digit() || ('a'..='f').contains(&c))
        );
        assert_ne!(nonce, random_nonce_hex());
    }
}
