// T-one (T-Bank, Apache 2.0): streaming Russian speech recognition that writes what it hears letter by
// letter (CTC), so it does not replace unknown words with dictionary words like Vosk does.
// ONNX Runtime is not shaded: Paper/Spigot download it via plugin.yml "libraries".
description = "PV-Transcribe T-one engine"

dependencies {
    implementation(project(":core"))
    compileOnly("com.microsoft.onnxruntime:onnxruntime:${property("onnxRuntimeVersion")}")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:${property("onnxRuntimeVersion")}")
}

tasks.test {
    // forward -Dtone.model / -Dtone.wav to the optional real-model test (ToneEngineIT)
    listOf("tone.model", "tone.wav").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    testLogging.showStandardStreams = true
}
