plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.protobuf") }
android { namespace = "com.zeus.remote"; compileSdk = 35
    defaultConfig {
        applicationId = "com.zeus.remote"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.0"
        testInstrumentationRunner = "com.zeus.remote.RemoteSmokeInstrumentation"
        manifestPlaceholders["appLabel"] = "Zeus Remote"
    }
    buildTypes {
        getByName("debug") {
            // Optional side-by-side USB testing without replacing a differently signed installed APK.
            if (providers.gradleProperty("localTestInstall").orNull == "true") {
                applicationIdSuffix = ".localtest"
                versionNameSuffix = "-localtest"
                manifestPlaceholders["appLabel"] = "Zeus Remote"
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.15.0"); implementation("androidx.activity:activity-compose:1.9.3"); implementation("androidx.compose.ui:ui:1.7.6"); implementation("androidx.compose.material3:material3:1.3.1"); implementation("androidx.compose.ui:ui-tooling-preview:1.7.6"); debugImplementation("androidx.compose.ui:ui-tooling:1.7.6"); implementation("com.squareup.okhttp3:okhttp:4.12.0") }

dependencies {
    implementation(project(":shared"))
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    generateProtoTasks {
        all().configureEach {
            builtins { maybeCreate("java").apply { option("lite") } }
        }
    }
}
