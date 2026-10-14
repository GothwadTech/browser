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
    echo '**Compiler errors**'
    echo '```'
    grep -E '^e: ' "$log" | head -60 || true
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
