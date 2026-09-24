//! Device-side canonical strings and Ed25519 signatures for host push
//! subscriptions (host push design §5.1 grant, §5.3 revoke, §12 v2 vectors,
//! §13 input rules).
//!
//! The device signs with its iroh `SecretKey` (the same key the alleycat
//! endpoint is bound to); the device id is that key's public half as 64
//! lowercase hex characters. Signatures are RFC 8032 Ed25519, encoded as
//! 128 lowercase hex characters. Every signed string carries `aud`, the
//! origin of the Worker it is meant for, so it cannot be replayed against
//! another deployment.

use iroh::SecretKey;
use rand_core::{OsRng, RngCore};
use sha2::{Digest, Sha256};

use super::{AppApnsEnvironment, AppPushPlatform};

pub(crate) const GRANT_DOMAIN: &str = "agentbuddy-push-grant-v2";
pub(crate) const REVOKE_DOMAIN: &str = "agentbuddy-push-revoke-v2";
pub(crate) const REVOKE_SCOPE_ALL: &str = "all";
/// §13: `agent` matches `[A-Za-z0-9._-]{1,64}`.
pub(crate) const MAX_AGENT_LEN: usize = 64;
/// §13: thread / turn ids are at most 128 UTF-8 bytes.
pub(crate) const MAX_REF_ID_BYTES: usize = 128;

/// Fields of a device grant (§5.1), in canonical order.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct GrantFields<'a> {
    /// Worker origin, see [`worker_origin`].
    pub aud: &'a str,
    pub host_id: &'a str,
    pub device_id: &'a str,
    pub platform: AppPushPlatform,
    /// `None` renders as `none` (Android).
    pub environment: Option<AppApnsEnvironment>,
    /// The §5.5 sealed target string; the grant binds its SHA-256.
    pub sealed_target: &'a str,
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

/// Parse a Worker base URL. Only HTTPS is accepted (plain HTTP only for
/// loopback debugging), so nothing device-signed leaves the device in clear.
pub(crate) fn parse_worker_url(worker_base_url: &str) -> Result<url::Url, String> {
    let url = url::Url::parse(worker_base_url.trim())
        .map_err(|error| format!("invalid worker URL: {error}"))?;
    let loopback = matches!(
        url.host_str(),
        Some("localhost") | Some("127.0.0.1") | Some("[::1]")
    );
    if url.scheme() != "https" && !(url.scheme() == "http" && loopback) {
        return Err(format!(
            "refusing non-HTTPS worker URL scheme {}",
            url.scheme()
        ));
    }
    Ok(url)
}

/// `aud` for signed strings: the Worker origin `scheme://host[:port]`
/// without a trailing slash (what the Worker gets from
/// `new URL(request.url).origin`).
pub(crate) fn worker_origin(worker_base_url: &str) -> Result<String, String> {
    Ok(parse_worker_url(worker_base_url)?
        .origin()
        .ascii_serialization())
}

/// §13: `[A-Za-z0-9._-]{1,64}`.
pub(crate) fn is_valid_agent(agent: &str) -> bool {
    !agent.is_empty()
        && agent.len() <= MAX_AGENT_LEN
        && agent
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
}

/// §13: thread / turn ids are non-empty, at most 128 UTF-8 bytes and free of
/// control characters (Rust strings cannot hold unpaired surrogates).
pub(crate) fn is_valid_ref_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= MAX_REF_ID_BYTES && !id.chars().any(char::is_control)
}

