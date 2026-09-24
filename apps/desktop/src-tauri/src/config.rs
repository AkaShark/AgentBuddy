use std::collections::BTreeMap;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

use serde::Serialize;
use toml_edit::{value, DocumentMut, Item, Table, TableLike};

use crate::error::HostError;

/// Per-agent settings the console shows: the `enabled` flag and the
/// configured executable (`shell_bin` for the shell agent, `bin` otherwise).
#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct AgentSettings {
    pub enabled: bool,
    pub bin: Option<String>,
    pub host: Option<String>,
    pub port: Option<u16>,
}

fn bin_key(agent: &str) -> &'static str {
    if agent == "shell" {
        "shell_bin"
    } else {
        "bin"
    }
}

fn parse(text: &str) -> Result<DocumentMut, HostError> {
    text.parse::<DocumentMut>()
        .map_err(|e| HostError::config_invalid(format!("host.toml: {e}")))
}

fn agent_table<'a>(doc: &'a mut DocumentMut, agent: &str) -> Result<&'a mut dyn TableLike, HostError> {
    let agents = doc
        .as_table_mut()
        .entry("agents")
        .or_insert_with(|| {
            let mut t = Table::new();
            t.set_implicit(true);
            Item::Table(t)
        })
        .as_table_like_mut()
        .ok_or_else(|| HostError::config_invalid("host.toml: `agents` is not a table"))?;
    agents
        .entry(agent)
        .or_insert_with(|| Item::Table(Table::new()))
        .as_table_like_mut()
        .ok_or_else(|| {
            HostError::config_invalid(format!("host.toml: `agents.{agent}` is not a table"))
        })
}

pub fn set_agent_enabled(text: &str, agent: &str, enabled: bool) -> Result<String, HostError> {
    let mut doc = parse(text)?;
    agent_table(&mut doc, agent)?.insert("enabled", value(enabled));
    Ok(doc.to_string())
}

pub fn set_agent_bin(text: &str, agent: &str, bin: &str) -> Result<String, HostError> {
    let mut doc = parse(text)?;
    agent_table(&mut doc, agent)?.insert(bin_key(agent), value(bin));
    Ok(doc.to_string())
}

pub fn set_codex_endpoint(text: &str, host: Option<&str>, port: Option<u16>) -> Result<String, HostError> {
    if host.is_some_and(|h| h.trim().is_empty() || h.chars().any(char::is_whitespace)) || port == Some(0) {
        return Err(HostError::config_invalid("host must be nonempty without whitespace; port must be 1–65535"));
    }
    let mut doc = parse(text)?;
    let table = agent_table(&mut doc, "codex")?;
    if let Some(host) = host { table.insert("host", value(host)); }
    if let Some(port) = port { table.insert("port", value(i64::from(port))); }
    Ok(doc.to_string())
}

pub fn read_agent_settings(text: &str) -> Result<BTreeMap<String, AgentSettings>, HostError> {
    let doc = parse(text)?;
    let mut out = BTreeMap::new();
    if let Some(agents) = doc.get("agents").and_then(Item::as_table_like) {
        for (name, item) in agents.iter() {
            let Some(t) = item.as_table_like() else { continue };
            let enabled = t.get("enabled").and_then(Item::as_bool).unwrap_or(true);
            let bin = t.get(bin_key(name)).and_then(Item::as_str).map(str::to_owned);
            let host = t.get("host").and_then(Item::as_str).map(str::to_owned);
            let port = t.get("port").and_then(Item::as_integer).and_then(|p| u16::try_from(p).ok());
            out.insert(name.to_owned(), AgentSettings { enabled, bin, host, port });
        }
    }
    Ok(out)
}

pub fn read_or_empty(path: &Path) -> Result<String, HostError> {
    match std::fs::read_to_string(path) {
        Ok(s) => Ok(s),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(String::new()),
        Err(e) => Err(e.into()),
    }
}

