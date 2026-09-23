//! Runs the real daemon binary and checks that its JSON still matches what
//! the app parses. Gated by AGENTBUDDY_SIDECAR so `cargo test` stays offline
//! and fast by default; CI sets it to the freshly built sidecar.
//!
//! Note: `pair` starts a daemon if none is running (alleycat's
//! ensure_current_daemon), so a local run leaves one running; stop it with
//! `$AGENTBUDDY_SIDECAR stop`.

use std::process::Command;

use agentbuddy_desktop_lib::status::{parse_pair, parse_status};

fn sidecar() -> Option<String> {
    std::env::var("AGENTBUDDY_SIDECAR").ok()
}

fn run(args: &[&str]) -> String {
    let bin = sidecar().unwrap();
    let out = Command::new(&bin).args(args).output().expect("spawn sidecar");
    assert!(out.status.success(), "{bin} {args:?} failed: {}", String::from_utf8_lossy(&out.stderr));
    String::from_utf8_lossy(&out.stdout).into_owned()
}

#[test]
fn status_json_matches_app_types() {
    if sidecar().is_none() {
        eprintln!("AGENTBUDDY_SIDECAR not set; skipping");
        return;
    }
    let s = parse_status(&run(&["status", "--json"])).expect("status --json parses");
    assert!(!s.node_id.is_empty());
    assert!(s.config_path.ends_with("host.toml"));
    assert!(s.agents.iter().any(|a| a.name == "codex"));
    assert!(s.version.is_some(), "daemon should report its version");
}

#[test]
fn pair_output_matches_app_types() {
    if sidecar().is_none() {
        eprintln!("AGENTBUDDY_SIDECAR not set; skipping");
        return;
    }
    let p = parse_pair(&run(&["pair"])).expect("pair parses");
    assert!(!p.node_id.is_empty());
    assert!(!p.token.is_empty());
}
