# Ghostty dependency patches

`sync-ghostty.sh` applies these patches on both mobile build paths. Keep fixes
here instead of committing changes inside the Ghostty submodule. The Makefile
fingerprints `*.patch`, so changing a patch invalidates Ghostty build stamps.

## Wuffs package hash (Ghostty a968e120d, Zig 0.15.2)

The pinned Wuffs URL returns a package whose Zig hash is
`N-V-__8AAEXUywEb8JCSytwiCVUsFb2CwHjOB59jhyRhOhsj`, but the upstream manifest
declares `N-V-__8AAAzZywE3s51XfsLbP9eyEw57ae9swYB9aGB6fCMs`.
`wuffs-package-hash.patch` pins the verified hash without changing versions or
disabling integrity checks.

Verified on 2026-09-23:

- The mirrored tarball SHA-256 is
  `9e4cd20abe96e6c4c6ede9c3057108860126e7be2e2c3e35515476c250be1c13`.
  This matches the archive hash already recorded in the pinned Ghostty
  `build.zig.zon.json` (`sha256-nkzSCr6W5sTG7enDBXEIhgEm574uLD41UVR2wlC+HBM=`).
- `zig fetch` against both the mirror and the original
  `https://github.com/google/wuffs/archive/refs/tags/v0.4.0-alpha.9.tar.gz`
  yields the corrected Zig hash. The original URL comes from Ghostty history
  before mirror migration, commit `8231ebb77`.

These checks establish a manifest/package-hash mismatch, not evidence that
the server recently replaced the archive. Zig package hashes and archive
SHA-256 checksums are different checks and must not be substituted for each other.
The upstream generated Nix/Flatpak dependency manifests are not used by the
mobile build scripts; this patch targets the Zig manifest they actually consume.

## Proxy handling

The iOS (including Mac Catalyst) and Android Ghostty build scripts default to
removing uppercase and lowercase HTTP/HTTPS/ALL proxy variables only within
the Zig build subprocess. This avoids the HTTP 400 observed with the local
port-7897 proxy. Git/submodule sync and the invoking shell retain their settings.
Use `GHOSTTY_USE_PROXY=1 make ghostty-ios` (or `make ghostty-android`) to retain
proxy variables on networks that require them.

To apply and rebuild: `make ghostty-ios`. Use Zig 0.15.2 for this pinned checkout.
When upgrading Ghostty, recheck whether this patch is still needed.