/// Replace `path` atomically. The first write keeps the original as
/// `<name>.bak`. host.toml holds the pairing token, so the new file keeps the
/// original permissions (owner-only 0600 when there was no file yet).
pub fn write_atomic(path: &Path, contents: &str) -> Result<(), HostError> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let mode = std::fs::metadata(path)
        .map(|m| m.permissions().mode() & 0o777)
        .unwrap_or(0o600);
    let bak = path.with_extension("toml.bak");
    if path.exists() && !bak.exists() {
        std::fs::copy(path, &bak)?;
    }
    let tmp = path.with_extension("toml.tmp");
    std::fs::write(&tmp, contents)?;
    std::fs::set_permissions(&tmp, std::fs::Permissions::from_mode(mode))?;
    std::fs::rename(&tmp, path)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const HOST: &str = include_str!("../tests/fixtures/host.toml");

    #[test]
    fn codex_endpoint_changes_preserve_unrelated_config_and_validate_input() {
        let out = set_codex_endpoint(HOST, Some("127.0.0.2"), Some(9000)).unwrap();
        let doc = parse(&out).unwrap();
        assert_eq!(doc["agents"]["codex"]["host"].as_str(), Some("127.0.0.2"));
        assert_eq!(doc["agents"]["codex"]["port"].as_integer(), Some(9000));
        assert_eq!(doc["token"].as_str(), Some("abc123"));
        assert!(out.contains("# keep codex on"));
        assert!(set_codex_endpoint(HOST, Some(""), None).is_err());
        assert!(set_codex_endpoint(HOST, None, Some(0)).is_err());
    }

    #[test]
    fn edits_inline_agent_tables_without_losing_other_settings() {
        for input in [
            "[agents]\ncodex = { enabled = true, bin = 'old', port = 8390 }\n",
            "agents = { codex = { enabled = true, bin = 'old', port = 8390 } }\n",
        ] {
            let out = set_agent_enabled(input, "codex", false).unwrap();
            let out = set_agent_bin(&out, "codex", "/new/codex").unwrap();
            let doc = parse(&out).unwrap();
            assert_eq!(doc["agents"]["codex"]["enabled"].as_bool(), Some(false));
            assert_eq!(doc["agents"]["codex"]["bin"].as_str(), Some("/new/codex"));
            assert_eq!(doc["agents"]["codex"]["port"].as_integer(), Some(8390));
        }
    }

    #[test]
    fn set_agent_enabled_flips_only_that_agent_and_keeps_comments() {
        let out = set_agent_enabled(HOST, "claude", true).unwrap();
        assert!(out.contains("# AgentBuddy host config"));
        assert!(out.contains("enabled = true   # keep codex on"));
        assert!(out.contains("token = \"abc123\""));
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["claude"]["enabled"].as_bool(), Some(true));
        assert_eq!(doc["agents"]["codex"]["enabled"].as_bool(), Some(true));
        assert_eq!(doc["session"]["replay_max_msgs"].as_integer(), Some(2048));
    }

    #[test]
    fn set_agent_bin_replaces_path() {
        let out = set_agent_bin(HOST, "codex", "/opt/homebrew/bin/codex").unwrap();
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["codex"]["bin"].as_str(), Some("/opt/homebrew/bin/codex"));
        assert_eq!(doc["agents"]["codex"]["port"].as_integer(), Some(8390));
    }

    #[test]
    fn set_agent_bin_uses_shell_bin_for_the_shell_agent() {
        let out = set_agent_bin(HOST, "shell", "/opt/homebrew/bin/fish").unwrap();
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["shell"]["shell_bin"].as_str(), Some("/opt/homebrew/bin/fish"));
        assert!(doc["agents"]["shell"].get("bin").is_none());
    }

    #[test]
    fn set_agent_enabled_creates_missing_table() {
        let out = set_agent_enabled("token = \"t\"\n", "pi", true).unwrap();
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["pi"]["enabled"].as_bool(), Some(true));
        assert_eq!(doc["token"].as_str(), Some("t"));
        let out2 = set_agent_enabled("", "pi", false).unwrap();
        let doc2: toml_edit::DocumentMut = out2.parse().unwrap();
        assert_eq!(doc2["agents"]["pi"]["enabled"].as_bool(), Some(false));
    }

    #[test]
    fn invalid_toml_is_rejected() {
        let err = set_agent_enabled("[agents.codex\nenabled = ", "codex", true).unwrap_err();
        assert!(matches!(err.kind, crate::error::HostErrorKind::ConfigInvalid));
    }

    #[test]
    fn read_agent_settings_reports_enabled_and_binary_per_agent() {
        let m = read_agent_settings(HOST).unwrap();
        assert_eq!(m["codex"], AgentSettings { enabled: true, bin: Some("codex".into()), host: Some("127.0.0.1".into()), port: Some(8390) });
        assert_eq!(m["claude"], AgentSettings { enabled: false, bin: Some("claude".into()), host: None, port: None });
        assert_eq!(m["shell"], AgentSettings { enabled: true, bin: Some("/bin/zsh".into()), host: None, port: None });
        assert!(read_agent_settings("").unwrap().is_empty());
    }

    #[test]
    fn write_atomic_backs_up_once_and_replaces_content() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("host.toml");
        std::fs::write(&path, "v1").unwrap();
        write_atomic(&path, "v2").unwrap();
        write_atomic(&path, "v3").unwrap();
        assert_eq!(std::fs::read_to_string(&path).unwrap(), "v3");
        assert_eq!(std::fs::read_to_string(dir.path().join("host.toml.bak")).unwrap(), "v1");
        assert!(!dir.path().join("host.toml.tmp").exists());
    }

    #[test]
    fn write_atomic_keeps_owner_only_permissions() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("host.toml");
        std::fs::write(&path, "v1").unwrap();
        std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600)).unwrap();
        write_atomic(&path, "v2").unwrap();
        let mode = std::fs::metadata(&path).unwrap().permissions().mode() & 0o777;
        assert_eq!(mode, 0o600);
    }

    #[test]
    fn read_or_empty_handles_missing_file() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(read_or_empty(&dir.path().join("nope.toml")).unwrap(), "");
    }
}
