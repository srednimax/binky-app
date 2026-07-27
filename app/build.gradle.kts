import java.util.Properties

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

// Signing coordinates come from local.properties, which is gitignored; the keystore itself lives
// outside the repo entirely so it cannot be committed by accident (ADR-0009). `Properties` is
// Java's old key=value map, and `use {}` is Kotlin's try-with-resources — it closes the stream
// however the block exits, success or throw. Roughly `try/finally` around a file handle.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

val uploadKeyProperties =
    listOf(
        "binky.upload.storeFile",
        "binky.upload.storePassword",
        "binky.upload.keyAlias",
        "binky.upload.keyPassword",
    )

// `?.isNotBlank() == true` is the null-safe idiom: a missing property gives null, and null == true
// is false, so absent and empty both count as "no key" without a separate null check.
val hasUploadKey = uploadKeyProperties.all { localProperties.getProperty(it)?.isNotBlank() == true }

// A release build with no key must fail loudly rather than quietly emitting an unsigned artifact
// that only Play rejects, half an hour later. Inspecting the requested task names is deliberately
// blunt: this is a single-module build, so anything with "Release" in it is ours, and the check
// runs at configuration time — before Gradle does any work at all.
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (buildingRelease && !hasUploadKey) {
    error(
        "Release build requested but the upload key is missing. Add to local.properties:\n" +
            uploadKeyProperties.joinToString("\n") { "  $it=..." } +
            "\nThe keystore belongs outside the repo and is never committed. See docs/PLAN.md 3a.",
    )
}

android {
    namespace = "app.binky.tracker"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.binky.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode
        // versionName is the human-facing semver string. Do NOT edit it by hand —
        // release-please bumps it from your Conventional Commits. The trailing
        // comment is the marker its "generic" updater looks for. See docs/RELEASING.md.
        versionName = "0.4.0" // x-release-please-version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Created only when the coordinates are present, so a fresh checkout with no key can still
        // run `assembleDebug` and the test suite. The guard above is what stops a *release* getting
        // through unsigned — this block staying empty is not itself the error.
        if (hasUploadKey) {
            create("release") {
                storeFile = file(localProperties.getProperty("binky.upload.storeFile"))
                storePassword = localProperties.getProperty("binky.upload.storePassword")
                keyAlias = localProperties.getProperty("binky.upload.keyAlias")
                keyPassword = localProperties.getProperty("binky.upload.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off deliberately, not by template default: 1.0 already differs from any
            // tested build in several ways, and a sixth divergence whose failures are release-only,
            // runtime and reflection-shaped is the opposite of what this checkpoint proves.
            // Revisit at 1.1, against a known-good 1.0. See docs/PLAN.md 3a.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Google holds the permanent *app signing* key; this is only the *upload* key proving
            // the artifact came from us, and an upload key can be reset (ADR-0009). Losing it is
            // an inconvenience, not the end of the listing.
            if (hasUploadKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = false
        // Needed for BuildConfig.DEBUG, which gates the sample-data action in Settings — a fixture
        // that writes through the repositories and must never reach a release build.
        buildConfig = true
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

    // The weight chart (ADR-0022). Accepted on behaviour, not on whether it compiles: it plots real
    // `recordedAt` on a numeric x-axis, which is the house rule a categorical/index axis would break.
    implementation(libs.vico.compose.m3)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
