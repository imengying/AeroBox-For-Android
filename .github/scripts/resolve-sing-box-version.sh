#!/usr/bin/env bash
set -euo pipefail

github_api_get() {
  local url="$1"
  local -a curl_args=(
    -fsSL
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28"
    -H "User-Agent: AeroBox-CI"
  )

  if [ -n "${GITHUB_TOKEN:-}" ]; then
    curl_args+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
  fi

  curl "${curl_args[@]}" "${url}"
}

SING_BOX_VERSION="$(
  github_api_get "https://api.github.com/repos/SagerNet/sing-box/releases/latest" \
    | grep '"tag_name":' \
    | sed -E 's/.*"([^"]+)".*/\1/'
)"

if [ -z "${SING_BOX_VERSION}" ]; then
  echo "::error::Failed to resolve latest sing-box version"
  exit 1
fi

echo "SING_BOX_VERSION=${SING_BOX_VERSION}" >> "${GITHUB_ENV}"
echo "Resolved sing-box version: ${SING_BOX_VERSION}"
