// Cloud streaming speech-to-text (OpenAI Realtime, Deepgram) over WebSockets.
// Uses only the JDK HTTP client, no extra libraries.
description = "PV-Transcribe cloud engines"

dependencies {
    implementation(project(":core"))
}

tasks.test {
    System.getProperty("cloud.online")?.let { systemProperty("cloud.online", it) }
    testLogging.showStandardStreams = true
}
