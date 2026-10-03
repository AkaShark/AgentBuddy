# HarmonyOS compatibility patches

These packages are copied from their crates.io source distributions, including
licenses, upstream documentation, examples and tests. The workspace's
`[patch.crates-io]` selects them; no submodule or generated Rust API was modified.

| Package | Original crates.io checksum | Changes |
| --- | --- | --- |
| nix 0.29.0 | `71e2746dc3a24dd78b3cfcb7be93368c6de9963d30f43a6a73998a9cf4b17b46` | Infer the platform `cmsghdr.cmsg_len` integer type when assigning (OHOS uses `u32`); permit unused platform socket helpers only under `target_env = "ohos"`. |
| rustyline 14.0.0 | `7803e8936da37efd9b6d4478277f4b2b9bb5cdb37a113e8d63222e58da647e63` | Under OHOS, call the SDK's `libc::ioctl` signature for terminal size. Other platforms retain the original nix macro. |

The Rust target is `aarch64-unknown-linux-ohos`. OHOS has `target_os = "linux"`
with its own libc ABI; Android binaries and a glibc Linux build are not compatible.

Validation on 2026-10-02: shared client OHOS check and release build; installed
and executed on HUAWEI Pura X API 24. On macOS, rustyline's library tests pass
(177 passed, 1 ignored); nix's two `test_scm_rights` tests pass. Running nix as a
standalone package on Rust 1.98.1 needs `RUSTFLAGS="--cap-lints warn"` because of
upstream `deny(unused)` helpers. This does not disable type checks or test assertions.
