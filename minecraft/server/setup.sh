#!/usr/bin/env bash
#
# setup.sh — Provision a Minecraft server that runs ONLY mods made in Claude.
#
# It will:
#   1. Build the ClaudeCraft mod from source (../mods/claudecraft).
#   2. Download the Fabric server launcher for the target Minecraft version.
#   3. Download Fabric API (the one runtime dependency the mod needs).
#   4. Install the built mod + Fabric API into ./mods.
#   5. Write a sensible server.properties.
#
# Run this on a machine WITH internet access (it reaches Mojang + Fabric).
#
# Usage:  ./setup.sh
set -euo pipefail

# ---- Versions (keep in sync with mods/claudecraft/gradle.properties) ----
MINECRAFT_VERSION="1.21.1"
FABRIC_LOADER_VERSION="0.16.5"
FABRIC_INSTALLER_VERSION="1.0.1"
FABRIC_API_VERSION="0.105.0+1.21.1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$SCRIPT_DIR/../mods/claudecraft"
SERVER_DIR="$SCRIPT_DIR"
MODS_TARGET="$SERVER_DIR/mods"

echo "==> ClaudeCraft server setup"
echo "    Minecraft   : $MINECRAFT_VERSION"
echo "    Fabric loader: $FABRIC_LOADER_VERSION"
echo "    Fabric API  : $FABRIC_API_VERSION"
echo

# ---- 1. Build the mod ----
echo "==> Building ClaudeCraft mod..."
(cd "$MOD_DIR" && ./gradlew --no-daemon build)

BUILT_JAR="$(find "$MOD_DIR/build/libs" -maxdepth 1 -name 'claudecraft-*.jar' \
	! -name '*-sources.jar' | head -n1)"
if [[ -z "$BUILT_JAR" ]]; then
	echo "ERROR: could not find built mod jar in $MOD_DIR/build/libs" >&2
	exit 1
fi
echo "    Built: $BUILT_JAR"

# ---- 2. Download Fabric server launcher ----
mkdir -p "$MODS_TARGET"
FABRIC_LAUNCHER="$SERVER_DIR/fabric-server-launch.jar"
FABRIC_URL="https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${FABRIC_LOADER_VERSION}/${FABRIC_INSTALLER_VERSION}/server/jar"
echo "==> Downloading Fabric server launcher..."
curl -fL --retry 4 -o "$FABRIC_LAUNCHER" "$FABRIC_URL"

# ---- 3. Download Fabric API ----
FABRIC_API_JAR="$MODS_TARGET/fabric-api-${FABRIC_API_VERSION}.jar"
FABRIC_API_URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar"
echo "==> Downloading Fabric API..."
curl -fL --retry 4 -o "$FABRIC_API_JAR" "$FABRIC_API_URL"

# ---- 4. Install the built mod ----
echo "==> Installing ClaudeCraft into mods/..."
rm -f "$MODS_TARGET"/claudecraft-*.jar
cp "$BUILT_JAR" "$MODS_TARGET/"

# ---- 5. server.properties (created once; not overwritten) ----
if [[ ! -f "$SERVER_DIR/server.properties" ]]; then
	echo "==> Writing default server.properties..."
	cat > "$SERVER_DIR/server.properties" <<'PROPS'
motd=A Minecraft server running only mods made in Claude
level-name=world
gamemode=survival
difficulty=normal
max-players=10
online-mode=true
view-distance=10
spawn-protection=0
enable-command-block=true
PROPS
fi

echo
echo "==> Done. Installed mods:"
ls -1 "$MODS_TARGET"
echo
echo "Next steps:"
echo "  1. Accept the Minecraft EULA:  echo 'eula=true' > '$SERVER_DIR/eula.txt'"
echo "  2. Start the server:           ./start.sh"
