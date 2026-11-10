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
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "com.karim.aiprojectmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GITHUB_APP_CLIENT_ID", "\"$githubAppClientId\"")
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
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
