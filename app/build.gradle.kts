plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val githubAppClientId = providers.gradleProperty("githubAppClientId").orElse("").get()
val requireGitHubAppClientId = providers.gradleProperty("requireGitHubAppClientId")
    .map { it.toBooleanStrict() }
    .orElse(false)
    .get()
require(!requireGitHubAppClientId || githubAppClientId.isNotEmpty()) {
    "GitHub authentication builds require githubAppClientId. Configure APP_CLIENT_ID in GitHub Actions."
}
require(githubAppClientId.isEmpty() || githubAppClientId.matches(Regex("[A-Za-z0-9]{1,100}"))) {
    "Invalid githubAppClientId Gradle property"
}

android {
    namespace = "com.karim.aiprojectmanager"
    compileSdk = 35
    testBuildType = "vnext"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "com.karim.aiprojectmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GITHUB_APP_CLIENT_ID", "\"$githubAppClientId\"")
    }
    // Installs beside the owner's app with an independent private data directory.
    // Normal debug/release identifiers and release signing configuration stay unchanged.
    buildTypes {
        create("vnext") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".vnext"
            versionNameSuffix = "-vnext"
            matchingFallbacks += listOf("debug")
        }
    }
    // VNext instrumentation targets its isolated package while reusing Debug-only test hosts.
    sourceSets.getByName("vnext") {
        java.srcDir("src/debug/java")
        manifest.srcFile("src/debug/AndroidManifest.xml")
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

// Validate only tasks for the isolated build type; ordinary Debug/Release remain independent.
val verifyVnextGitHubAppClientId = tasks.register("verifyVnextGitHubAppClientId") {
    doLast {
        if (githubAppClientId.isEmpty()) {
            throw GradleException(
                "VNext requires githubAppClientId. Configure the Gradle property or GitHub Actions APP_CLIENT_ID."
            )
        }
    }
}
tasks.configureEach {
    if (name.contains("Vnext") && name != "verifyVnextGitHubAppClientId") {
        dependsOn(verifyVnextGitHubAppClientId)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
    add("vnextImplementation", "androidx.compose.ui:ui-test-manifest:1.8.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
