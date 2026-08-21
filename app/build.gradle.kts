import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.licensee)
}

// Whether this invocation is producing a release artifact. Two configuration-time guards depend on
// it — the versionCode just below and the upload key further down — and both have to fire before
// Gradle does any work, so it is computed here rather than inside a task. Inspecting the requested
// task names is deliberately blunt: this is a single-module build, so anything with "Release" in it
// is ours.
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

// versionCode must strictly increase for every installable build. We derive it
// from the git commit count so it climbs on its own and is never hand-edited.
// (Unlike versionName below, this number is NOT semver — it only has to keep
// going up, and the Play Store / installer rejects a build whose code didn't.)
//
// When git history isn't available — a shallow CI checkout, a source archive — a debug build falls
// back to 1 and doesn't care. A release must not, because the fallback is indistinguishable from a
// genuine count of 1: a release built that way either collides with a code already uploaded, or
// burns a low code that the real history will later produce again. Both fail at upload, after the
// artifact is signed and the release notes are written.
//
// `isIgnoreExitValue` is load-bearing, not tidiness. Left at its default, a failing `git` makes
// Gradle's own value source throw, and the configuration cache reports that as a build problem it
// cannot serialise — which fails the build *after* the artifact has already been packaged and
// signed. Swallowing the exit code hands us an empty string instead, so `toInt()` throws inside
// `runCatching`, where the decision below is actually ours to make.
//
// `getOrElse` is `runCatching`'s catch clause — it receives the exception, so the release path can
// rethrow with a reason. `error()` returns Kotlin's `Nothing`, a subtype of every type, which is
// why throwing satisfies the `Int` this block otherwise has to produce.
val gitVersionCode: Int =
    runCatching {
        providers
            .exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .toInt()
    }.getOrElse { cause ->
        if (buildingRelease) {
            error(
                "Release build requested but the versionCode could not be derived from " +
                    "`git rev-list --count HEAD` ($cause).\n" +
                    "Falling back to 1 here would ship a version code that cannot climb. Build " +
                    "releases from a full clone — not a shallow checkout or a source archive.",
            )
        }
        1
    }

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
// that only Play rejects, half an hour later. Same reasoning as the versionCode guard above, and
// the same `buildingRelease` flag drives both.
if (buildingRelease && !hasUploadKey) {
    error(
        "Release build requested but the upload key is missing. Add to local.properties:\n" +
            uploadKeyProperties.joinToString("\n") { "  $it=..." } +
            "\nThe keystore belongs outside the repo and is never committed. See docs/PLAN.md 3a.",
    )
}

android {
    // namespace and applicationId are different things and here they deliberately disagree.
    // namespace is compile-time: it's the package R and BuildConfig are generated into, and it
    // matches the source tree under app/src/main/java. applicationId is install-time identity —
    // the string Play, the package manager and the Store URL know the app by, and it is permanent
    // once published. Nothing requires them to match; only convention usually makes them.
    //
    // They diverge because the Play Console entry was created with the package name it suggests
    // from the app title, and a Console package name cannot be changed afterwards. Bending the
    // build to Play was chosen over recreating the listing. See docs/PLAN.md 3h.
    namespace = "app.binky.tracker"
    compileSdk = 36
    defaultConfig {
        applicationId = "binky.bunny.and.rabbit.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode
        // versionName is the human-facing semver string. Do NOT edit it by hand —
        // release-please bumps it from your Conventional Commits. The trailing
        // comment is the marker its "generic" updater looks for. See docs/RELEASING.md.
        versionName = "1.7.0" // x-release-please-version
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
        debug {
            // The debug build is a *separate app* from the Play one (ADR-0023). Once 1.0 is
            // installed from Play, a locally-signed build sharing its applicationId can neither
            // sit beside it nor replace it — Android refuses on the signature mismatch — and the
            // only way through would be uninstalling the Play build, destroying the real bunny
            // history it holds. The suffix makes them two installs that ignore each other.
            //
            // FileProvider's authority is already `${applicationId}.fileprovider` in the manifest,
            // so it follows this automatically. The instrumentation package follows too, becoming
            // binky.bunny.and.rabbit.tracker.debug.test — see CLAUDE.md's Xiaomi fallback commands.
            applicationIdSuffix = ".debug"
        }

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

    // The 1.0 gate reads "lint: 0 errors, 0 warnings", and that has to mean *project code*
    // (docs/PLAN.md, Phase 3). Everything demoted here is a version-pinning decision already on the
    // record in CLAUDE.md: AGP 9.0.1 / Kotlin 2.3.20 / Compose BOM 2026.03.01 is the combination
    // this app is built and tested against, and compileSdk/targetSdk stay at 36 for the same
    // reason. Lint is right that newer ones exist; taking them is a deliberate bump with a build to
    // prove it, not a release-eve tidy-up.
    //
    // `informational` rather than `disable`: they stay in the report, so the next bump is still one
    // `./gradlew lint` away — they just stop being counted against a gate that is about our code.
    lint {
        informational +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                "OldTargetApi",
            )
    }
}

