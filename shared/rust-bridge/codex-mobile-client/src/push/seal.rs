//! Sealed push target (host push design §5.5): the push token is encrypted
//! to the Worker's static X25519 key so the host only ever stores and
//! forwards an opaque string it can neither read nor swap.
//!
//! `sealedTarget = base64url-nopad(0x01 ‖ kid ‖ epk(32) ‖ nonce(12) ‖ ct+tag)`
//! with `shared = X25519(esk, workerPk)`,
//! `key = HKDF-SHA256(shared, salt = epk ‖ workerPk, info = "agentbuddy-push-target-v1")`,
//! AES-256-GCM over the target JSON with AAD `"<hostId>|<deviceId>"`.

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Nonce};
use base64::Engine as _;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use hkdf::Hkdf;
use rand_core::{OsRng, RngCore};
use serde::Serialize;
use sha2::Sha256;
use x25519_dalek::{PublicKey, StaticSecret};

use super::signing::{environment_wire, platform_wire};
use super::{AppApnsEnvironment, AppPushPlatform};

pub(crate) const SEALED_TARGET_VERSION: u8 = 0x01;
pub(crate) const SEAL_HKDF_INFO: &[u8] = b"agentbuddy-push-target-v1";

/// A Worker sealing public key and its 1-byte key id.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct WorkerSealKey<'a> {
    pub kid: u8,
    /// X25519 public key, 64 lowercase hex characters.
    pub public_hex: &'a str,
}

/// Built-in production Worker sealing key. The matching X25519 private key
/// exists only in the Worker secret `PUSH_TARGET_SEAL_KEY` (`<kid>:<hex>`);
/// the host cannot replace this key because it ships inside the app.
///
/// Rotation: add the new private key to `PUSH_TARGET_SEAL_KEY` next to the
/// old one (comma-separated), ship an app release with the new `kid` and
/// public key here, and drop the old private key from the Worker only after
/// app versions carrying the old key have aged out.
pub(crate) const PRODUCTION_SEAL_KEY: WorkerSealKey<'static> = WorkerSealKey {
    kid: 1,
    public_hex: "eddcd4bb4ee04376d77bb5b1c193adfc46fe4f0163ee31b25abe79aef7642c53",
};

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub(crate) enum SealError {
    #[error("invalid worker sealing key")]
    InvalidWorkerKey,
    #[error("non-contributory X25519 agreement")]
    WeakSharedSecret,
    #[error("encrypting push target failed")]
    Encrypt,
}

/// §5.5 plaintext, serialized with exactly this key order.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct TargetPlaintext<'a> {
    v: u8,
    platform: &'a str,
    token: &'a str,
    /// `null` on Android.
    apns_environment: Option<&'a str>,
    device_id: &'a str,
    host_id: &'a str,
}

/// What gets sealed: the push destination bound to one host/device pair.
#[derive(Debug, Clone, Copy)]
pub(crate) struct SealTarget<'a> {
    pub platform: AppPushPlatform,
    pub token: &'a str,
    pub apns_environment: Option<AppApnsEnvironment>,
    pub device_id: &'a str,
    pub host_id: &'a str,
}

impl SealTarget<'_> {
    pub(crate) fn plaintext_json(&self) -> String {
        let plaintext = TargetPlaintext {
            v: 1,
            platform: platform_wire(self.platform),
            token: self.token,
            apns_environment: self.apns_environment.map(environment_wire),
            device_id: self.device_id,
            host_id: self.host_id,
        };
        serde_json::to_string(&plaintext).expect("target plaintext serializes")
    }

    pub(crate) fn aad(&self) -> String {
        format!("{}|{}", self.host_id, self.device_id)
    }
}

fn worker_public_key(key: &WorkerSealKey<'_>) -> Result<[u8; 32], SealError> {
    hex::decode(key.public_hex)
        .ok()
        .and_then(|bytes| <[u8; 32]>::try_from(bytes).ok())
        .ok_or(SealError::InvalidWorkerKey)
}

