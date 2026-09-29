import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Descarga manga-ocr (revisión fija de Hugging Face, SHA-256 comprobado) para
 * meterlo dentro del APK. Se guarda en la caché de Gradle, así sólo se baja una
 * vez. Con MANGA_OCR_DIR se puede usar una copia local del modelo.
 */
abstract class DownloadMangaOcr : DefaultTask() {
    @get:Input abstract val baseUrl: Property<String>
    @get:Input abstract val files: MapProperty<String, String> // nombre -> SHA-256
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:Internal abstract val localDir: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        val out = outputDir.get().asFile.resolve("manga_ocr").apply { mkdirs() }
        for ((name, sha) in files.get()) {
            val cached = File(cache, name)
            if (!cached.exists() || sha256(cached) != sha) {
                val local = localDir.orNull?.let { File(it, name) }
                val tmp = File(cache, "$name.part")
                if (local != null && local.exists()) {
                    local.copyTo(tmp, overwrite = true)
                } else {
                    logger.lifecycle("Descargando $name de Hugging Face…")
                    URI("${baseUrl.get()}/$name").toURL().openStream().use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                }
                check(sha256(tmp) == sha) { "El archivo $name no coincide con su SHA-256" }
                check(tmp.renameTo(cached)) { "No se pudo guardar $name en la caché" }
            }
            cached.copyTo(File(out, name), overwrite = true)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val mangaOcrRevision = "f9023406bb2f6b17df67bc4a327c56ecd20611f0"

android {
    namespace = "com.mangatraductor.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mangatraductor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"
    }

    // Dos versiones de la misma app:
    //  - completa: manga-ocr y el OCR de ML Kit van dentro del APK (~150 MB).
    //  - ligera:   cabe en 30 MB; el OCR de ML Kit lo da Google Play Services y
    //              manga-ocr se descarga solo la primera vez que se abre la app.
    flavorDimensions += "ocr"
    productFlavors {
        create("completa") {
            dimension = "ocr"
            isDefault = true
        }
        create("ligera") {
            dimension = "ocr"
            versionNameSuffix = "-ligera"
        }
    }

    signingConfigs {
        // Clave de pruebas guardada en el repositorio: así los APK compilados aquí y
        // en GitHub Actions tienen la misma firma y se instalan uno encima de otro.
        create("compartida") {
            storeFile = file("signing/manga-traductor.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        debug {
            signingConfig = signingConfigs.getByName("compartida")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("compartida")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { viewBinding = true }

    androidResources {
        // Sólo textos de librerías en español e inglés: el APK pesa menos.
        localeFilters += listOf("es", "en")
        // El modelo se guarda sin comprimir para leerlo directamente del APK.
        noCompress += "onnx"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("samplePage", rootProject.file("../manga-translator/examples/sample_page.png").path)
                // Prueba con manga real (opcional): páginas, modelos de calidad, manga-ocr y Qwen.
                for ((property, env) in listOf(
                    "realMangaDir" to "REAL_MANGA_DIR", "realMangaPages" to "REAL_MANGA_PAGES",
                    "qualityDir" to "QUALITY_DIR", "mangaOcrDir" to "MANGA_OCR_DIR", "qwenDir" to "QWEN_DIR",
                    "realMangaTranslations" to "REAL_MANGA_TRANSLATIONS", "realMangaUppercase" to "REAL_MANGA_UPPERCASE",
                    "realMangaFont" to "REAL_MANGA_FONT",
                )) it.systemProperty(property, System.getenv(env) ?: "")
                it.maxHeapSize = "3g"
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

androidComponents {
    onVariants { variant ->
        if (variant.flavorName != "completa") return@onVariants
        val task = tasks.register<DownloadMangaOcr>("downloadMangaOcr${variant.name.replaceFirstChar { it.uppercase() }}") {
            baseUrl.set("https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/$mangaOcrRevision/onnx")
            files.set(mapOf(
                "encoder_model_quantized.onnx" to "ddd1af56963093795705fa38da6ce7e6567d1658e7c7359db7e13fcd37dbf279",
                "decoder_model_quantized.onnx" to "2e7177d2b0a59f1c612b694ed70c13971bee765cc2b2bc7bc9376e4753652f27",
            ))
            cacheDir.set(File(gradle.gradleUserHomeDir, "caches/manga-ocr/$mangaOcrRevision"))
            System.getenv("MANGA_OCR_DIR")?.let { localDir.set(it) }
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, DownloadMangaOcr::outputDir)
    }
}

dependencies {
    implementation(project(":core"))

    // 1.24: la versión más ligera que soporta el modelo cuantizado (ConvInteger).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    // OCR de ML Kit: en la completa el modelo va dentro del APK; en la ligera lo
    // aporta Google Play Services (se descarga una vez).
    // OCR de ML Kit para manga (japonés), manhua (chino) y manhwa (coreano).
    for (script in listOf("japanese", "chinese", "korean")) {
        "completaImplementation"("com.google.mlkit:text-recognition-$script:16.0.1")
        "ligeraImplementation"("com.google.android.gms:play-services-mlkit-text-recognition-$script:16.0.1")
    }
    "ligeraImplementation"("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.mlkit:translate:17.0.3")
    // Gemini Nano (la IA que viene en el Pixel 10 y otros móviles compatibles).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

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
    // ONNX Runtime para el PC, para probar el OCR cargado desde los assets de la app.
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.24.3")
}
