plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.focuslauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.focuslauncher"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Con -PperfBuild=true (mediciones sobre código de release) se usa el runner estándar: arranca la
        // Application real de producción en lugar de la de Hilt para pruebas.
        testInstrumentationRunner =
            if (providers.gradleProperty("perfBuild").isPresent) "androidx.test.runner.AndroidJUnitRunner"
            else "com.focuslauncher.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // SOLO para medir RNF-04 (búsqueda y cambio de visibilidad): como release (no depurable, sin
        // sobrecoste de depuración) pero SIN R8 —las pruebas instrumentadas necesitan clases que R8 elimina de
        // la app— y firmado con la clave de depuración local. El arranque frío se mide aparte sobre el APK
        // release real (minificado). Se activa con -PperfBuild=true; no se distribuye.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // Con -PperfBuild=true las pruebas instrumentadas corren contra el build "benchmark" (código de release).
    testBuildType = if (providers.gradleProperty("perfBuild").isPresent) "benchmark" else "debug"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") { option("lite") }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "benchmarkImplementation"(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.accessibility)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// Verifica el manifiesto COMBINADO del build release (las dependencias pueden añadir permisos y componentes).
// Falla si aparece un permiso fuera de la lista, un componente exportado que no sea la Activity de Focus, un
// receiver/service/provider sin exported=false explícito, `debuggable`, copias de seguridad o consultas amplias.
tasks.register("verifyReleaseManifest") {
    group = "verification"
    description = "Audita el manifiesto combinado del build release (permisos, exports, backups, queries)."
    dependsOn("processReleaseMainManifest")
    doLast {
        val merged = layout.buildDirectory.file("intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml").get().asFile
        require(merged.exists()) { "No existe el manifiesto combinado: ${merged.path}" }
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(merged)
        val ns = "http://schemas.android.com/apk/res/android"
        fun org.w3c.dom.Element.attr(n: String) = getAttributeNS(ns, n)
        fun elements(tag: String) = (0 until doc.getElementsByTagName(tag).length).map { doc.getElementsByTagName(tag).item(it) as org.w3c.dom.Element }
        val problems = mutableListOf<String>()

        val allowedPermissions = setOf("com.focuslauncher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        elements("uses-permission").map { it.attr("name") }.filter { it !in allowedPermissions }
            .forEach { problems += "permiso no permitido: $it" }

        val app = elements("application").single()
        if (app.attr("allowBackup") != "false") problems += "allowBackup debe ser false"
        if (app.attr("dataExtractionRules").isEmpty()) problems += "falta dataExtractionRules"
        if (app.attr("debuggable") == "true") problems += "debuggable=true en release"
        if (app.attr("usesCleartextTraffic") == "true") problems += "usesCleartextTraffic=true"

        elements("activity").filter { it.attr("name") != ".MainActivity" && it.attr("name") != "com.focuslauncher.MainActivity" }
            .forEach { problems += "actividad inesperada: ${it.attr("name")}" }
        elements("receiver").forEach { problems += "receiver declarado (no debe haber ninguno): ${it.attr("name")} exported=${it.attr("exported")}" }
        elements("service").forEach { problems += "service declarado: ${it.attr("name")}" }
        elements("provider").filter { it.attr("exported") != "false" }.forEach { problems += "provider sin exported=false: ${it.attr("name")}" }
        elements("activity").filter { it.attr("exported") == "true" }.map { it.attr("name") }.let {
            if (it.size != 1) problems += "debe haber exactamente 1 actividad exportada (la de Focus), hay: $it"
        }

        // Consultas acotadas: solo el intent MAIN/LAUNCHER, sin <package> ni provider.
        elements("queries").forEach { q ->
            val kids = (0 until q.childNodes.length).map { q.childNodes.item(it) }.filterIsInstance<org.w3c.dom.Element>()
            kids.filter { it.tagName != "intent" }.forEach { problems += "queries amplio: <${it.tagName}>" }
        }
        if (problems.isNotEmpty()) throw GradleException("Manifiesto combinado inválido:\n - " + problems.joinToString("\n - "))
        logger.lifecycle("verifyReleaseManifest: OK (${elements("uses-permission").size} permiso(s), sin receivers/services, 1 actividad exportada)")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy("verifyReleaseManifest") }
