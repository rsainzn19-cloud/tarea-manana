import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Lógica de la traducción en Kotlin puro (sin Android), para poder probarla en el PC.
plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-test-fixtures` // página de prueba compartida con las pruebas de la app
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

val onnxRuntime = "1.30.0"

dependencies {
    api("com.anthropic:anthropic-java:2.65.0")
    // En el móvil las clases de ONNX Runtime las aporta onnxruntime-android.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:$onnxRuntime")

    testImplementation(kotlin("test"))
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$onnxRuntime")
}

tasks.test {
    // Carpeta con el modelo manga-ocr en ONNX para la prueba de OCR (opcional).
    systemProperty("mangaOcrDir", System.getenv("MANGA_OCR_DIR") ?: "")
    systemProperty("samplePage", rootProject.file("../manga-translator/examples/sample_page.png").path)
    testLogging { showStandardStreams = true }
}
