#!/usr/bin/env bash
# Builds the hand-in files in projekt/hand-in from an explicit list only.
set -euo pipefail
cd "$(dirname "$0")"
OUT=hand-in
NAME=VIS_ZAM0074_C06_IS
ART=docs/artefacts
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/$NAME/config" "$STAGE/$NAME/db" "$OUT"
cp -r app/src app/pom.xml "$STAGE/$NAME/"
cp app/config/db.properties.example "$STAGE/$NAME/config/"
cp ../../TDS/db/01_schema.sql ../../TDS/db/02_seed.sql "$STAGE/$NAME/db/"

# the archive must hold neither credentials nor build output nor documents
if find "$STAGE" \( -name db.properties -o -name target -o -name documents \) | grep -q .; then
  echo "ERROR: forbidden file in the archive" >&2; exit 1
fi

rm -f "$OUT/$NAME.zip"
(cd "$STAGE" && zip -qr "$OLDPWD/$OUT/$NAME.zip" "$NAME")
cp "$ART/main.pdf" "$OUT/VIS_ZAM0074_C06_A1-A7.pdf"
for n in 5 6 7; do cp "$ART/VIS_ZAM0074_C06_A$n.pdf" "$OUT/"; done

unzip -l "$OUT/$NAME.zip"
ls -l "$OUT"
