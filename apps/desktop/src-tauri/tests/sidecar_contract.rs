//! Runs the real daemon binary and checks that its JSON still matches what
//! the app parses. Gated by AGENTBUDDY_SIDECAR so `cargo test` stays offline
//! and fast by default; CI sets it to the freshly built sidecar.
//!
//! Note: `pair` starts a daemon if none is running (alleycat's
//! ensure_current_daemon), so a local run leaves one running; stop it with
//! `$AGENTBUDDY_SIDECAR stop`.

use std::io::{Read, Seek, SeekFrom};
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

use agentbuddy_desktop_lib::status::{parse_pair, parse_status};

fn sidecar() -> Option<String> {
    std::env::var("AGENTBUDDY_SIDECAR").ok()
}

fn run(args: &[&str]) -> String {
    let bin = sidecar().unwrap();
    run_command(Command::new(&bin).args(args), Duration::from_secs(90))
}

fn run_command(command: &mut Command, timeout: Duration) -> String {
    // Files avoid both pipe-buffer deadlocks and waiting for EOF from a
    // descendant that inherited stdout after the CLI itself exited.
    let mut stdout = tempfile::tempfile().expect("stdout file");
    let mut stderr = tempfile::tempfile().expect("stderr file");
    let mut child = command
        .stdin(Stdio::null())
        .stdout(stdout.try_clone().unwrap())
        .stderr(stderr.try_clone().unwrap())
        .spawn()
        .expect("spawn sidecar");
    let deadline = Instant::now() + timeout;
    let status = loop {
        if let Some(status) = child.try_wait().expect("poll sidecar") {
            break Some(status);
        }
        if Instant::now() >= deadline {
            child.kill().expect("kill timed-out sidecar");
            child.wait().expect("reap timed-out sidecar");
            break None;
        }
        std::thread::sleep(Duration::from_millis(50));
    };
    stderr.seek(SeekFrom::Start(0)).unwrap();
    let mut error = String::new();
    stderr.read_to_string(&mut error).unwrap();
    // Do not print stdout on failure: pair's output contains the bearer token.
    assert!(status.is_some(), "{command:?} timed out after {timeout:?}: {error}");
    assert!(status.unwrap().success(), "{command:?} failed: {error}");
    stdout.seek(SeekFrom::Start(0)).unwrap();
    let mut output = String::new();
    stdout.read_to_string(&mut output).unwrap();
    output
}

#[test]
fn pair_and_status_match_app_types() {
    if sidecar().is_none() {
        eprintln!("AGENTBUDDY_SIDECAR not set; skipping");
        return;
    }
    // pair establishes a ready daemon before status queries it. Separate
    // parallel tests race daemon startup against offline status initialization.
    let p = parse_pair(&run(&["pair"])).expect("pair parses");
    assert!(!p.node_id.is_empty());
    assert!(!p.token.is_empty());
    let s = parse_status(&run(&["status", "--json"])).expect("status --json parses");
    assert!(!s.node_id.is_empty());
    assert!(s.config_path.ends_with("host.toml"));
    assert!(s.agents.iter().any(|a| a.name == "codex"));
    assert!(s.version.is_some(), "daemon should report its version");
    assert_eq!(s.node_id, p.node_id);
}

#[test]
#[should_panic(expected = "timed out")]
fn hung_command_is_bounded() {
    run_command(Command::new("/bin/sleep").arg("30"), Duration::from_millis(100));
}

#[test]
fn large_output_does_not_fill_a_pipe() {
    let output = run_command(
        Command::new("/bin/sh").args(["-c", "head -c 131072 /dev/zero"]),
        Duration::from_secs(5),
    );
    assert_eq!(output.len(), 131072);
}
