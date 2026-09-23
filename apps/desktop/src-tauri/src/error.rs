use serde::Serialize;

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum HostErrorKind {
    SidecarMissing,
    CommandFailed { code: Option<i32>, stderr: String },
    ParseFailed,
    PermissionDenied,
    ConfigInvalid,
}

#[derive(Debug, Clone, Serialize, PartialEq, thiserror::Error)]
#[error("{detail}")]
pub struct HostError {
    pub kind: HostErrorKind,
    pub detail: String,
}

impl HostError {
    pub fn sidecar_missing(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::SidecarMissing, detail: detail.into() }
    }
    pub fn command_failed(label: &str, code: Option<i32>, stderr: impl Into<String>) -> Self {
        let stderr = stderr.into();
        let detail = format!("`agentbuddy {label}` exited with {code:?}: {}", stderr.trim());
        Self { kind: HostErrorKind::CommandFailed { code, stderr }, detail }
    }
    pub fn parse_failed(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::ParseFailed, detail: detail.into() }
    }
    pub fn permission_denied(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::PermissionDenied, detail: detail.into() }
    }
    pub fn config_invalid(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::ConfigInvalid, detail: detail.into() }
    }
}

impl From<std::io::Error> for HostError {
    fn from(e: std::io::Error) -> Self {
        if e.kind() == std::io::ErrorKind::PermissionDenied {
            Self::permission_denied(e.to_string())
        } else {
            Self::config_invalid(e.to_string())
        }
    }
}
