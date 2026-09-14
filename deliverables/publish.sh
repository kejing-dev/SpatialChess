#!/bin/bash
# Publish a release: ./deliverables/publish.sh v0.2.2  (after `gh auth login`)
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="${1:?usage: publish.sh vX.Y.Z}"
cp app/build/outputs/apk/debug/app-debug.apk "deliverables/SpatialChess-$VERSION.apk"
gh release create "$VERSION" "deliverables/SpatialChess-$VERSION.apk" --title "$VERSION" --notes-file deliverables/release-notes.md
