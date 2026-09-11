import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val localSigningFile = rootProject.file("signing.local.properties")
    if (localSigningFile.isFile) {
        localSigningFile.inputStream().use(::load)
    }
}

val signingStoreFile = providers.gradleProperty("hypermodifierStoreFile").orNull
    ?: signingProperties.getProperty("storeFile")
val signingKeyAlias = providers.gradleProperty("hypermodifierKeyAlias").orNull
    ?: signingProperties.getProperty("keyAlias")
val signingPasswordFromKeychain = runCatching {
    val process = ProcessBuilder(
        "/usr/bin/security", "find-generic-password",
        "-s", "com.aritxonly.hypermodifier.signing",
        "-a", "release",
        "-w",
    ).start()
    val password = process.inputStream.bufferedReader().use { it.readText().trim() }
    password.takeIf { process.waitFor() == 0 && it.isNotBlank() }
}.getOrNull()
val signingPassword = providers.gradleProperty("hypermodifierSigningPassword")
    .orElse(providers.environmentVariable("HYPERMODIFIER_SIGNING_PASSWORD"))
    .orNull ?: signingPasswordFromKeychain
val hasReleaseSigning = !signingStoreFile.isNullOrBlank()
    && !signingKeyAlias.isNullOrBlank()
    && !signingPassword.isNullOrBlank()
val configuredVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 31
val configuredVersionName = providers.gradleProperty("versionName").orNull ?: "1.3.7"

android {
    namespace = "com.aritxonly.myhypermodifier"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aritxonly.myhypermodifier"
        minSdk = 33
        targetSdk = 35
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(signingStoreFile)
                storePassword = signingPassword
                keyAlias = signingKeyAlias
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("com.materialkolor:material-kolor:4.1.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
    implementation("io.github.kyant0:shapes:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
