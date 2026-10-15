#!/usr/bin/env bash
# One-time setup: fetch the JDK this build needs.
#
# Fabric Loom 1.18.4 must run on a Java 25 JVM (it refuses anything older), while the
# mod itself is compiled against a Java 21 toolchain that Gradle provisions on demand.
# Many machines only ship Java 17/21, so this script downloads a Java 25 JDK and prints
# the JAVA_HOME to export. Nothing is installed system-wide.
#
#   ./scripts/setup-jdk.sh
#   export JAVA_HOME="$HOME/.local/logvex-jdk-25"
#   ./gradlew build
set -eu

VERSION="25"
DEST="${LOGVEX_JDK_DIR:-$HOME/.local/logvex-jdk-$VERSION}"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64|amd64) A=x64 ;;
  aarch64|arm64) A=aarch64 ;;
  *) echo "unsupported architecture: $ARCH" >&2; exit 1 ;;
esac

if [ -x "$DEST/bin/java" ]; then
  echo "JDK already present at $DEST"
else
  mkdir -p "$DEST"
  URL="https://api.adoptium.net/v3/binary/latest/$VERSION/ga/linux/$A/jdk/hotspot/normal/eclipse"
  echo "Downloading Temurin JDK $VERSION ($A) ..."
  curl -fsSL -o /tmp/logvex-jdk.tar.gz "$URL"
  tar xzf /tmp/logvex-jdk.tar.gz -C "$DEST" --strip-components=1
  rm -f /tmp/logvex-jdk.tar.gz
fi

echo
echo "JAVA_HOME=$DEST"
"$DEST/bin/java" -version
echo
echo "Now run:"
echo "  export JAVA_HOME=\"$DEST\""
echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
echo "  ./gradlew build"
