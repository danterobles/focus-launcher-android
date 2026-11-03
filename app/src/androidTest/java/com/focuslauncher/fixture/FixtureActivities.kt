package com.focuslauncher.fixture

import android.app.Activity

/** Actividades de prueba. Corren en su propio proceso, así que las pruebas las observan vía dumpsys. */
class FixtureActivityA : Activity()
class FixtureActivityB : Activity()
class FixtureNoLauncherActivity : Activity()
