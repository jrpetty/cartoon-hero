#!/usr/bin/env bash
#
# start.sh — Launch the ClaudeCraft Fabric server.
#
# Run ./setup.sh first. You must accept the Minecraft EULA before the server
# will start (see the message setup.sh prints, or edit eula.txt to eula=true).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Memory allocation — tweak for your machine.
MIN_RAM="${MIN_RAM:-1G}"
MAX_RAM="${MAX_RAM:-2G}"

if [[ ! -f fabric-server-launch.jar ]]; then
	echo "ERROR: fabric-server-launch.jar not found. Run ./setup.sh first." >&2
	exit 1
fi

if [[ ! -f eula.txt ]] || ! grep -q '^eula=true' eula.txt; then
	echo "You must accept the Minecraft EULA (https://aka.ms/MinecraftEULA)."
	echo "Run:  echo 'eula=true' > eula.txt"
	exit 1
fi

exec java -Xms"$MIN_RAM" -Xmx"$MAX_RAM" -jar fabric-server-launch.jar nogui