/// `HKDF-SHA256(ikm = shared, salt = epk ‖ workerPk, info, L = 32)`.
fn derive_key(shared: &[u8; 32], epk: &[u8; 32], worker_pk: &[u8; 32]) -> [u8; 32] {
    let mut salt = [0u8; 64];
    salt[..32].copy_from_slice(epk);
    salt[32..].copy_from_slice(worker_pk);
    let mut key = [0u8; 32];
    Hkdf::<Sha256>::new(Some(&salt), shared)
        .expand(SEAL_HKDF_INFO, &mut key)
        .expect("32 bytes is a valid HKDF-SHA256 output length");
    key
}

/// Seal `target` to `worker_key` with a fresh CSPRNG ephemeral key and nonce.
pub(crate) fn seal_target(
    worker_key: &WorkerSealKey<'_>,
    target: &SealTarget<'_>,
) -> Result<String, SealError> {
    let mut ephemeral_secret = [0u8; 32];
    let mut nonce = [0u8; 12];
    OsRng.fill_bytes(&mut ephemeral_secret);
    OsRng.fill_bytes(&mut nonce);
    seal_target_with(worker_key, target, ephemeral_secret, nonce)
}

/// [`seal_target`] with an injected ephemeral secret and GCM nonce (the §12
/// vector pins both).
pub(crate) fn seal_target_with(
    worker_key: &WorkerSealKey<'_>,
    target: &SealTarget<'_>,
    ephemeral_secret: [u8; 32],
    nonce: [u8; 12],
) -> Result<String, SealError> {
    let worker_pk = worker_public_key(worker_key)?;
    let esk = StaticSecret::from(ephemeral_secret);
    let epk = PublicKey::from(&esk);
    let shared = esk.diffie_hellman(&PublicKey::from(worker_pk));
    if !shared.was_contributory() {
        return Err(SealError::WeakSharedSecret);
    }
    let key = derive_key(shared.as_bytes(), epk.as_bytes(), &worker_pk);
    let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| SealError::Encrypt)?;
    let plaintext = target.plaintext_json();
    let aad = target.aad();
    let ciphertext = cipher
        .encrypt(
            Nonce::from_slice(&nonce),
            Payload {
                msg: plaintext.as_bytes(),
                aad: aad.as_bytes(),
            },
        )
        .map_err(|_| SealError::Encrypt)?;
    let mut blob = Vec::with_capacity(2 + 32 + 12 + ciphertext.len());
    blob.push(SEALED_TARGET_VERSION);
    blob.push(worker_key.kid);
    blob.extend_from_slice(epk.as_bytes());
    blob.extend_from_slice(&nonce);
    blob.extend_from_slice(&ciphertext);
    Ok(URL_SAFE_NO_PAD.encode(blob))
}

