#!/bin/bash
# Publish this project to github.com/kejing-dev/SpatialChess (run after `gh auth login`).
set -euo pipefail
cd "$(dirname "$0")/.."
gh repo create kejing-dev/SpatialChess --private --source . --remote origin --push \
  --description "Spatial Chess · PICO Spatial SDK free-play chess for Shared Space (PICO Spatial UI, PICO Sans)"
gh release create v0.1.0 "deliverables/SpatialChess-debug.apk#SpatialChess-debug.apk (PICO OS 6 / Emulator 6.1)" \
  --title "v0.1.0 · 自由摆棋首版" --notes-file deliverables/release-notes.md
gh repo view --web >/dev/null 2>&1 || true
