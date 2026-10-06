import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Release signing. Locally: a git-ignored `keystore.properties` at the repo root
 * (storeFile, storePassword, keyAlias, keyPassword). In CI: the BUTLER_KEYSTORE_PATH,
 * BUTLER_KEYSTORE_PASSWORD, BUTLER_KEY_ALIAS and BUTLER_KEY_PASSWORD environment
 * variables. With neither, `release` builds unsigned, which is what F-Droid wants: it
 * signs with its own key.
 */
val keystoreProps = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
fun signing(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: providers.environmentVariable(env).orNull

/*
 * The debug-only session mirror (FileDevSessionSink) writes the login token to a plain
 * file for scripts/dev-token.sh. Off unless `butler.devMirror=true` is in this machine's
 * git-ignored local.properties, so a debug APK built anywhere else, CI included, never
 * writes it.
 */
val devMirror = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(it.reader()) }
}.getProperty("butler.devMirror") == "true"

android {
    namespace = "com.cherry.butler"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cherry.butler"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.3"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        signing("storeFile", "BUTLER_KEYSTORE_PATH")?.let { store ->
            create("release") {
                storeFile = file(store)
                storePassword = signing("storePassword", "BUTLER_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "BUTLER_KEY_ALIAS")
                keyPassword = signing("keyPassword", "BUTLER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("boolean", "DEV_MIRROR", devMirror.toString())
            // CI signs the published debug APK with the release key, so each one installs
            // over the last. A local debug build keeps the machine's own debug key.
            if (providers.environmentVariable("BUTLER_SIGN_DEBUG").orNull == "true") {
                signingConfigs.findByName("release")?.let { signingConfig = it }
            }
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // What a user's phone actually runs — R8, shrunk resources, the AndroidX baseline
        // profiles, no debug checks — but signed with the debug key and sharing the debug
        // package, so it installs over the debug build and keeps the signed-in session.
        // Measure scroll and startup here, never on `debug`.
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    sourceSets {
        // perf is a release build in all but signing: same no-op dev session sink.
        getByName("perf") { java.srcDirs("src/release/java") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room schemas are checked in so migrations are reviewable in diffs.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    // F-Droid asks for this off: by default AGP puts a dependency list in the APK,
    // encrypted with Google's key, which nobody else can read or verify.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    // Installs the baseline profiles bundled by Compose & co. on first launch, so sideloaded
    // non-debug builds are AOT-compiled where it matters instead of waiting for Play.
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking + serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Supabase (auth today; postgrest/realtime land with browse + chat)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    // Offline mirror + paged lists
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)

    // Encrypted session storage
    implementation(libs.androidx.security.crypto)

    // Custom Tabs for the OAuth flow
    implementation(libs.androidx.browser)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Compile with the JDK running Gradle (21 here) while emitting JVM 17 bytecode,
// so no separate JDK 17 toolchain install is required.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
