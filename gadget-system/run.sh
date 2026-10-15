#!/usr/bin/env bash
# Bertzkie Gadget Sales and Inventory System - Unix launcher
# Suppresses JDK 24+ native-access warning for SQLite

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

JAR="$SCRIPT_DIR/target/gadget-sales-inventory-1.0.0.jar"
if [ ! -f "$JAR" ]; then
  echo "[!] Jar not found at $JAR"
  echo "    Run: mvn clean package  (or use Windows: filelist build)"
  exit 1
fi

# Always pass --enable-native-access if JVM supports it (no-op on Java 17)
if java --help 2>&1 | grep -q "enable-native-access"; then
  exec java --enable-native-access=ALL-UNNAMED -jar "$JAR" "$@"
else
  exec java -jar "$JAR" "$@"
fi