kotlin {
    jvmToolchain(21)
}

// TranslationTest and AppLanguageTest read `res/` off disk as plain files, because an XML resource
// is not readable from a JVM unit test without Robolectric and the file itself is the artifact whose
// contents are in question. Gradle cannot see that: a `File("src/main/res/…")` opened inside a test
// body is invisible to up-to-date checking, so **editing a translation and re-running `test` used to
// report the previous run's verdict** — `:app:test UP-TO-DATE`, green, having checked nothing. Found
// in Phase 8, and it is the worst shape of failure a gate can take: a check that passes because it
// did not run.
//
// Declaring the directory as an input is the whole fix. Registering it on the task type covers both
// variants' test tasks without naming either.
//
// `translations/` is the same fix for the same reason, one directory up: Phase 8 stages a language's
// draft outside `res/` so that its existence does not ship it, and TranslationTest holds a staged
// draft to exactly the rules it holds a shipped one to. A draft Gradle cannot see is a draft nobody
// checks.
tasks.withType<Test>().configureEach {
    inputs
        .dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("resourcesReadByUnitTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // It is registered as a **file tree** rather than as a directory, because `translations/` is
    // legitimately absent whenever nothing is staged — which is the normal state, and became the
    // state the moment Phase 8 promoted its last draft. A missing directory is a hard validation
    // failure for `inputs.dir()`, and `.optional()` does **not** rescue it: that makes the *property*
    // optional, not the *path* absent. A `fileTree` over a missing directory is simply empty, so it
    // validates whether or not anything is staged, and still sees every file once something is.
    //
    // ⚠️ This fails only on a fresh clone, which is why it reached CI green from here. Git does not
    // track an empty directory, so promotion deletes the drafts and leaves `translations/` behind on
    // the machine that did it — `./gradlew test` passes locally and every CI job dies at task
    // configuration. `rmdir translations` before trusting a local run.
    inputs
        .files(fileTree(rootProject.layout.projectDirectory.dir("translations")))
        .withPropertyName("stagedTranslationsReadByUnitTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Room exports the compiled schema as JSON. ADR-0007 lets us wipe the database until Phase 3,
// but these files are what makes Phase 3's first real migration reviewable — so they are
// generated here and committed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// Attribution, generated rather than remembered (Phase 7.5 §3).
//
// The obligation is over the **resolved runtime classpath**, not the 13 entries in
// `libs.versions.toml`: Compose alone pulls dozens of transitive artifacts and Apache-2.0 §4 travels
// with each. Licensee resolves that classpath, so the list cannot go stale behind a dependency bump.
//
// Every licence the app ships has to be named here, and that is the point rather than a chore: an
// artifact whose licence is not on this list **fails the build**, which is the failure a hand-typed
// list has no way to produce. When it fires, the fix is two things — allow the licence here, and put
// its text in `src/main/assets/licences/<spdx-id>.txt` so it still travels with the binary.
//
// Test dependencies are out of scope automatically: Licensee reads the runtime classpath, so junit,
// espresso and androidx.test never appear.
licensee {
    // 195 of the 201 artifacts, as expected — androidx, Kotlin, Coroutines, Coil, Vico, Room.
    allow("Apache-2.0")
    // Exactly one: `androidx.datastore:datastore-preferences-external-protobuf`, androidx's
    // repackaging of protobuf-javalite. Nobody knew it was here before this plugin was run, which is
    // the whole argument for generating the list.
    allow("BSD-3-Clause")
    // The two that are **not** open-source licences and cannot be bundled: they are Google's terms
    // of service, hosted by Google, and their text is not ours to ship. Their rows link out instead
    // — see `LicencesScreen`. `play-services-base`/`-basement`/`-tasks` come in behind ML Kit
    // (ADR-0009), which is one more thing that leaves with it if the scanner is ever dropped.
    allowUrl("https://developer.android.com/studio/terms.html") {
        because("Android Software Development Kit License — Google's own terms, not redistributable")
    }
    allowUrl("https://developers.google.com/ml-kit/terms") {
        because("ML Kit Terms of Service — the same, and it arrives with ADR-0009's scanner")
    }
}

/**
 * Licensee's report, copied into the variant's assets as the one file the app reads.
 *
 * A copy rather than pointing the asset source directory straight at the report folder, because that
 * folder also holds `validation.txt` — a build artifact with no business in an APK.
 *
 * Kotlin/Gradle note: `abstract class` with `@get:` annotated abstract properties is Gradle's own
 * lazy-property idiom — it generates the implementation, and the `Property` types are what let the
 * value be wired before the task ever runs (roughly a promise that Gradle resolves at execution).
 */
abstract class BundleLicences : DefaultTask() {
    @get:InputFile
    abstract val artifacts: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun bundle() {
        val destination = outputDirectory.get().asFile.resolve(ASSET_NAME)
        destination.parentFile.mkdirs()
        artifacts.get().asFile.copyTo(destination, overwrite = true)
    }

    companion object {
        const val ASSET_NAME = "licences.json"
    }
}

androidComponents {
    onVariants { variant ->
        // The exported schemas, shipped inside the *instrumented test* APK as assets.
        //
        // `MigrationTestHelper` builds a database at an old version by reading that version's JSON at
        // runtime, so `4.json` has to be readable on the device — it is not enough for it to exist in
        // the repository. This is the one line that turns a committed schema file into a testable one.
        variant.androidTest
            ?.sources
            ?.assets
            ?.addStaticSourceDirectory("$projectDir/schemas")

        // Per variant on purpose: the debug build genuinely ships `ui-tooling` and the release build
        // does not, so one shared list would be wrong for whichever variant it was not generated
        // from. The screen therefore names what *this* binary contains, which is what the obligation
        // is about.
        val suffix = variant.name.replaceFirstChar(Char::uppercase)
        val licenseeTask = "licenseeAndroid$suffix"
        val bundleTask =
            tasks.register<BundleLicences>("bundle${suffix}Licences") {
                artifacts.set(layout.buildDirectory.file("reports/licensee/android$suffix/artifacts.json"))
                // The report path is Licensee's own convention rather than something it exposes, so
                // the dependency is declared by name. Wrong here and the build fails on a missing
                // input file — loudly, not silently with a stale list.
                dependsOn(licenseeTask)
            }
        variant.sources.assets?.addGeneratedSourceDirectory(bundleTask, BundleLicences::outputDirectory)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    //
    // AppCompat is here for exactly one thing: the per-app language backport below Android 13
    // (ADR-0013). None of its widgets are used — Compose M3 draws every pixel — but the backport is
    // applied through AppCompatDelegate, which only exists inside an AppCompatActivity, which in
    // turn only starts under an AppCompat-descended theme. That chain is why this costs a
    // dependency, the activity's base class and the root theme rather than a Settings row.
    implementation(libs.androidx.appcompat)
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
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(libs.androidx.datastore.preferences)

    // The export manifest inside a backup zip (ADR-0005). JSON rather than a hand-rolled format
    // because the manifest is what a restore's promise is sourced from — a parser written here
    // would be one more thing between an owner's archive and their bunny's history. Enums
    // serialise by *name*, which is the same rule the database's converters follow.
    implementation(libs.kotlinx.serialization.json)

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

    // Scheduling. One worker for the whole app (ADR-0024), initialised on demand rather than by
    // androidx.startup — see BinkyApplication, where ADR-0007's wipe guard also lives and the
    // ordering between the two has to be a decision rather than a merged-manifest accident.
    implementation(libs.androidx.work.runtime)

    // The guided document scanner (ADR-0009). **The one dependency in this file that is allowed to
    // be absent at runtime**: it is delivered by Google Play services, so a device without them
    // runs the plain-camera fallback instead — which is why nothing outside `scan/` names it, and
    // why dropping it is a one-line change in `AppContainer` rather than a rewrite.
    //
    // It is also the one whose *merged manifest* is checked rather than assumed. 4h's finding was
    // that a dependency can write permissions nobody declared; `scripts/aab-permissions.py` runs
    // against the artifact at 5g for exactly this, and its `uses-feature` section exists because a
    // merged `android.hardware.camera` at required="true" would filter the app off every device
    // without a camera on Play — a distribution change no permission list would show.
    implementation(libs.mlkit.document.scanner)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
