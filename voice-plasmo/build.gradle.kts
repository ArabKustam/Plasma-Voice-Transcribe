// Plasmo Voice -> PV-Transcribe adapter. Uses only the Plasmo Voice server API, which is the same on
// Paper/Spigot and Fabric, so this module is shared by all platforms.
description = "PV-Transcribe Plasmo Voice adapter"

dependencies {
    implementation(project(":core"))
    compileOnly("su.plo.voice.api:server:${property("plasmoVoiceVersion")}")
    // Plasmo Voice API annotations are written in Kotlin; only needed to read them at compile time
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
}
