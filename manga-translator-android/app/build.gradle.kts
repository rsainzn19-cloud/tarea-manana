import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mangatraductor.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mangatraductor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Un APK por tipo de procesador: arm64-v8a (casi todos los móviles) y
    // armeabi-v7a (móviles antiguos o muy básicos de 32 bits).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { viewBinding = true }

    // Sólo textos de librerías en español e inglés: el APK pesa menos.
    androidResources { localeFilters += listOf("es", "en") }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("samplePage", rootProject.file("../manga-translator/examples/sample_page.png").path)
                it.testLogging { showStandardStreams = true }
                // Opcional: usar los jars de Android de Robolectric ya descargados.
                System.getenv("ROBOLECTRIC_DEPS_DIR")?.let { dir ->
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", dir)
                }
            }
        }
    }

    packaging {
        // Comprime las librerías nativas dentro del APK: la descarga pesa mucho menos.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/INDEX.LIST",
            )
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    // 1.24: la versión más ligera que soporta el modelo cuantizado (ConvInteger).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    // OCR de ML Kit servido por Google Play Services: el modelo no va dentro
    // del APK (se descarga una vez), así el APK pesa ~7 MB menos.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.mlkit:translate:17.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")

    // Pruebas en el PC con un Android simulado (Robolectric).
    testImplementation(testFixtures(project(":core")))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")
}
