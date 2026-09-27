# Archived Ghostty patches

`litter-mobile-embed.patch` is historical provenance only. Its complete resulting
source tree was committed to [AkaShark/ghostty](https://github.com/AkaShark/ghostty/tree/codex/agentbuddy)
in `e337be2262fc13c978f5b0edab49ab449485ac6a`, based on upstream
`a968e120dd084bd886239d1cac938f0177f019d9`.

Builds consume the pinned fork directly and never apply or reverse this patch.
Commit new changes in the fork and update the parent gitlink. Makefile build stamps
include the fork commit. See [the fork workflow](../../docs/DEVELOPMENT.md#maintained-codex-and-ghostty-forks).

## Wuffs package hash: no patch needed

Under Zig 0.15.2 the pinned Wuffs mirror (tarball SHA-256
`9e4cd20abe96e6c4c6ede9c3057108860126e7be2e2c3e35515476c250be1c13`) fetches to
`N-V-__8AAAzZywE3s51XfsLbP9eyEw57ae9swYB9aGB6fCMs`, which is the hash upstream
already declares (checked with `zig fetch` on 2026-09-24). An earlier
`wuffs-package-hash.patch` pinned a different hash, which broke the build with
`error: hash mismatch`, so it was removed. If a hash mismatch comes back, check
`zig version` first: this checkout needs 0.15.2.

The "fetched package" hash `N-V-__8AAEXUywEb8JCSytwiCVUsFb2CwHjOB59jhyRhOhsj`
is what zig computes when `test/data/artificial-jpeg/hippopotamus-bad-comment-length.jpeg`
is missing. Endpoint security (ESET real-time protection, 2026-09-27) deletes that
deliberately malformed JPEG as soon as it is extracted, so the tarball is fine
but a fresh fetch fails. Do not change the declared hash; copy a complete
package into the Ghostty zig cache (`<cache>/global/p/<declared-hash>`) and
build with `GHOSTTY_KEEP_ZIG_CACHE=1` so zig reuses it instead of re-fetching.

## Proxy handling

The iOS (including Mac Catalyst) and Android Ghostty build scripts default to
removing uppercase and lowercase HTTP/HTTPS/ALL proxy variables only within
the Zig build subprocess. This avoids the HTTP 400 observed with the local
port-7897 proxy. Git/submodule sync and the invoking shell retain their settings.
Use `GHOSTTY_USE_PROXY=1 make ghostty-ios` (or `make ghostty-android`) to retain
proxy variables on networks that require them.

To rebuild: `make ghostty-ios`. Use Zig 0.15.2 for this pinned checkout.
When upgrading Ghostty, review the fork adaptations against upstream changes.
