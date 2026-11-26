#!/usr/bin/env sh
# Builds the Connect IQ app. Both values are machine-local; neither is committed.
set -eu

: "${CONNECTIQ_HOME:?Set CONNECTIQ_HOME to the installed Connect IQ SDK}"
: "${CONNECTIQ_DEVELOPER_KEY:?Set CONNECTIQ_DEVELOPER_KEY to the .der key outside the repo}"

BLD="${CONNECTIQ_HOME}/bin/monkeyc"
if [ ! -x "$BLD" ]; then
    echo "monkeyc not found at ${BLD}" >&2
    exit 1
fi

mkdir -p bin
for device in vivoactive5 vivoactive4 vivoactive4s venu3 venu3s fr255 fr265 fr955 fr965 fenix7 fenix7s fenix7x; do
    echo "building ${device}"
    "$BLD" -f monkey.jungle -o "bin/openhealth-${device}.prg" -d "$device" \
        -y "$CONNECTIQ_DEVELOPER_KEY" --release
done
echo "done: bin/*.prg"
