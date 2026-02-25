#!/usr/bin/env bash
set -euo pipefail

DOC1="docs/protocol-spec.md"
DOC2="docs/protocol-conformance-spec.md"

# Adjust in CI/env if your protocol-impact surface changes.
PROTOCOL_IMPACT_PATH_REGEX="${PROTOCOL_IMPACT_PATH_REGEX:-^src/main/java/com/deriklima/retronap/(message|session|transfer|search|channel|user|hotlist)/|^src/main/java/com/deriklima/retronap/(Server|MetaServer)\\.java$}"

BASE_REF="${1:-}"
if [[ -z "${BASE_REF}" ]]; then
  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    BASE_REF="origin/${GITHUB_BASE_REF}"
  else
    BASE_REF="origin/main"
  fi
fi

if ! git rev-parse --verify "${BASE_REF}" >/dev/null 2>&1; then
  echo "warning: base ref '${BASE_REF}' not found; skipping protocol doc guard"
  exit 0
fi

MERGE_BASE="$(git merge-base "${BASE_REF}" HEAD)"
CHANGED_FILES="$(git diff --name-only "${MERGE_BASE}"...HEAD)"

if [[ -z "${CHANGED_FILES}" ]]; then
  echo "No changed files detected."
  exit 0
fi

DOCS_UPDATED=false
if grep -Eq "^${DOC1}$|^${DOC2}$" <<<"${CHANGED_FILES}"; then
  DOCS_UPDATED=true
fi

PROTOCOL_FILES="$(grep -E "${PROTOCOL_IMPACT_PATH_REGEX}" <<<"${CHANGED_FILES}" || true)"

if [[ -z "${PROTOCOL_FILES}" ]]; then
  echo "No protocol-impacting files changed."
  exit 0
fi

if [[ "${DOCS_UPDATED}" == true ]]; then
  echo "Protocol docs updated; check passed."
  exit 0
fi

echo "Protocol-impacting files changed but protocol docs were not updated."
echo
echo "Changed protocol-impacting files:"
printf '%s\n' "${PROTOCOL_FILES}"
echo
echo "Please update at least one of:"
echo "  - ${DOC1}"
echo "  - ${DOC2}"
exit 1
