//! The user's login-shell environment for sidecar calls.
//!
//! A Finder-launched app inherits launchd's minimal PATH
//! (/usr/bin:/bin:/usr/sbin:/sbin). `agentbuddy install` copies its own PATH
//! and SHELL into the LaunchAgent plist, and a daemon started by `pair` also
//! inherits them, so without this the daemon cannot find codex, claude, pi…
//! installed via Homebrew, npm or nvm.

use std::io::Read;
use std::process::{Command, Stdio};
use std::sync::OnceLock;
use std::time::{Duration, Instant};

pub const DELIM: &str = "__AGENTBUDDY_ENV_DELIMITER__";

/// Extract PATH from `env` output printed between two DELIM markers, so shell
/// rc noise (motd, nvm banners) before or after is ignored.
pub fn parse_path(output: &str) -> Option<String> {
    let start = output.find(DELIM)? + DELIM.len();
    let rest = &output[start..];
    let end = rest.find(DELIM)?;
    rest[..end]
        .lines()
        .find_map(|l| l.strip_prefix("PATH="))
        .map(str::to_owned)
}

/// Login PATH first (in its order, de-duplicated), then any fallback
/// directory it did not already contain.
pub fn merge_path(primary: Option<&str>, fallback: &[&str]) -> String {
    let mut out: Vec<&str> = Vec::new();
    for dir in primary.unwrap_or("").split(':').chain(fallback.iter().copied()) {
        if !dir.is_empty() && !out.contains(&dir) {
            out.push(dir);
        }
    }
    out.join(":")
}

pub fn user_shell() -> String {
    std::env::var("SHELL")
        .ok()
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "/bin/zsh".to_owned())
}

fn login_shell_output(shell: &str, timeout: Duration) -> Option<String> {
    let script = format!("printf '{DELIM}'; env; printf '{DELIM}'; exit");
    let mut child = Command::new(shell)
        .args(["-ilc", &script])
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;
    let deadline = Instant::now() + timeout;
    let mut stdout = child.stdout.take()?;
    let (sender, receiver) = std::sync::mpsc::sync_channel(1);
    std::thread::spawn(move || {
        let mut output = String::new();
        let result = stdout.read_to_string(&mut output).map(|_| output);
        let _ = sender.send(result);
    });
    loop {
        match child.try_wait() {
            Ok(Some(_)) => break,
            Ok(None) if Instant::now() < deadline => std::thread::sleep(Duration::from_millis(50)),
            _ => {
                let _ = child.kill();
                let _ = child.wait();
                return None;
            }
        }
    }
    // A shell helper may inherit stdout; keep the same deadline even if the
    // shell itself has exited before that helper closes the pipe.
    receiver.recv_timeout(deadline.saturating_duration_since(Instant::now())).ok()?.ok()
}

/// PATH to hand to every sidecar process; computed once per app run.
pub fn user_path() -> &'static str {
    static PATH: OnceLock<String> = OnceLock::new();
    PATH.get_or_init(|| {
        let home = dirs::home_dir().map(|h| h.to_string_lossy().into_owned()).unwrap_or_default();
        let fallback_owned = [
            "/opt/homebrew/bin".to_owned(),
            "/opt/homebrew/sbin".to_owned(),
            "/usr/local/bin".to_owned(),
            format!("{home}/.local/bin"),
            format!("{home}/.cargo/bin"),
            format!("{home}/.bun/bin"),
            "/usr/bin".to_owned(),
            "/bin".to_owned(),
            "/usr/sbin".to_owned(),
            "/sbin".to_owned(),
        ];
        let fallback: Vec<&str> = fallback_owned.iter().map(String::as_str).collect();
        let login = login_shell_output(&user_shell(), Duration::from_secs(5))
            .and_then(|o| parse_path(&o));
        merge_path(login.as_deref(), &fallback)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn login_shell_drains_output_larger_than_the_pipe_buffer() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let shell = dir.path().join("shell");
        std::fs::write(&shell, format!(
            "#!/bin/sh\n/usr/bin/head -c 262144 /dev/zero\nprintf '{DELIM}\\nPATH=/test/bin\\n{DELIM}'\n"
        )).unwrap();
        std::fs::set_permissions(&shell, std::fs::Permissions::from_mode(0o700)).unwrap();
        let output = login_shell_output(shell.to_str().unwrap(), Duration::from_secs(2));
        assert_eq!(output.as_deref().and_then(parse_path).as_deref(), Some("/test/bin"));
    }

    #[test]
    fn parse_path_reads_path_between_delimiters_and_ignores_rc_noise() {
        let out = format!(
            "Last login: Wed\nnvm: using node 22\n{DELIM}HOME=/Users/me\nPATH=/Users/me/.nvm/versions/node/v22/bin:/opt/homebrew/bin:/usr/bin\nSHELL=/bin/zsh\n{DELIM}\nbye\n"
        );
        assert_eq!(
            parse_path(&out).as_deref(),
            Some("/Users/me/.nvm/versions/node/v22/bin:/opt/homebrew/bin:/usr/bin")
        );
    }

    #[test]
    fn parse_path_returns_none_without_delimited_block() {
        assert_eq!(parse_path("PATH=/usr/bin\n"), None);
        assert_eq!(parse_path(&format!("{DELIM}HOME=/x\n{DELIM}")), None);
    }

    #[test]
    fn merge_path_keeps_login_order_dedups_and_appends_missing_fallbacks() {
        let merged = merge_path(Some("/opt/homebrew/bin:/usr/bin:/opt/homebrew/bin"), &["/usr/bin", "/usr/local/bin"]);
        assert_eq!(merged, "/opt/homebrew/bin:/usr/bin:/usr/local/bin");
        assert_eq!(merge_path(None, &["/usr/local/bin", "/usr/bin"]), "/usr/local/bin:/usr/bin");
        assert_eq!(merge_path(Some(""), &["/bin"]), "/bin");
    }
}
