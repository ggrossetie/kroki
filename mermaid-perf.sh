#!/bin/bash
# Memory leak / cascade-failure test for kroki-mermaid.
#
# Reproduces the suspected production scenario:
#   1. Pathological diagrams exceed the 10s convert timeout (408) but the
#      runaway render keeps running inside Chromium (the Promise.race in
#      worker.js does not cancel page.evaluate).
#   2. Chromium gets wedged: CDP calls outside the race (newPage, goto,
#      close...) stall up to puppeteer's protocolTimeout (180s default)
#      -> ProtocolError "Runtime.callFunctionOn timed out".
#   3. The core (Java) delegates with no timeout (Delegator.java): pending
#      requests pile up in the vert.x wait queue -> heap growth -> OOM.
#
# Phases:
#   calibrate  find a diagram size that blows the 10s convert timeout
#   baseline   idle monitoring (15s)
#   A          healthy concurrent load (normal diagrams only)
#   B          poison: normal load + pathological diagrams in parallel
#   C          flood probe: fire-and-forget burst while mermaid is wedged
#              (measures pending-request buildup in the core)
#   D          recovery: no load at all, watch if Chromium/core recover
#   E          cold-start race: restart mermaid + concurrent burst, count
#              Chromium *main* processes (should be exactly 1)
#
# Usage: ./mermaid-perf.sh
# Tunables (env): NORMAL_WORKERS SLOW_WORKERS PHASE_A_DURATION
#                 POISON_DURATION FLOOD_REQUESTS RECOVERY_DURATION
#                 MON_INTERVAL GATEWAY_URL

set -u

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8000}
MERMAID_NAME=${MERMAID_NAME:-kroki-mermaid}
NORMAL_WORKERS=${NORMAL_WORKERS:-10}
SLOW_WORKERS=${SLOW_WORKERS:-4}
PHASE_A_DURATION=${PHASE_A_DURATION:-45}
POISON_DURATION=${POISON_DURATION:-90}
FLOOD_REQUESTS=${FLOOD_REQUESTS:-400}
RECOVERY_DURATION=${RECOVERY_DURATION:-120}
MON_INTERVAL=${MON_INTERVAL:-5}

# Simple generic mermaid diagram: "graph TD; A-->B"
# Pre-encoded in Kroki's deflate+base64url format
DIAGRAM_PATH="/mermaid/svg/eNpLL0osyFDwCeJSAILkTA1nT00FXV07hZTUgpz8St2U1DINBBNFqiS1uEQDiY0iWVCUn6KBxNYEAKfcIkM="

# Poison requests need mermaid's edge cap lifted. maxEdges is settable via a
# query param, but maxTextSize / securityLevel are ignored by config.js for
# security, so the diagram source MUST stay under the default maxTextSize
# (~50000 chars). The dash form max-edges -> camelCase maxEdges (config.js).
POISON_QS="max-edges=500000"

RUN_DIR="perf-results/run-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN_DIR"
REQ_LOG="$RUN_DIR/requests.log"   # epoch phase label http_code time_total
MON_LOG="$RUN_DIR/monitor.log"    # epoch phase mermaid_mib core_mib chrom_main chrom_rend chrom_rss_kb
PHASE_FILE="$RUN_DIR/.phase"
STOP_FILE="$RUN_DIR/.stop"
END_FILE="$RUN_DIR/.end"
SLOW_FILE="$RUN_DIR/slow.mmd"
START_TS=$(date +%s)

KROKI_CID=$(docker compose ps -q kroki 2>/dev/null)
if [ -z "$KROKI_CID" ]; then
  echo "ERROR: kroki core container not found (run 'docker compose up -d' first)"
  exit 1
fi

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

phase() {
  echo "$1" > "$PHASE_FILE"
  log "=== Phase $1: $2 ==="
}

# Convert a docker-stats memory string (e.g. "215.4MiB") to MiB
to_mib() {
  awk -v s="$1" 'BEGIN {
    if (s ~ /GiB/)      printf "%.1f", substr(s, 1, length(s)-3) * 1024
    else if (s ~ /MiB/) printf "%.1f", substr(s, 1, length(s)-3) + 0
    else if (s ~ /KiB/) printf "%.1f", substr(s, 1, length(s)-3) / 1024
    else if (s ~ /B/)   printf "%.1f", substr(s, 1, length(s)-1) / 1048576
    else                printf "0"
  }'
}

