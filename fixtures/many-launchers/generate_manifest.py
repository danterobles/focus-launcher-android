#!/usr/bin/env python3
"""Genera src/main/AndroidManifest.xml con 200 activity-alias launcher (etiquetas con acentos y mayúsculas)."""
import itertools

NAMES = ["Álgebra", "Biblioteca", "Cámara", "Diario", "Éxito", "Finanzas", "Galería", "Hábitos", "Ícono",
         "Jardín", "Karaoke", "Lección", "Música", "Noticias", "Órbita", "Podcast", "Química", "Recetas", "Salud", "Tareas"]
SUFFIX = ["Pro", "Lite", "Plus", "Mini", "Max", "Go", "Hoy", "Familia", "Viaje", "Casa"]
labels = [f"{n} {s}" for n, s in itertools.product(NAMES, SUFFIX)]
assert len(labels) == 200

out = ['<?xml version="1.0" encoding="utf-8"?>',
       '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
       '    <application android:label="Fixture 200" android:allowBackup="false">',
       '        <activity android:name=".Stub" android:exported="false" />']
for i, label in enumerate(labels, 1):
    out += [f'        <activity-alias android:name=".A{i:03d}" android:targetActivity=".Stub" android:exported="true" android:label="{label}">',
            '            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>',
            '        </activity-alias>']
out += ['    </application>', '</manifest>']
open("src/main/AndroidManifest.xml", "w", encoding="utf-8").write("\n".join(out) + "\n")
print("200 alias generados")
