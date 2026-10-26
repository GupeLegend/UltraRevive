#!/usr/bin/env bash
# Compiles UltraRevive with plain javac (no Gradle). Output: build/UltraRevive-1.0.jar
# Built against the OLDEST supported Paper (26.1.2) so the jar runs on 26.1, 26.2 and 26.3.
# To check it also compiles against a newer API: PAPER_VERSION=26.3.build.214-stable ./compilar.sh
set -e
cd "$(dirname "$0")"

NAME="UltraRevive"
VERSION="1.0"
PAPER_VERSION="${PAPER_VERSION:-26.1.2.build.74-stable}"
JDK_MIN=25

# Paper 26 targets Java 25: an older javac fails with a confusing "cannot access Player" error.
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then JAVAC="$JAVA_HOME/bin/javac"; else JAVAC="javac"; fi
javac_version() { v=$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p'); echo "${v:-0}"; }
JAVAC_VERSION=$(javac_version)
if [ "$JAVAC_VERSION" -lt "$JDK_MIN" ]; then
    for candidate in "$HOME/jdk25"/*/bin/javac* /c/Users/*/jdk25/*/bin/javac*; do
        [ -x "$candidate" ] && { JAVAC="$candidate"; break; }
    done
    [ "$(javac_version)" -lt "$JDK_MIN" ] && { echo "ERROR: need JDK $JDK_MIN or newer."; exit 1; }
fi
echo "javac: $("$JAVAC" -version 2>&1)"

# Local to this project only (no shared _libs/ across plugins).
LIBS="libs"
mkdir -p "$LIBS"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac

PAPER_REPO="https://repo.papermc.io/repository/maven-public"
CENTRAL="https://repo1.maven.org/maven2"
fetch() { [ -f "$LIBS/$2" ] || { echo "Downloading $2..."; curl -sL "$1" -o "$LIBS/$2"; }; }

# All transitive dependencies of paper-api; Gradle resolves them automatically, but plain javac
# needs every jar listed on the classpath by hand.
rm -f "$LIBS/paper-api.jar"   # old name, before the API version was selectable
fetch "$PAPER_REPO/io/papermc/paper/paper-api/$PAPER_VERSION/paper-api-$PAPER_VERSION.jar" "paper-api-$PAPER_VERSION.jar"
fetch "$CENTRAL/net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar" "adventure-api.jar"
fetch "$CENTRAL/net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar" "adventure-key.jar"
fetch "$CENTRAL/net/kyori/adventure-text-serializer-legacy/4.26.1/adventure-text-serializer-legacy-4.26.1.jar" "adventure-legacy.jar"
fetch "$CENTRAL/net/kyori/adventure-text-minimessage/4.26.1/adventure-text-minimessage-4.26.1.jar" "adventure-minimessage.jar"
fetch "$CENTRAL/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar" "examination-api.jar"
fetch "$CENTRAL/net/kyori/examination-string/1.3.0/examination-string-1.3.0.jar" "examination-string.jar"
fetch "$PAPER_REPO/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar" "bungeecord-chat.jar"
fetch "$CENTRAL/net/luckperms/api/5.4/api-5.4.jar" "luckperms-api.jar"
# Floodgate (softdepend, menu for Bedrock players): the plugin jar ships its API (Floodgate + Cumulus)
fetch "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot" "floodgate-spigot.jar"
fetch "$CENTRAL/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" "gson.jar"
fetch "$CENTRAL/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar" "annotations.jar"
fetch "$CENTRAL/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar" "jspecify.jar"
fetch "$CENTRAL/com/google/guava/guava/33.7.1-jre/guava-33.7.1-jre.jar" "guava.jar"

CP=""
for jar in "$LIBS"/*.jar; do
    case "$jar" in */paper-api-*) [ "$jar" = "$LIBS/paper-api-$PAPER_VERSION.jar" ] || continue ;; esac
    CP="$CP${CP:+$SEP}$jar"
done

echo "Compiling $NAME $VERSION..."
rm -rf build/classes
mkdir -p build/classes
find src/main/java -name "*.java" > build/sources.txt
"$JAVAC" ${XLINT:--Xlint:none} -encoding UTF-8 -cp "$CP" -d build/classes @build/sources.txt
cp -r src/main/resources/* build/classes/
rm -f build/sources.txt

JAR_BIN="$(dirname "$JAVAC")/jar"
[ -x "$JAR_BIN" ] || [ -x "$JAR_BIN.exe" ] || JAR_BIN="jar"
( cd build/classes && "$JAR_BIN" cf "../$NAME-$VERSION.jar" . )
echo "DONE -> build/$NAME-$VERSION.jar"
