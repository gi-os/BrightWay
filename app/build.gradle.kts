plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gios.brightway"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gios.brightway"
        minSdk = 29
        targetSdk = 35
        // CI overwrites both from the workflow run number; see .github/workflows/build.yml
        versionCode = 1
        versionName = "1.12.0"

        buildConfigField("String", "REPORT_REPO", "\"gi-os/light-reports\"")

        // The LPIII is arm64 only.
        ndk { abiFilters += "arm64-v8a" }
    }

    // The release key used to sit in this repository with its password written three
    // lines under it, so anyone at all could build an APK that Android would accept as
    // an update to this one. It is a CI secret now: the workflow decodes it to
    // keystore/brightway.jks, and that path is gitignored so a local checkout cannot
    // commit it back.
    //
    // A build without the secret still compiles and still produces an APK. It is simply
    // not signed with the release key and will not install over one — which is the right
    // failure. A build that announces it is not the real thing beats one that quietly
    // is not.
    val keystoreFile = rootProject.file("keystore/brightway.jks")
    val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD") ?: ""
    val canSignRelease = keystoreFile.exists() && keystorePassword.isNotEmpty()

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "brightway"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Shake-to-report, the wheel, the shared type/greys — and from 1.7.0 the colour hold:
    // ColourEffect() asks BrightControl for colour over a bound service, so a crash releases
    // the hold when the binder dies instead of leaving the whole phone in colour.
    implementation("com.gios:light-common:1.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Google Routes / Places / Geocoding — plain REST, no Play Services on LightOS.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // QR scanning (API key entry)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Geo math is pure Kotlin with no Android imports, so it runs here.
    testImplementation("junit:junit:4.13.2")
    // org.json ships inside Android; the unit-test environment stubs it (every method
    // throws "not mocked"). Pull the real artifact so DuplicateStepTest can exercise
    // parseRoute on the JVM.
    testImplementation("org.json:json:20240303")
}
