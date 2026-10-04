// Platform-independent heart of PV-Transcribe: speech sessions, threading, engine abstraction,
// phrase matching and subtitle state. Knows nothing about Bukkit, Fabric or a specific voice chat.
description = "PV-Transcribe core"

dependencies {
    api(project(":api"))
}
