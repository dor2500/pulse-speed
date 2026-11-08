#!/bin/bash
# Builds a signed APK locally and publishes it as a GitHub release: scripts/release.sh v1.0.0
set -euo pipefail

tag="$1"
key_dir="$HOME/.pulse-speed"
notes="docs/release-notes/$tag.md"

[ -f "$notes" ] || { echo "Missing $notes"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Commit your changes first"; exit 1; }

export PULSE_KEYSTORE_PATH="$key_dir/release.jks"
PULSE_KEYSTORE_PASSWORD="$(cat "$key_dir/release-password.txt")"
export PULSE_KEYSTORE_PASSWORD
export PULSE_KEY_ALIAS=pulse

./gradlew --no-daemon assembleRelease

apk="app/build/outputs/apk/release/Pulse-$tag.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"
gh release create "$tag" "$apk" --title "Pulse $tag" --notes-file "$notes"
