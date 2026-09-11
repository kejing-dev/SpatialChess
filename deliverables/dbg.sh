#!/bin/bash
# usage: dbg.sh <cmd> [arg] — tagged broadcast; waits up to 8 s for the app to log this exact tag (no blind retries)
TAG="t$(date +%s%N)"
ARGS=(-a com.example.spatialchess.DEBUG --es cmd "$1" --es tag "$TAG")
if [ -n "${2:-}" ]; then ARGS+=(--es arg "'$2'"); fi
T0=$(date +%s.%N)
adb -s emulator-5554 shell am broadcast "${ARGS[@]}" > /dev/null 2>&1
for j in $(seq 1 40); do
  sleep 0.2
  if adb -s emulator-5554 logcat -d -s SpatialChess.Dbg 2>/dev/null | grep -q "tag=$TAG"; then
    printf "ok %s %s (%.1fs)\n" "$1" "${2:-}" "$(echo "$(date +%s.%N) - $T0" | bc)"; exit 0; fi
done
echo "LOST $1 ${2:-}"; exit 1