/// Worker-side open, for tests: parse the blob, re-derive the key from the
/// Worker's static secret and decrypt with `aad`. Returns `(kid, plaintext)`.
#[cfg(test)]
pub(crate) fn open_target(
    worker_secret: [u8; 32],
    sealed: &str,
    aad: &str,
) -> Option<(u8, String)> {
    let blob = URL_SAFE_NO_PAD.decode(sealed).ok()?;
    if blob.len() < 2 + 32 + 12 + 16 || blob[0] != SEALED_TARGET_VERSION {
        return None;
    }
    let kid = blob[1];
    let epk: [u8; 32] = blob[2..34].try_into().ok()?;
    let secret = StaticSecret::from(worker_secret);
    let worker_pk = PublicKey::from(&secret);
    let shared = secret.diffie_hellman(&PublicKey::from(epk));
    let key = derive_key(shared.as_bytes(), &epk, worker_pk.as_bytes());
    let plaintext = Aes256Gcm::new_from_slice(&key)
        .ok()?
        .decrypt(
            Nonce::from_slice(&blob[34..46]),
            Payload {
                msg: &blob[46..],
                aad: aad.as_bytes(),
            },
        )
        .ok()?;
    Some((kid, String::from_utf8(plaintext).ok()?))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::push::signing::sha256_hex;

    // Host push design §12 sealed-target vector (TEST Worker key only).
    const TEST_WORKER_SECRET: [u8; 32] = [3u8; 32];
    const TEST_WORKER_KEY: WorkerSealKey<'static> = WorkerSealKey {
        kid: 1,
        public_hex: "5dfedd3b6bd47f6fa28ee15d969d5bb0ea53774d488bdaf9df1c6e0124b3ef22",
    };
    const EPHEMERAL_SECRET: [u8; 32] = [4u8; 32];
    const EPHEMERAL_PUBLIC: &str =
        "ac01b2209e86354fb853237b5de0f4fab13c7fcbf433a61c019369617fecf10b";
    const GCM_NONCE: [u8; 12] = [5u8; 12];
    const SHARED: &str = "40e47a3f525bdcac491d418978d7db5af623ac7afe7623c6d78a5d4fce9d0f63";
    const HKDF_KEY: &str = "defc170b9433ff2d066c943ce0cc4d508d0f384468635ee35355a6847269928d";
    const HOST_ID: &str = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c";
    const DEVICE_ID: &str = "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";
    const PUSH_TOKEN: &str = "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1";
    const PLAINTEXT: &str = r#"{"v":1,"platform":"ios","token":"a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1","apnsEnvironment":"production","deviceId":"8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394","hostId":"8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c"}"#;
    const AAD: &str = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c|8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";
    const SEALED_TARGET: &str = "AQGsAbIgnoY1T7hTI3td4PT6sTx_y_QzphwBk2lhf-zxCwUFBQUFBQUFBQUFBZ-odLYTYW6QLwzbORXVqxoBYANUa4EtrUQcvhO-SD6OJtfJpKJt7AB1O9_WrnlX9wxFfUOQylFkMm8ji3OikI9ZvU_wZHaKZN_Mw5iiKA9ThUZYktapcYlLgIb3bFGgQ4_UHttBPnCwqeBa45g0BTJQO33TxFOrTfbK8HkwaDUeG1NPZGxxgDDKI6YdUyR2YDwE4xdNqIM1WIXyg8b0l0ZoFXhpNpBil2Utqj7hSPCUsIFPHGvGJENgkeIM_hWXuwfK_KOP598o6DflDBF60r9C5N-VnaaY1YxF_VkW2-ySG5tjwNtJ2Pyd6UbAwr_wDDh3t9IXNpuWi4W_nUudMfy_znge2419AjQmS42j6jSjXIiiPP9cmbvJi3hx4wyO2TrEifIPigtxn7SG3g";
    const SEALED_TARGET_SHA256: &str =
        "3f40a3fbd4fe15e4a6a69c92c3ea5a264f483efa20e82f0fc4826a2734b58fa1";

    fn vector_target() -> SealTarget<'static> {
        SealTarget {
            platform: AppPushPlatform::Ios,
            token: PUSH_TOKEN,
            apns_environment: Some(AppApnsEnvironment::Production),
            device_id: DEVICE_ID,
            host_id: HOST_ID,
        }
    }

    #[test]
    fn production_key_decodes_to_32_bytes() {
        assert_eq!(PRODUCTION_SEAL_KEY.kid, 1);
        assert!(worker_public_key(&PRODUCTION_SEAL_KEY).is_ok());
    }

    #[test]
    fn vector_keys_and_intermediates_match() {
        let worker_secret = StaticSecret::from(TEST_WORKER_SECRET);
        assert_eq!(
            hex::encode(PublicKey::from(&worker_secret).as_bytes()),
            TEST_WORKER_KEY.public_hex
        );
        let esk = StaticSecret::from(EPHEMERAL_SECRET);
        let epk = PublicKey::from(&esk);
        assert_eq!(hex::encode(epk.as_bytes()), EPHEMERAL_PUBLIC);
        let worker_pk = worker_public_key(&TEST_WORKER_KEY).unwrap();
        let shared = esk.diffie_hellman(&PublicKey::from(worker_pk));
        assert_eq!(hex::encode(shared.as_bytes()), SHARED);
        assert_eq!(
            hex::encode(derive_key(shared.as_bytes(), epk.as_bytes(), &worker_pk)),
            HKDF_KEY
        );
    }

    #[test]
    fn vector_plaintext_and_aad_are_byte_identical() {
        assert_eq!(vector_target().plaintext_json(), PLAINTEXT);
        assert_eq!(vector_target().aad(), AAD);
    }

    #[test]
    fn vector_sealed_target_is_byte_identical() {
        let sealed = seal_target_with(
            &TEST_WORKER_KEY,
            &vector_target(),
            EPHEMERAL_SECRET,
            GCM_NONCE,
        )
        .unwrap();
        assert_eq!(sealed, SEALED_TARGET);
        assert_eq!(sha256_hex(sealed.as_bytes()), SEALED_TARGET_SHA256);
    }

    #[test]
    fn android_plaintext_has_null_apns_environment() {
        let target = SealTarget {
            platform: AppPushPlatform::Android,
            token: "fcm:token-1",
            apns_environment: None,
            ..vector_target()
        };
        assert_eq!(
            target.plaintext_json(),
            format!(
                r#"{{"v":1,"platform":"android","token":"fcm:token-1","apnsEnvironment":null,"deviceId":"{DEVICE_ID}","hostId":"{HOST_ID}"}}"#
            )
        );
    }

    #[test]
    fn random_seal_roundtrips_with_test_keypair_and_binds_aad() {
        let worker_secret = [7u8; 32];
        let worker_public_hex =
            hex::encode(PublicKey::from(&StaticSecret::from(worker_secret)).as_bytes());
        let key = WorkerSealKey {
            kid: 9,
            public_hex: &worker_public_hex,
        };
        let first = seal_target(&key, &vector_target()).unwrap();
        let second = seal_target(&key, &vector_target()).unwrap();
        assert_ne!(first, second, "fresh ephemeral key and nonce per seal");
        assert!(!first.contains(PUSH_TOKEN));

        let (kid, plaintext) = open_target(worker_secret, &first, AAD).expect("opens");
        assert_eq!(kid, 9);
        assert_eq!(plaintext, PLAINTEXT);
        // Another host/device pair (AAD) or another Worker key cannot open it.
        let other_aad = format!("{DEVICE_ID}|{HOST_ID}");
        assert!(open_target(worker_secret, &first, &other_aad).is_none());
        assert!(open_target(TEST_WORKER_SECRET, &first, AAD).is_none());
    }

    #[test]
    fn production_path_seals_to_production_key() {
        let sealed = seal_target(&PRODUCTION_SEAL_KEY, &vector_target()).unwrap();
        let blob = URL_SAFE_NO_PAD.decode(&sealed).unwrap();
        assert_eq!(blob[0], SEALED_TARGET_VERSION);
        assert_eq!(blob[1], PRODUCTION_SEAL_KEY.kid);
        // version + kid + epk + nonce + plaintext + tag
        assert_eq!(blob.len(), 2 + 32 + 12 + PLAINTEXT.len() + 16);
        assert!(!sealed.contains('=') && !sealed.contains('+') && !sealed.contains('/'));
    }

    #[test]
    fn invalid_worker_key_is_rejected() {
        let key = WorkerSealKey {
            kid: 1,
            public_hex: "abcd",
        };
        assert_eq!(
            seal_target(&key, &vector_target()),
            Err(SealError::InvalidWorkerKey)
        );
        // The all-zero point yields a non-contributory agreement.
        let zero = WorkerSealKey {
            kid: 1,
            public_hex: "0000000000000000000000000000000000000000000000000000000000000000",
        };
        assert_eq!(
            seal_target(&zero, &vector_target()),
            Err(SealError::WeakSharedSecret)
        );
    }
}
