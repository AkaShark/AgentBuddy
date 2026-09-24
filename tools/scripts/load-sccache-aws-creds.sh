#!/usr/bin/env bash
# Opt-in remote (S3/R2) backend for sccache. Sourced by build-rust.sh and
# generate-bindings.sh.
#
# Nothing is configured by default: without SCCACHE_BUCKET, sccache (when
# installed) uses its local disk cache. To opt in, export one of:
#   - Cloudflare R2, or any other S3-compatible endpoint (region defaults
#     to `auto`):
#       SCCACHE_BUCKET=<bucket>
#       SCCACHE_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
#       AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=...
#   - Plain AWS S3 (no SCCACHE_ENDPOINT):
#       SCCACHE_BUCKET=<bucket>
#       AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=...   (or AWS_PROFILE /
#                                                          SCCACHE_AWS_PROFILE)
#       SCCACHE_REGION=<region>   (optional; falls back to AWS_REGION)
# A bucket with neither an endpoint nor AWS credentials is treated as not
# configured and falls through to the local cache, so a stray bucket name can
# never point sccache at someone else's AWS bucket.
# CI maps its SCCACHE_R2_* secrets onto these same variables, and blanks
# SCCACHE_BUCKET itself when those secrets are absent, which also falls
# through to the local-cache branch below.

if [ -n "${SCCACHE_BUCKET:-}" ] && [ -z "${SCCACHE_ENDPOINT:-}" ] \
  && [ -z "${AWS_ACCESS_KEY_ID:-}" ] && [ -z "${AWS_PROFILE:-}" ] \
  && [ -z "${SCCACHE_AWS_PROFILE:-}" ]; then
  SCCACHE_BUCKET=""
fi

if [ -n "${SCCACHE_BUCKET:-}" ]; then
  export SCCACHE_BUCKET
  export SCCACHE_S3_USE_SSL="${SCCACHE_S3_USE_SSL:-true}"
  export SCCACHE_S3_KEY_PREFIX="${SCCACHE_S3_KEY_PREFIX:-machine}"
  if [ -n "${SCCACHE_ENDPOINT:-}" ]; then
    export SCCACHE_ENDPOINT
    export SCCACHE_REGION="${SCCACHE_REGION:-auto}"
  else
    # Plain AWS S3: no explicit endpoint needed. Never default this to the
    # upstream litter R2 endpoint, and never default the region to R2's
    # `auto` (sccache would target s3.auto.amazonaws.com).
    unset SCCACHE_ENDPOINT
    if [ -n "${SCCACHE_REGION:-}" ]; then
      export SCCACHE_REGION
    elif [ -n "${AWS_REGION:-}" ]; then
      export SCCACHE_REGION="$AWS_REGION"
    else
      unset SCCACHE_REGION
    fi
  fi
  if [ -n "${SCCACHE_AWS_PROFILE:-}" ]; then
    export AWS_PROFILE="$SCCACHE_AWS_PROFILE"
  fi
else
  # An empty-but-set SCCACHE_BUCKET still selects the S3 backend in sccache
  # (CI blanks these when secrets are absent), so drop them entirely.
  unset SCCACHE_BUCKET SCCACHE_ENDPOINT SCCACHE_S3_KEY_PREFIX
fi
