#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
SIGNING_KEYSTORE="${SIGNING_KEYSTORE:?Set SIGNING_KEYSTORE to a private PKCS12 keystore outside this repository}"
SIGNING_PASSWORD_FILE="${SIGNING_PASSWORD_FILE:?Set SIGNING_PASSWORD_FILE to a private password file outside this repository}"
SIGNING_ALIAS="${SIGNING_ALIAS:-watch-timer-bridge}"
test -f "$ANDROID_JAR" && test -f "$SIGNING_KEYSTORE" && test -f "$SIGNING_PASSWORD_FILE"
mkdir -p build/classes

java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 \
  -cp "$ANDROID_JAR" -d build/classes $(find src -name '*.java' -print)
dalvik-exchange --dex --min-sdk-version=28 --output=build/classes.dex build/classes
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" \
  --min-sdk-version 28 --target-sdk-version 35 -F build/unsigned.apk
(cd build && zip -q -j unsigned.apk classes.dex)
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" \
  --ks-pass "file:$SIGNING_PASSWORD_FILE" \
  --out build/watch-timer-bridge-v1.0.6.apk build/aligned.apk
apksigner verify --verbose --print-certs build/watch-timer-bridge-v1.0.6.apk
