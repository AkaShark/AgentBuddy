# Ghostty dependency patches

`sync-ghostty.sh` applies these patches on both mobile build paths. Keep fixes
here instead of committing changes inside the Ghostty submodule. The Makefile
fingerprints `*.patch`, so changing a patch invalidates Ghostty build stamps.

## Wuffs package hash: no patch needed

Under Zig 0.15.2 the pinned Wuffs mirror (tarball SHA-256
`9e4cd20abe96e6c4c6ede9c3057108860126e7be2e2c3e35515476c250be1c13`) fetches to
`N-V-__8AAAzZywE3s51XfsLbP9eyEw57ae9swYB9aGB6fCMs`, which is the hash upstream
already declares (checked with `zig fetch` on 2026-09-24). An earlier
`wuffs-package-hash.patch` pinned a different hash, which broke the build with
`error: hash mismatch`, so it was removed. If a hash mismatch comes back, check
`zig version` first: this checkout needs 0.15.2.

## Proxy handling

The iOS (including Mac Catalyst) and Android Ghostty build scripts default to
removing uppercase and lowercase HTTP/HTTPS/ALL proxy variables only within
the Zig build subprocess. This avoids the HTTP 400 observed with the local
port-7897 proxy. Git/submodule sync and the invoking shell retain their settings.
Use `GHOSTTY_USE_PROXY=1 make ghostty-ios` (or `make ghostty-android`) to retain
proxy variables on networks that require them.

To apply and rebuild: `make ghostty-ios`. Use Zig 0.15.2 for this pinned checkout.
When upgrading Ghostty, recheck whether each patch is still needed.
