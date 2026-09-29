plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.fitplan.planiim"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fitplan.planiim"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
}