# Inspect chromium processes inside the mermaid container via /proc
# (busybox ps has no RSS column). Prints: "<main> <renderers> <total_rss_kb>"
# main = browser master processes (no --type= flag); should always be 1.
chrome_stats() {
  docker exec "$MERMAID_NAME" sh -c '
    main=0; rend=0; rss=0
    for d in /proc/[0-9]*; do
      [ -r "$d/cmdline" ] || continue
      cmd=$(tr "\0" " " < "$d/cmdline" 2>/dev/null)
      case "$cmd" in
        *chrom*)
          k=$(awk "/^VmRSS:/ {print \$2}" "$d/status" 2>/dev/null)
          rss=$((rss + ${k:-0}))
          case "$cmd" in
            *--type=renderer*) rend=$((rend + 1)) ;;
            *--type=*) : ;;
            *) main=$((main + 1)) ;;
          esac
        ;;
      esac
    done
    echo "$main $rend $rss"
  ' 2>/dev/null || echo "0 0 0"
}

monitor() {
  echo "epoch phase mermaid_mib core_mib chrom_main chrom_rend chrom_rss_kb" > "$MON_LOG"
  while [ ! -f "$END_FILE" ]; do
    local ts cur stats mmem cmem cstats
    ts=$(date +%s)
    cur=$(cat "$PHASE_FILE" 2>/dev/null || echo "-")
    stats=$(docker stats --no-stream --format "{{.Name}} {{.MemUsage}}" "$MERMAID_NAME" "$KROKI_CID" 2>/dev/null) || true
    mmem=$(echo "$stats" | awk -v n="$MERMAID_NAME" '$1 == n {print $2}')
    cmem=$(echo "$stats" | awk -v n="$MERMAID_NAME" '$1 != n && NF {print $2}')
    cstats=$(chrome_stats)
    echo "$ts $cur $(to_mib "${mmem:-0}") $(to_mib "${cmem:-0}") $cstats" >> "$MON_LOG"
    sleep "$MON_INTERVAL"
  done
}

wait_ready() {
  local tries=$1 i code
  for i in $(seq 1 "$tries"); do
    code=$(curl -s -o /dev/null --max-time 15 -w "%{http_code}" "${GATEWAY_URL}${DIAGRAM_PATH}" 2>/dev/null) || true
    [ "$code" = "200" ] && return 0
    sleep 2
  done
  return 1
}

# Dense flowchart: each node links to its successor and to a far node,
# which makes the dagre layout cost explode with size (~O(n^2) observed:
# 100 nodes ~1s, 200 nodes ~4s). Labels are kept short on purpose so the
# source stays under mermaid's non-overridable maxTextSize (~50000 chars).
gen_slow() {
  local n=$1 i
  {
    echo "graph TD"
    for i in $(seq 1 "$n"); do
      echo "n$i-->n$((i % n + 1))"
      echo "n$i-->n$(( (i * 7) % n + 1 ))"
    done
  } > "$SLOW_FILE"
}

