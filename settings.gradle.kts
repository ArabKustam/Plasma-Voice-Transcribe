pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

rootProject.name = "PV-Transcribe"

include(
    "api",
    "core",
    "engine-vosk",
    "engine-tone",
    "engine-cloud",
    "voice-plasmo",
    "platform-paper",
)
