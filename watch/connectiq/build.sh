#!/usr/bin/env sh
# Builds the Connect IQ app. Both values are machine-local; neither is committed.
set -eu

: "${CONNECTIQ_HOME:?Set CONNECTIQ_HOME to the installed Connect IQ SDK}"
: "${CONNECTIQ_DEVELOPER_KEY:?Set CONNECTIQ_DEVELOPER_KEY to the .der key outside the repo}"

cd "$(dirname "$0")"

BLD="${CONNECTIQ_HOME}/bin/monkeyc"
if [ ! -x "$BLD" ]; then
    echo "monkeyc not found at ${BLD}" >&2
    exit 1
fi

# The targets are read from manifest.xml, never kept in a second list here: a device that
# is in one and not the other is the drift `ConnectIqCompatibilityTest` exists to stop.
DEVICES=$(sed -n 's/.*<iq:product[[:space:]][[:space:]]*id="\([^"]*\)".*/\1/p' manifest.xml)
if [ -z "$DEVICES" ]; then
    echo "no <iq:product> entries in manifest.xml" >&2
    exit 1
fi

mkdir -p bin
for device in $DEVICES; do
    echo "building ${device}"
    "$BLD" -f monkey.jungle -o "bin/openhealth-${device}.prg" -d "$device" \
        -y "$CONNECTIQ_DEVELOPER_KEY" --release
done
echo "done: bin/*.prg"
