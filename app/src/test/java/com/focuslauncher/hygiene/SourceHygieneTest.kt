package com.focuslauncher.hygiene

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privacidad del código: ningún registro (un log podría filtrar PIN, verificadores o inventario de
 * apps) y ningún estado guardado/restaurable (Bundle, SavedStateHandle, rememberSaveable) que pudiera
 * persistir un PIN, una autorización o la lista administrativa. Se comprueba el código sin comentarios.
 */
class SourceHygieneTest {
    private val main = File("src/main/java")

    private fun codeWithoutComments(f: File): String =
        f.readText().replace(Regex("/\\*[\\s\\S]*?\\*/"), "").lines().joinToString("\n") { it.substringBefore("//") }

    private fun sources() = main.walkTopDown().filter { it.extension == "kt" }.toList()

    private fun offenders(vararg banned: String): List<String> = sources().flatMap { f ->
        val code = codeWithoutComments(f)
        banned.filter { code.contains(it) }.map { "${f.name}: $it" }
    }

    @Test fun thereIsNoLoggingAtAll() {
        val found = offenders("android.util.Log", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "Log.v(", "println(", "printStackTrace", "Timber")
        assertTrue("registros en código principal: $found", found.isEmpty())
    }

    @Test fun nothingSecretCanBeSavedOrRestored() {
        val found = offenders("rememberSaveable", "SavedStateHandle", "onSaveInstanceState", "Parcelable", "Serializable", "putExtra", "getStringExtra")
        assertTrue("estado guardado/restaurable: $found", found.isEmpty())
    }

    @Test fun failuresCarryOnlyTheirKindNeverData() {
        val failure = codeWithoutComments(File("src/main/java/com/focuslauncher/data/local/StateFailure.kt"))
        assertTrue(failure.contains("Exception(kind.name, cause)"))
        val all = sources().joinToString("\n") { codeWithoutComments(it) }
        assertEquals("interpolación de secretos en cadenas", 0, Regex("\\$\\{?(pin|verifier|salt|secret)\\b").findAll(all).count())
    }

    @Test fun theManifestDeclaresOnlyTheActivityAndNoBackup() {
        // Sin comentarios y sin las directivas tools:node="remove" (que ELIMINAN componentes de dependencias).
        val manifest = File("src/main/AndroidManifest.xml").readText()
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("<(receiver|service|provider)[^>]*tools:node=\"remove\"[^>]*/>"), "")
        assertEquals(0, Regex("<(service|receiver|provider)").findAll(manifest).count())
        assertEquals(1, Regex("<activity\\b").findAll(manifest).count())
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
