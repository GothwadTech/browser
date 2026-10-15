#!/usr/bin/env bash
# Mechanical checks for the rules in AGENTS.md that a reviewer cannot be trusted to remember.
# Everything here is a grep, so it runs in milliseconds and needs no Android SDK - which is exactly
# why the expensive build steps do not have to catch these regressions first.
#
# A line may be exempted by ending it with:  quality-gate: allow
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

SRC=(app/src/main/java app/common/src/main/java)
RES=(app/src/main/res)
fail=0

report() {
  local name="$1"; shift
  local hits="$1"
  if [[ -n "$hits" ]]; then
    echo "FAIL: $name"
    echo "$hits" | sed 's/^/      /'
    fail=1
  else
    echo "ok:   $name"
  fi
}

# strip // comments and allow-marked lines before matching, so explanatory comments never trip a gate
scan() {
  grep -rnE "$1" "${@:2}" --include='*.kt' --include='*.java' 2>/dev/null \
    | grep -v 'quality-gate: allow' \
    | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(\*|//)' \
    || true
}

scan_res() {
  grep -rnE "$1" "${@:2}" --include='*.xml' 2>/dev/null | grep -v 'quality-gate: allow' || true
}

echo "== AGENTS.md mechanical gates =="

# rule 6 / §6 checklist: no blocking calls on the UI thread
report "no runBlocking()" "$(scan 'runBlocking[[:space:]]*\(' "${SRC[@]}")"
report "no Thread.sleep()" "$(scan 'Thread[[:space:]]*\.[[:space:]]*sleep' "${SRC[@]}")"
report "no SharedPreferences .commit()" "$(scan '\.commit\(\)' "${SRC[@]}")"
report "no System.gc() (there is no such thing as a RAM booster)" "$(scan 'System[[:space:]]*\.[[:space:]]*gc\(\)' "${SRC[@]}")"

# rule 5: RecyclerView adapters must not blindly rebind
rv_offenders=""
while IFS= read -r f; do
  body=$(grep -vE '^[[:space:]]*(\*|//)' "$f" | grep -v 'quality-gate: allow')
  if grep -q 'notifyDataSetChanged()' <<<"$body"; then
    rv_offenders+="$f: notifyDataSetChanged in a RecyclerView adapter"$'\n'
  fi
done < <(grep -rl 'RecyclerView.Adapter' "${SRC[@]}" --include='*.kt' 2>/dev/null)
report "no notifyDataSetChanged() in RecyclerView adapters" "$(sed '/^$/d' <<<"$rv_offenders")"

# rule 14: no OEM / device-model specific branches in code
report "no Build.MANUFACTURER / MODEL branching" "$(scan 'Build\.(MANUFACTURER|MODEL|BRAND|DEVICE)[[:space:]]*(==|\.equals|\.contains)' "${SRC[@]}")"

# style: logging must go through android.util.Log
report "no println/stdout" "$(scan 'println[[:space:]]*\(' "${SRC[@]}")"

# i18n: user-visible text must be a resource
toast_lits=$(scan 'Toast\.makeText\([A-Za-z_.]+, *"' "${SRC[@]}")
title_lits=$(scan '\.set(Title|Message)\( *"' "${SRC[@]}")
report "no hard-coded toast text" "$toast_lits"
report "no hard-coded dialog text" "$title_lits"
xml_lits=$(scan_res 'android:(text|hint|contentDescription)="[A-Za-z]' "${RES[@]}/layout")
report "no hard-coded layout text/contentDescription" "$xml_lits"

# theming: colours come from the palette, never from a literal
layout_hex=$(scan_res 'android:(textColor|textColorHint|background|backgroundTint)="#[0-9A-Fa-f]{6}"' "${RES[@]}/layout")
drawable_hex=$(scan_res '<(solid|stroke)[^>]*/?>' "${RES[@]}/drawable" | grep -E 'android:color="#[0-9A-Fa-f]{6}"')
kotlin_hex=$(scan '0xFF[0-9A-Fa-f]{6}' "${SRC[@]}")
report "no hard-coded colours in layouts" "$layout_hex"
report "no hard-coded colours in drawables" "$drawable_hex"
report "no hard-coded colours in Kotlin" "$kotlin_hex"

# resource integrity: a bulk string/colour refactor must not leave a dangling @string/ behind
STR_RES=$(cat app/src/main/res/values/strings.xml app/common/src/main/res/values/strings.xml 2>/dev/null \
  | grep -oE '<(string|plurals) name="[^"]+"' | sed 's/.*name="//; s/"//; s/ *$//' | sort -u)
USED_STR=$(grep -rhoE '@string/[a-zA-Z0-9_]+|(android\.)?R\.string\.[a-zA-Z0-9_]+|R\.plurals\.[a-zA-Z0-9_]+' \
  app/src/main app/common/src/main --include='*.xml' --include='*.kt' 2>/dev/null \
  | grep -v '^android\.R\.string' | sed 's|@string/||; s|R\.string\.||; s|R\.plurals\.||' | sort -u)
missing=$(comm -23 <(printf '%s\n' "$USED_STR") <(printf '%s\n' "$STR_RES"))
report "no dangling string references" "$missing"

# colour integrity, same reason
COL_RES=$(cat app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/common/src/main/res/values/colors.xml 2>/dev/null \
  | grep -oE '<color name="[^"]+"' | sed 's/.*name="//; s/"//' | sort -u; ls app/src/main/res/color 2>/dev/null | sed 's/\.xml$//')
USED_COL=$(grep -rhoE '@color/[a-zA-Z0-9_]+|(android\.)?R\.color\.[a-zA-Z0-9_]+' app/src/main app/common/src/main \
  --include='*.xml' --include='*.kt' 2>/dev/null | grep -v '^android\.R\.color' | sed 's|@color/||; s|R\.color\.||' | sort -u)
missing_col=$(comm -23 <(printf '%s\n' "$USED_COL") <(printf '%s\n' "$COL_RES" | sort -u))
report "no dangling colour references" "$missing_col"

# aapt2 rejects an unescaped apostrophe and a \uXXXX escape in a string resource (a resource is
# not a Kotlin literal). Both came out of scripted extraction, so the script checks the script.
esc=$(python3 - <<'EOF' 2>/dev/null || true
import glob, re
files = sorted(set(glob.glob('app/src/main/res/values*/*.xml') + glob.glob('app/common/src/main/res/values*/*.xml')))
BS = chr(92)
for p in files:
    for i, line in enumerate(open(p, encoding='utf-8'), 1):
        m = re.match(r'\s*<(?:string|item)\b[^>]*>(.*?)</(?:string|item)>\s*$', line)
        if not m:
            continue
        v = m.group(1)
        bad = []
        if re.search(BS + '[uU][0-9a-fA-F]', v):
            bad.append('unicode escape (a resource is not a Kotlin literal)')
        quoted = v.strip().startswith('"') and v.strip().endswith('"') and v.strip().count('"') == 2
        if not quoted:
            for q in [j for j, c in enumerate(v) if c == chr(39)]:
                k = 0
                while q - 1 - k >= 0 and v[q - 1 - k] == BS:
                    k += 1
                if k % 2 == 0:  # 0 = unescaped, 2 = escaped backslash then a live quote
                    bad.append('apostrophe needs a single backslash: %r' % v[max(0, q - 6):q + 2])
                    break
        if bad:
            print('%s:%d: %s' % (p, i, '; '.join(bad)))
EOF
)
report "string resources escape apostrophes and avoid unicode escapes" "$esc"

# CDATA inside a <string> is a hard aapt2 failure ("Can not extract resource from ParsedResource"),
# so escaped entities are the only legal way to carry & or < in a resource.
cdata=$(grep -rn 'CDATA' app/src/main/res/values* app/common/src/main/res/values* 2>/dev/null || true)
report "no CDATA in resource values" "$cdata"

# user-visible text must stay emoji-free (translatable, and readable on every TV font)
emoji=$(grep -rnP '"[^"]*[\x{2600}-\x{27BF}]|"[^"]*\xf0\x9f' app/src/main/res/values/strings.xml 2>/dev/null || true)
report "no emoji in user-visible strings" "$emoji"

# CI must not be the thing that silently stops running: a workflow that fails to parse shows up as
# a red X with no jobs and no log, which everyone learns to ignore.
if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  yml=$(python3 - <<'EOF'
import glob, io, yaml
for f in sorted(glob.glob(".github/workflows/*.yml")):
    try:
        d = yaml.safe_load(io.open(f, encoding="utf-8"))
        jobs = (d or {}).get("jobs") or {}
        assert jobs, "no jobs"
        for name, job in jobs.items():
            assert "runs-on" in job and job.get("steps"), f"job {name} malformed"
            for step in job["steps"]:
                assert isinstance(step, dict), f"job {name}: step is not a mapping: {step!r}"
                assert bool(step.get("uses")) != bool(step.get("run")), f"job {name}/{step.get('name')}: needs exactly one of uses/run"
    except Exception as e:
        print(f"{f}: {type(e).__name__}: {e}")
EOF
)
  report "workflow files parse and define jobs" "$yml"
fi

echo
if [[ $fail -ne 0 ]]; then
  cat <<'EOF'
Quality gate failed. Fix the findings above (see AGENTS.md for why each rule exists).
If a finding is genuinely intentional, end that line with: quality-gate: allow
EOF
  exit 1
fi
echo "Quality gate passed."
