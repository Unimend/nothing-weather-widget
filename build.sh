#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
UNITY_ANDROID="/Applications/Unity/2022.3.52f1c1/PlaybackEngines/AndroidPlayer"
SDK_ROOT=${ANDROID_SDK_ROOT:-"$UNITY_ANDROID/SDK"}
JDK_ROOT=${JAVA_HOME:-"$UNITY_ANDROID/OpenJDK"}
BT="$SDK_ROOT/build-tools/34.0.0"
PLATFORM="$SDK_ROOT/platforms/android-34/android.jar"
OUT="$PROJECT_DIR/build"
mkdir -p "$OUT"
RUN=$(mktemp -d "$OUT/run.XXXXXX")
mkdir -p "$RUN/gen" "$RUN/classes" "$RUN/dex"
export JAVA_HOME="$JDK_ROOT"
export PATH="$JDK_ROOT/bin:$PATH"
"$BT/aapt2" compile --dir "$PROJECT_DIR/res" -o "$RUN/res.zip"
"$BT/aapt2" link -o "$RUN/base.apk" -I "$PLATFORM" --manifest "$PROJECT_DIR/AndroidManifest.xml" -R "$RUN/res.zip" --java "$RUN/gen" -A "$PROJECT_DIR/assets" --auto-add-overlay --min-sdk-version 24 --target-sdk-version 31 --version-code 1 --version-name 1.0.0
find "$PROJECT_DIR/src" "$RUN/gen" -name '*.java' -print0 | xargs -0 "$JDK_ROOT/bin/javac" -source 8 -target 8 -encoding UTF-8 -classpath "$PLATFORM" -d "$RUN/classes"
find "$RUN/classes" -name '*.class' -print0 | xargs -0 "$BT/d8" --release --min-api 24 --lib "$PLATFORM" --output "$RUN/dex"
cp "$RUN/base.apk" "$RUN/unsigned.apk"
(cd "$RUN/dex" && "$JDK_ROOT/bin/jar" -uf "$RUN/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$RUN/unsigned.apk" "$RUN/aligned.apk"
if [ ! -f "$OUT/debug.keystore" ]; then
    "$JDK_ROOT/bin/keytool" -genkeypair -keystore "$OUT/debug.keystore" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "$OUT/weather-widget.apk" "$RUN/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/weather-widget.apk"
echo "BUILD OK: $OUT/weather-widget.apk"
