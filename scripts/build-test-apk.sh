#!/usr/bin/env bash
# Genera el APK de PRUEBA de Focus Launcher: build release (R8) firmado con la clave de DEPURACIÓN local.
#
#   - NO es un APK para tienda: la clave de depuración es pública y su contraseña es «android».
#   - No se versiona ninguna clave ni APK (dist/ está en .gitignore). Cada máquina usa su propia clave de
#     depuración, así que la firma —y por tanto el hash— difiere entre máquinas.
#   - Requisitos: JDK 17 (JAVA_HOME) y Android SDK (ANDROID_HOME) con build-tools instaladas.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?Define ANDROID_HOME (ruta del Android SDK)}"
VERSION="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | cut -d'"' -f2)"
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
KEYSTORE="${FOCUS_TEST_KEYSTORE:-$HOME/.android/debug.keystore}"
OUT="dist/focus-launcher-${VERSION}-test.apk"

echo "==> Compilando release y auditando el manifiesto combinado"
./gradlew :app:assembleRelease :app:verifyReleaseManifest

if [ ! -f "$KEYSTORE" ]; then
  echo "==> Creando la clave de depuración local en $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

echo "==> Alineando y firmando (clave de depuración: SOLO pruebas)"
mkdir -p dist
"$BUILD_TOOLS/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$OUT.aligned"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$OUT" "$OUT.aligned"
rm -f "$OUT.aligned" "$OUT.idsig"
"$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" | head -3

echo "==> Permisos declarados en el APK final:"
"$BUILD_TOOLS/aapt2" dump permissions "$OUT" | sed 's/^/    /'
shasum -a 256 "$OUT" | tee "$OUT.sha256"
echo "Listo: $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes)"
