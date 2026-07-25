plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// versionCode must strictly increase for every installable build. We derive it
// from the git commit count so it climbs on its own and is never hand-edited.
// (Unlike versionName below, this number is NOT semver — it only has to keep
// going up, and the Play Store / installer rejects a build whose code didn't.)
// runCatching falls back to 1 when git history isn't available — a shallow CI
// checkout or a source archive — which only affects debug builds that don't care.
val gitVersionCode: Int =
    runCatching {
        providers
            .exec {
                commandLine("git", "rev-list", "--count", "HEAD")
            }.standardOutput.asText
            .get()
            .trim()
            .toInt()
    }.getOrDefault(1)

android {
    namespace = "app.bunny.tracker"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.bunny.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode
        // versionName is the human-facing semver string. Do NOT edit it by hand —
        // release-please bumps it from your Conventional Commits. The trailing
        // comment is the marker its "generic" updater looks for. See docs/RELEASING.md.
        versionName = "0.1.0" // x-release-please-version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Room exports the compiled schema as JSON. ADR-0007 lets us wipe the database until Phase 3,
// but these files are what makes Phase 3's first real migration reviewable — so they are
// generated here and committed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Media: reading the camera's orientation tag so it can be baked into the pixels (ADR-0020).
    // The androidx one, not android.media.ExifInterface — it reads from an InputStream, which is
    // what a content:// Uri from the photo picker gives us.
    implementation(libs.androidx.exifinterface)
    androidTestImplementation(libs.androidx.exifinterface)

    // Avatars on screen. Coil renders a missing file as its `error` painter rather than throwing,
    // which is the house rule's "missing media is a placeholder, never a crash" for free.
    implementation(libs.coil.compose)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
