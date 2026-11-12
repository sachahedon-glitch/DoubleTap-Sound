#!/bin/bash
# Prérequis : JDK 8+, Android SDK (build-tools + platforms/android-19 ou plus récent)
set -e
SDK=${ANDROID_HOME:?définis ANDROID_HOME}
BT=$(ls -d $SDK/build-tools/* | tail -1)
AJ=$(ls -d $SDK/platforms/android-* | sort -V | tail -1)/android.jar
rm -rf out && mkdir -p out/gen out/classes
$BT/aapt package -f -m -J out/gen -M AndroidManifest.xml -S res -I $AJ -F out/res.zip
javac -source 8 -target 8 -cp $AJ -d out/classes $(find src out/gen -name '*.java')
$BT/d8 --min-api 19 --lib $AJ --output out $(find out/classes -name '*.class')
cp out/res.zip out/app.apk && (cd out && zip -qj app.apk classes.dex)
[ -f debug.keystore ] || keytool -genkey -v -keystore debug.keystore -alias k -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=DoubleTap"
# v1 (JAR) uniquement pour Android 4.4.2
$BT/apksigner sign --v1-signing-enabled true --v2-signing-enabled false --ks debug.keystore --ks-pass pass:android --out DoubleTapSound.apk out/app.apk
echo "OK -> DoubleTapSound.apk"
