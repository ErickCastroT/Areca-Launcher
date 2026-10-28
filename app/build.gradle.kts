import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Versión tipo Ubuntu: AA.MM.N. AA.MM es el año y mes de la compilación y N cuenta las compilaciones
// del mes (vuelve a 1 al cambiar de mes). El contador vive en version.properties y sube solo cuando
// se compila o instala (assemble, install, bundle); sincronizar el proyecto no lo cambia.
val now = LocalDateTime.now()
val yearMonth = (now.year % 100) * 100 + now.monthValue
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply { if (versionFile.exists()) versionFile.reader().use { load(it) } }
val isBuildRun = gradle.startParameter.taskNames.any { Regex("assemble|install|bundle|package", RegexOption.IGNORE_CASE).containsMatchIn(it) }
if (isBuildRun) {
    val sameMonth = versionProps.getProperty("month")?.toIntOrNull() == yearMonth
    val next = if (sameMonth) (versionProps.getProperty("build")?.toIntOrNull() ?: 0) + 1 else 1
    versionProps.setProperty("month", yearMonth.toString())
    versionProps.setProperty("build", next.toString())
    // Se escribe a mano (sin la fecha que añade Properties.store) para que el archivo solo cambie cuando cambia el contador.
    versionFile.writeText("# Contador de compilaciones de Areca; lo actualiza app/build.gradle.kts\nmonth=$yearMonth\nbuild=$next\n")
}
val buildNumber = versionProps.getProperty("build")?.toIntOrNull() ?: 0

android {
    namespace = "com.areca.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.areca.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = yearMonth * 1000 + buildNumber
        versionName = "%02d.%02d.%d".format(yearMonth / 100, yearMonth % 100, buildNumber)
        buildConfigField("String", "CODENAME", "\"Arboreal Anole\"")
        buildConfigField("String", "BUILD_DATE", "\"${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Firmado con la clave debug solo para probar el rendimiento en dispositivo.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    // Instala el perfil de línea base (propio y el de las bibliotecas de Compose) al primer arranque, también fuera de Play Store.
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
}
