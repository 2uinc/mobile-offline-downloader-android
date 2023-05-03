plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {

    compileSdk = 33

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    defaultConfig {
        minSdk = 23
    }

    buildTypes {
        create("staging") {
        }
    }

    namespace = "com.twou.offline"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("io.github.pilgr:paperdb:2.7.2")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
}