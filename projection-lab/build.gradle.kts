plugins { id("com.android.application") }
android {
    namespace = "io.github.sixzleo.tabfold.projection"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.sixzleo.tabfold.projection"
        minSdk = 33
        targetSdk = 35
        versionCode = 26
        versionName = "0.3.15-finger-restore"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { aidl = true }
}
dependencies {
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
