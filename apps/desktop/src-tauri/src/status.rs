use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::error::HostError;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AgentInfo {
    pub name: String,
    pub display_name: String,
    pub wire: Value,
    pub available: bool,
    #[serde(default)]
    pub presentation: Option<Value>,
    #[serde(default)]
    pub capabilities: Option<Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StatusInfo {
    pub pid: u32,
    pub node_id: String,
    pub token_short: String,
    #[serde(default)]
    pub relay: Option<String>,
    pub config_path: String,
    pub uptime_secs: u64,
    #[serde(default)]
    pub agents: Vec<AgentInfo>,
    /// Daemon version as reported by `status --json` (absent in older daemons).
    #[serde(default)]
    pub version: Option<String>,
}

impl StatusInfo {
    pub fn is_running(&self) -> bool {
        self.pid > 0
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PairPayload {
    /// The exact JSON line the daemon printed; this is what the phone scans.
    pub raw: String,
    pub node_id: String,
    pub token: String,
    #[serde(default)]
    pub host_name: Option<String>,
    #[serde(default)]
    pub relay: Option<String>,
}

#[derive(Deserialize)]
struct PairWire {
    node_id: String,
    token: String,
    #[serde(default)]
    host_name: Option<String>,
    #[serde(default)]
    relay: Option<String>,
}

pub fn parse_status(stdout: &str) -> Result<StatusInfo, HostError> {
    serde_json::from_str(stdout.trim())
        .map_err(|e| HostError::parse_failed(format!("status --json: {e}")))
}

pub fn parse_pair(stdout: &str) -> Result<PairPayload, HostError> {
    for line in stdout.lines() {
        let line = line.trim();
        if !line.starts_with('{') {
            continue;
        }
        if let Ok(w) = serde_json::from_str::<PairWire>(line) {
            return Ok(PairPayload {
                raw: line.to_string(),
                node_id: w.node_id,
                token: w.token,
                host_name: w.host_name,
                relay: w.relay,
            });
        }
    }
    Err(HostError::parse_failed("pair: no JSON payload line in output"))
}

#[cfg(test)]
mod tests {
    use super::*;

    const STATUS: &str = include_str!("../tests/fixtures/status.json");
    const PAIR: &str = include_str!("../tests/fixtures/pair.txt");

    #[test]
    fn parse_status_reads_real_cli_output() {
        let s = parse_status(STATUS).unwrap();
        assert!(!s.node_id.is_empty());
        assert!(s.config_path.ends_with("host.toml"));
        assert!(s.agents.iter().any(|a| a.name == "codex"));
        assert_eq!(s.is_running(), s.pid > 0);
        assert!(s.version.as_deref().is_some_and(|v| !v.is_empty()));
    }

    #[test]
    fn parse_status_tolerates_unknown_fields() {
        let json = r#"{"pid":42,"node_id":"n","token_short":"ab12","relay":null,"config_path":"/x/host.toml",
            "uptime_secs":7,"future_field":{"a":1},
            "agents":[{"name":"codex","display_name":"Codex","wire":"some_new_wire","available":true,"extra":1}]}"#;
        let s = parse_status(json).unwrap();
        assert_eq!(s.pid, 42);
        assert!(s.is_running());
        assert_eq!(s.agents[0].wire, serde_json::Value::String("some_new_wire".into()));
    }

    #[test]
    fn parse_status_rejects_garbage() {
        let err = parse_status("not json").unwrap_err();
        assert!(matches!(err.kind, crate::error::HostErrorKind::ParseFailed));
    }

    #[test]
    fn parse_pair_reads_real_cli_output() {
        let p = parse_pair(PAIR).unwrap();
        assert!(!p.node_id.is_empty());
        assert!(!p.token.is_empty());
        assert!(p.raw.starts_with('{'));
        let round: serde_json::Value = serde_json::from_str(&p.raw).unwrap();
        assert_eq!(round["node_id"], p.node_id);
    }

    #[test]
    fn parse_pair_skips_non_json_lines() {
        let out = "2026-09-23T10:00:00Z WARN alleycat: generated new host key\n{\"v\":1,\"node_id\":\"abc\",\"token\":\"tok\",\"host_name\":\"studio\"}\n";
        let p = parse_pair(out).unwrap();
        assert_eq!(p.node_id, "abc");
        assert_eq!(p.host_name.as_deref(), Some("studio"));
        assert_eq!(p.raw, "{\"v\":1,\"node_id\":\"abc\",\"token\":\"tok\",\"host_name\":\"studio\"}");
    }
}