calibrate() {
  log "=== Calibration: finding a diagram size that exceeds the 10s convert timeout ==="
  local size chosen=0 out code t bytes
  # Short-label source stays ~20 bytes/node, so even 1600 nodes (~32KB) is
  # under maxTextSize. max-edges is lifted via POISON_QS so the edge cap
  # (default 500) never short-circuits the render into a fast 400.
  for size in 100 200 300 450 700 1100 1600; do
    gen_slow "$size"
    bytes=$(wc -c < "$SLOW_FILE")
    out=$(curl -s -o /dev/null --max-time 40 -w "%{http_code} %{time_total}" \
      -X POST -H "Content-Type: text/plain" --data-binary "@$SLOW_FILE" \
      "${GATEWAY_URL}/mermaid/svg?${POISON_QS}" 2>/dev/null) || true
    code=${out%% *}; t=${out##* }
    log "  size=$size nodes (${bytes}B) -> HTTP $code in ${t}s"
    if [ "$code" = "408" ] || [ "$code" = "000" ]; then
      chosen=$size
      break
    fi
    if [ "$code" = "400" ]; then
      log "  NOTE: HTTP 400 at $size nodes (likely maxTextSize/maxEdges) — stopping escalation"
      break
    fi
  done
  if [ "$chosen" -eq 0 ]; then
    # No 408 yet: pick the largest size that still rendered (<10s) and push
    # ~40% further so the render unambiguously overshoots the 10s timeout,
    # while clamping to keep the source under maxTextSize.
    chosen=$(( size * 14 / 10 ))
    [ "$chosen" -gt 2000 ] && chosen=2000
    log "  no 408 during sweep; targeting $chosen nodes to overshoot 10s"
  fi
  gen_slow "$chosen"
  bytes=$(wc -c < "$SLOW_FILE")
  log "  -> poison diagram: $chosen nodes (${bytes} bytes, must stay < ~50000)"
}

normal_worker() {
  local p=$1 out
  while [ ! -f "$STOP_FILE" ]; do
    out=$(curl -s -o /dev/null --max-time 30 -w "%{http_code} %{time_total}" \
      "${GATEWAY_URL}${DIAGRAM_PATH}" 2>/dev/null) || true
    echo "$(date +%s) $p normal ${out:-000 0}" >> "$REQ_LOG"
  done
}

slow_worker() {
  local p=$1 out
  while [ ! -f "$STOP_FILE" ]; do
    out=$(curl -s -o /dev/null --max-time 30 -w "%{http_code} %{time_total}" \
      -X POST -H "Content-Type: text/plain" --data-binary "@$SLOW_FILE" \
      "${GATEWAY_URL}/mermaid/svg?${POISON_QS}" 2>/dev/null) || true
    echo "$(date +%s) $p slow ${out:-000 0}" >> "$REQ_LOG"
  done
}

RACE_MAIN=0
RACE_REND=0
race_test() {
  log "Restarting $MERMAID_NAME and firing a concurrent burst (cold-start race)..."
  docker restart "$MERMAID_NAME" > /dev/null
  sleep 4
  local try codes
  for try in 1 2 3 4 5; do
    codes=$(seq 1 12 | xargs -P 12 -I{} curl -s -o /dev/null --max-time 25 -w "%{http_code}\n" \
      "${GATEWAY_URL}${DIAGRAM_PATH}" 2>/dev/null) || true
    log "  burst $try -> $(echo "$codes" | sort | uniq -c | sort -rn | tr -s '\n ' ' ')"
    # stop as soon as requests actually reached mermaid (not 000/502/503)
    if echo "$codes" | grep -qE "^(200|408|500)$"; then
      break
    fi
    sleep 2
  done
  sleep 5
  read -r RACE_MAIN RACE_REND _ <<< "$(chrome_stats)"
  log "  Chromium main processes after cold-start burst: $RACE_MAIN (renderers: $RACE_REND)"
}

summary() {
  local p l codes stats
  echo ""
  echo "============================================"
  echo "  SUMMARY  (artifacts in $RUN_DIR)"
  echo "============================================"
  echo ""
  echo "--- Requests (per phase) ---"
  for p in A B C; do
    for l in normal slow flood; do
      codes=$(awk -v p="$p" -v l="$l" '$2 == p && $3 == l {c[$4]++} END {for (k in c) printf "%s:%d ", k, c[k]}' "$REQ_LOG")
      [ -z "$codes" ] && continue
      stats=$(awk -v p="$p" -v l="$l" '$2 == p && $3 == l {print $5}' "$REQ_LOG" | sort -n | \
        awk '{a[NR] = $1} END {
          if (NR == 0) exit
          i95 = int(NR * 0.95); if (i95 < 1) i95 = 1
          printf "p50=%.2fs p95=%.2fs max=%.2fs", a[int((NR + 1) / 2)], a[i95], a[NR]
        }')
      printf "  %s/%-6s codes[ %s] %s\n" "$p" "$l" "$codes" "$stats"
    done
  done
  echo ""
  echo "--- Containers (max observed per phase) ---"
  awk 'NR > 1 {
      p = $2
      if (!(p in seen)) { order[++n] = p; seen[p] = 1 }
      if ($3 > mm[p]) mm[p] = $3
      if ($4 > cm[p]) cm[p] = $4
      if ($5 > mn[p]) mn[p] = $5
      if ($6 > rd[p]) rd[p] = $6
      if ($7 > rs[p]) rs[p] = $7
    }
    END {
      printf "  %-9s %12s %12s %11s %10s %12s\n", "phase", "mermaid", "core", "chrom-main", "renderers", "chrom-rss"
      for (i = 1; i <= n; i++) {
        p = order[i]
        printf "  %-9s %9.1fMiB %9.1fMiB %11d %10d %9.0fMiB\n", p, mm[p], cm[p], mn[p], rd[p], rs[p] / 1024
      }
    }' "$MON_LOG"
  echo ""
  echo "--- Service logs since test start ---"
  local m_timeouts m_proto core_oom
  m_timeouts=$(docker logs --since "$START_TS" "$MERMAID_NAME" 2>&1 | grep -c "TimeoutError")
  m_proto=$(docker logs --since "$START_TS" "$MERMAID_NAME" 2>&1 | grep -c "ProtocolError")
  core_oom=$(docker logs --since "$START_TS" "$KROKI_CID" 2>&1 | grep -c "OutOfMemoryError")
  echo "  mermaid TimeoutError:  $m_timeouts"
  echo "  mermaid ProtocolError: $m_proto"
  echo "  core OutOfMemoryError: $core_oom"
  echo ""
  echo "--- Verdicts ---"
  local base_rend end_rend end_main core_base core_peak_b core_end
  base_rend=$(awk 'NR > 1 && $2 == "baseline" {print $6; exit}' "$MON_LOG")
  core_base=$(awk 'NR > 1 && $2 == "baseline" {print $4; exit}' "$MON_LOG")
  end_rend=$(awk '$2 == "D" {r = $6} END {print r}' "$MON_LOG")
  end_main=$(awk '$2 == "D" {m = $5} END {print m}' "$MON_LOG")
  core_end=$(awk '$2 == "D" {c = $4} END {print c}' "$MON_LOG")
  core_peak_b=$(awk '($2 == "B" || $2 == "C") && $4 > x {x = $4} END {print x + 0}' "$MON_LOG")

  if [ "${end_rend:-0}" -gt "${base_rend:-0}" ]; then
    echo "  [LEAK]  renderers: ${base_rend:-?} at baseline -> ${end_rend:-?} after ${RECOVERY_DURATION}s idle"
    echo "          -> runaway renders / unclosed pages survive the convert timeout"
  else
    echo "  [ok]    renderer count back to baseline after recovery"
  fi
  if [ "${m_proto:-0}" -gt 0 ]; then
    echo "  [WEDGE] $m_proto ProtocolError(s): CDP calls stalled past protocolTimeout"
    echo "          -> Chromium was wedged; requests held pages for up to 180s"
  else
    echo "  [ok]    no ProtocolError: Chromium never fully wedged during this run"
  fi
  if awk -v a="${core_end:-0}" -v b="${core_base:-0}" 'BEGIN {exit !(a > b + 100)}'; then
    echo "  [CORE]  core memory: ${core_base}MiB baseline -> ${core_peak_b}MiB peak -> ${core_end}MiB after recovery"
    echo "          -> pending delegated requests accumulate (no timeout in Delegator)"
  else
    echo "  [ok]    core memory: ${core_base:-?}MiB baseline -> ${core_peak_b:-?}MiB peak -> ${core_end:-?}MiB after recovery"
  fi
  if [ "${RACE_MAIN:-0}" -gt 1 ]; then
    echo "  [RACE]  $RACE_MAIN Chromium main processes after cold-start burst (expected 1)"
    echo "          -> getBrowserWSEndpoint launched concurrent browsers; extras are leaked"
  else
    echo "  [ok]    single Chromium main process after cold-start burst"
  fi
}

