# Fixture: 200 entradas launcher

Paquete `com.focuslauncher.fixture200` con **200 `activity-alias` launcher** (etiquetas con acentos).
Sirve para medir RNF-04 con 200 entradas. No es parte de la app ni de sus dependencias.

```bash
python3 generate_manifest.py                       # regenera el manifiesto (ya está generado)
../../gradlew -p . :assembleDebug                  # build/outputs/apk/debug/many-launchers-debug.apk
adb install -r build/outputs/apk/debug/many-launchers-debug.apk
```
