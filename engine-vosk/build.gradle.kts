// Offline streaming speech recognition with Vosk (https://alphacephei.com/vosk).
// Vosk itself is not shaded: Paper/Spigot download it via plugin.yml "libraries",
// Fabric bundles it as a nested jar.
description = "PV-Transcribe Vosk engine"

dependencies {
    implementation(project(":core"))
    compileOnly("com.alphacephei:vosk:${property("voskVersion")}")
    testImplementation("com.alphacephei:vosk:${property("voskVersion")}")
}

tasks.test {
    // forward -Dvosk.models / -Dvosk.wav to the optional real-model test (VoskEngineIT)
    listOf("vosk.models", "vosk.wav").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    testLogging.showStandardStreams = true
}