/// Canonical grant string (§5.1): UTF-8, `\n`-separated, no trailing newline.
pub(crate) fn grant_canonical_string(fields: &GrantFields<'_>) -> String {
    let environment = fields.environment.map(environment_wire).unwrap_or("none");
    [
        GRANT_DOMAIN.to_string(),
        format!("aud={}", fields.aud),
        format!("host={}", fields.host_id),
        format!("device={}", fields.device_id),
        format!("platform={}", platform_wire(fields.platform)),
        format!("environment={environment}"),
        format!(
            "target_sha256={}",
            sha256_hex(fields.sealed_target.as_bytes())
        ),
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
    aud: &str,
    host_id: &str,
    device_id: &str,
    timestamp: u64,
    nonce: &str,
) -> String {
    [
        REVOKE_DOMAIN.to_string(),
        format!("aud={aud}"),
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

pub(crate) fn sign_revoke(
    key: &SecretKey,
    aud: &str,
    host_id: &str,
    timestamp: u64,
    nonce: &str,
) -> String {
    sign_hex(
        key,
        &revoke_canonical_string(aud, host_id, &device_id(key), timestamp, nonce),
    )
}

/// Fresh 128-bit CSPRNG nonce as 32 lowercase hex characters.
pub(crate) fn random_nonce_hex() -> String {
    let mut bytes = [0u8; 16];
    OsRng.fill_bytes(&mut bytes);
    hex::encode(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    // Host push design §12 v2 vectors, shared with the alleycat host and the
    // Worker test suites.
    const AUD: &str = "https://push.example.test";
    const HOST_ID: &str = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c";
    const DEVICE_ID: &str = "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";
    /// §12 sealed target (TEST Worker key, fixed ephemeral key and nonce;
    /// reproduced byte-for-byte in `seal::tests`).
    const SEALED_TARGET: &str = "AQGsAbIgnoY1T7hTI3td4PT6sTx_y_QzphwBk2lhf-zxCwUFBQUFBQUFBQUFBZ-odLYTYW6QLwzbORXVqxoBYANUa4EtrUQcvhO-SD6OJtfJpKJt7AB1O9_WrnlX9wxFfUOQylFkMm8ji3OikI9ZvU_wZHaKZN_Mw5iiKA9ThUZYktapcYlLgIb3bFGgQ4_UHttBPnCwqeBa45g0BTJQO33TxFOrTfbK8HkwaDUeG1NPZGxxgDDKI6YdUyR2YDwE4xdNqIM1WIXyg8b0l0ZoFXhpNpBil2Utqj7hSPCUsIFPHGvGJENgkeIM_hWXuwfK_KOP598o6DflDBF60r9C5N-VnaaY1YxF_VkW2-ySG5tjwNtJ2Pyd6UbAwr_wDDh3t9IXNpuWi4W_nUudMfy_znge2419AjQmS42j6jSjXIiiPP9cmbvJi3hx4wyO2TrEifIPigtxn7SG3g";
    const GRANT_STRING: &str = "agentbuddy-push-grant-v2\naud=https://push.example.test\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nplatform=ios\nenvironment=production\ntarget_sha256=3f40a3fbd4fe15e4a6a69c92c3ea5a264f483efa20e82f0fc4826a2734b58fa1\nagent=codex\nthread=thread-1\nturn=turn-1\nissued=1790300000\nexpires=1790386400\nnonce=ffeeddccbbaa99887766554433221100";
    const GRANT_SIGNATURE: &str = "40206a5b444e0c4beb95208ceb7c10f6d613ff68f0c9402ccc789c9a71017de9a1beb291f7c4ff488f3d72193fa4731113d6b84693a5c3d4636195277205bf05";
    const REVOKE_STRING: &str = "agentbuddy-push-revoke-v2\naud=https://push.example.test\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nscope=all\ntimestamp=1790300000\nnonce=0f0e0d0c0b0a09080706050403020100";
    const REVOKE_SIGNATURE: &str = "8ebac8fa199a1349c15d693b27a835f6a26d3aad210fe152f52470a78a1e2391d86d0fd2091059482df3d63236c24a934e5c8e0c3ffdc883ef0f91e6c7f34f0e";

    fn host_key() -> SecretKey {
        SecretKey::from_bytes(&[1u8; 32])
    }

    fn device_key() -> SecretKey {
        SecretKey::from_bytes(&[2u8; 32])
    }

    fn vector_grant() -> GrantFields<'static> {
        GrantFields {
            aud: AUD,
            host_id: HOST_ID,
            device_id: DEVICE_ID,
            platform: AppPushPlatform::Ios,
            environment: Some(AppApnsEnvironment::Production),
            sealed_target: SEALED_TARGET,
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
    fn vector_target_sha256() {
        assert_eq!(
            sha256_hex(SEALED_TARGET.as_bytes()),
            "3f40a3fbd4fe15e4a6a69c92c3ea5a264f483efa20e82f0fc4826a2734b58fa1"
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
            AUD,
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
                AUD,
                HOST_ID,
                1790300000,
                "0f0e0d0c0b0a09080706050403020100"
            ),
            REVOKE_SIGNATURE
        );
    }

    /// Cross-check the shared sha256 + Ed25519 primitives against the §12
    /// v2 host-request vector (the phone never signs host requests itself).
    #[test]
    fn vector_host_request_primitives_match() {
        let body = r#"{"eventId":"evt_0123456789abcdef0123456789abcdef","agent":"codex","threadId":"thread-1","turnId":"turn-1","type":"completed","reason":null,"occurredAt":1790300000}"#;
        let body_hash = sha256_hex(body.as_bytes());
        assert_eq!(
            body_hash,
            "b0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3"
        );
        let canonical = format!(
            "agentbuddy-push-host-v2\n{AUD}\nPOST\n/v2/events\n1790300000\n00112233445566778899aabbccddeeff\n{body_hash}"
        );
        assert_eq!(
            sign_hex(&host_key(), &canonical),
            "d3040154598d4426ae08efa11f69365fa37cf164458b05eb56e219c694f679bca6356a2dd8dd7f2151e3663c20dc7d354b71109fd8ab9d8dc8a693d8af1c2b05"
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
    fn worker_origin_is_scheme_host_port_without_trailing_slash() {
        assert_eq!(
            worker_origin("https://agentbuddy-push-proxy.aaksharker.workers.dev").unwrap(),
            "https://agentbuddy-push-proxy.aaksharker.workers.dev"
        );
        assert_eq!(
            worker_origin("https://Push.Example.Test/").unwrap(),
            "https://push.example.test"
        );
        assert_eq!(
            worker_origin("https://push.example.test:443/prefix/").unwrap(),
            "https://push.example.test"
        );
        assert_eq!(
            worker_origin("https://push.example.test:8443/x?y=1").unwrap(),
            "https://push.example.test:8443"
        );
        assert_eq!(
            worker_origin("http://127.0.0.1:8787").unwrap(),
            "http://127.0.0.1:8787"
        );
        assert!(worker_origin("http://push.example.test").is_err());
        assert!(worker_origin("not a url").is_err());
    }

    #[test]
    fn agent_names_follow_section_13() {
        let max = "a".repeat(MAX_AGENT_LEN);
        let too_long = "a".repeat(MAX_AGENT_LEN + 1);
        for agent in ["codex", "claude-code", "pi.dev", "A_b.9-z", max.as_str()] {
            assert!(is_valid_agent(agent), "{agent:?} should be valid");
        }
        for agent in [
            "",
            too_long.as_str(),
            "co dex",
            "codex\n",
            "codex/x",
            "cödex",
            "a|b",
        ] {
            assert!(!is_valid_agent(agent), "{agent:?} should be invalid");
        }
    }

    #[test]
    fn thread_and_turn_ids_follow_section_13() {
        let max = "t".repeat(MAX_REF_ID_BYTES);
        // 64 two-byte characters = 128 UTF-8 bytes.
        let max_multibyte = "é".repeat(64);
        for id in [
            "0199aa7e-0000-7000-8000-000000000000",
            "thread-1",
            "has space|pipe=eq",
            max.as_str(),
            max_multibyte.as_str(),
        ] {
            assert!(is_valid_ref_id(id), "{id:?} should be valid");
        }
        let too_long = "t".repeat(MAX_REF_ID_BYTES + 1);
        let too_long_multibyte = "é".repeat(65);
        for id in [
            "",
            too_long.as_str(),
            too_long_multibyte.as_str(),
            "turn\n1",
            "turn\r",
            "tab\there",
            "nul\0",
            "del\u{7f}",
            "c1\u{85}",
        ] {
            assert!(!is_valid_ref_id(id), "{id:?} should be invalid");
        }
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
