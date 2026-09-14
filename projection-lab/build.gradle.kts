plugins { id("com.android.application") }
android {
    namespace = "io.github.sixzleo.tabfold.projection"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.sixzleo.tabfold.projection"
        minSdk = 33
        targetSdk = 35
        versionCode = 84
        versionName = "0.4.27"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { aidl = true }
}
dependencies {
    implementation("com.android.tools.build:apksig:8.6.1")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
