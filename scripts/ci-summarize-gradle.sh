#!/usr/bin/env bash
# Writes the parts of a failed Gradle run that actually explain the failure into the GitHub job
# summary. Exists because the raw log lives behind an endpoint a restricted network cannot always
# reach (`gh run view --log` fails with EOF), and because a red X with no message is not reviewable.
#
# Usage: scripts/ci-summarize-gradle.sh [logfile]   (default: gradle.log)
set -uo pipefail

log="${1:-gradle.log}"
out="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

{
  echo "### Gradle failure summary"
  echo

  if [[ ! -s "$log" ]]; then
    echo "_No Gradle output was captured (\`$log\` missing or empty) — the step failed before piping to it._"
  else
    echo '**Compiler errors (Kotlin)**'
    echo '```'
    grep -E '^e: ' "$log" | head -60 || true
    echo '```'

    echo '**Resource compiler errors (aapt2)**'
    echo '```'
    grep -E ' error: |error: failed' "$log" | head -40 || true
    echo '```'

    echo '**What went wrong**'
    echo '```'
    awk '/^FAILURE: Build (failed|completion failed)/{p=1} p{print} /Get more help/{p=0}' "$log" | head -60 || true
    echo '```'

    echo '**Failed tasks**'
    echo '```'
    grep -E '^> Task .*FAILED$' "$log" | head -20 || true
    echo '```'

    reports=$(find . -path '*/build/test-results/*' -name '*.xml' 2>/dev/null | head -20)
    if [[ -n "$reports" ]]; then
      echo '**Failing tests**'
      echo '```'
      while read -r f; do
        grep -oE '<testcase name="[^"]*" classname="[^"]*"' "$f" \
          | sed 's/<testcase name="//; s/" classname="/ > /; s/"$//' | head -30 || true
        grep -oE '<failure message="[^"]{0,300}' "$f" | sed 's/<failure message="//' | head -30 || true
      done <<<"$reports"
      echo '```'
    fi

    echo '**Last 40 lines**'
    echo '```'
    tail -40 "$log" || true
    echo '```'
  fi
} >>"$out"

# A job step's $GITHUB_STEP_SUMMARY is not exposed by the REST API, and the raw log is often
# unreachable from a restricted network — but *annotations* are readable at
# GET /repos/{owner}/{repo}/check-runs/{id}/annotations. So the same digest is also emitted as an
# ::error:: workflow command, which is what a reviewer (or an agent) can actually fetch.
escape_gh() {
  printf '%s\n' "$1" \
    | sed -e 's/%/%25/g' -e 's/:/%3A/g' -e 's/,/%2C/g' -e 's/\r$//' \
    | awk 'NF { out = out ? out "%0A" $0 : $0 } END { printf "%s", out }'
}

if [[ -s "$log" ]]; then
  total=$(grep -cE '^e: ' "$log" || true)
  digest=$(
    {
      [ "$total" -gt 12 ] 2>/dev/null && echo "($total Kotlin compiler errors in total; first 12 below)"
      grep -E '^e: ' "$log" | head -12
      grep -E ' error: |^[[:space:]]*error:' "$log" | head -20
      awk '/^FAILURE: Build (failed|completion failed)/{p=1} p{print} /Get more help/{p=0}' "$log" | head -14
      grep -E '^> Task .*FAILED$' "$log" | head -6
    } | head -32
  )
  if [[ -n "$digest" ]]; then
    echo "::error title=Gradle failure::$(escape_gh "${digest:0:2000}")"
  fi
fi