cleanup() {
  touch "$STOP_FILE" "$END_FILE" 2>/dev/null
  local j
  for j in $(jobs -p); do
    kill "$j" 2>/dev/null
  done
}
trap cleanup EXIT INT TERM

echo "============================================"
echo "  Mermaid cascade-failure / leak test"
echo "  gateway=$GATEWAY_URL  run_dir=$RUN_DIR"
echo "  normal_workers=$NORMAL_WORKERS slow_workers=$SLOW_WORKERS"
echo "  poison=${POISON_DURATION}s flood=$FLOOD_REQUESTS recovery=${RECOVERY_DURATION}s"
echo "============================================"

if ! wait_ready 15; then
  echo "ERROR: mermaid service not responding through $GATEWAY_URL"
  exit 1
fi

calibrate
log "Restarting $MERMAID_NAME to clear calibration leftovers..."
docker restart "$MERMAID_NAME" > /dev/null
wait_ready 30 || { echo "ERROR: mermaid did not come back after restart"; exit 1; }

echo "baseline" > "$PHASE_FILE"
monitor &
MONITOR_PID=$!

log "=== Phase baseline: idle monitoring (15s) ==="
sleep 15

phase A "healthy concurrent load (${NORMAL_WORKERS} workers, ${PHASE_A_DURATION}s)"
rm -f "$STOP_FILE"
pids=()
for i in $(seq 1 "$NORMAL_WORKERS"); do normal_worker A & pids+=($!); done
sleep "$PHASE_A_DURATION"
touch "$STOP_FILE"
wait "${pids[@]}" 2>/dev/null

phase B "poison (${NORMAL_WORKERS} normal + ${SLOW_WORKERS} slow workers, ${POISON_DURATION}s)"
rm -f "$STOP_FILE"
pids=()
for i in $(seq 1 "$NORMAL_WORKERS"); do normal_worker B & pids+=($!); done
for i in $(seq 1 "$SLOW_WORKERS"); do slow_worker B & pids+=($!); done
sleep "$POISON_DURATION"
touch "$STOP_FILE"
wait "${pids[@]}" 2>/dev/null

phase C "flood probe ($FLOOD_REQUESTS fire-and-forget requests against wedged service)"
seq 1 "$FLOOD_REQUESTS" | xargs -P 50 -I{} curl -s -o /dev/null --max-time 2 \
  -w "$(date +%s) C flood %{http_code} %{time_total}\n" \
  "${GATEWAY_URL}${DIAGRAM_PATH}" >> "$REQ_LOG" 2>/dev/null || true

phase D "recovery: zero load for ${RECOVERY_DURATION}s"
sleep "$RECOVERY_DURATION"

phase E "cold-start race test"
race_test

touch "$END_FILE"
wait "$MONITOR_PID" 2>/dev/null

summary