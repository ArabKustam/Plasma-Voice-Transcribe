plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "org.lavacast.pvtranscribe"
    version = property("pluginVersion") as String
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.plasmoverse.com/releases")
    }

    extensions.configure<JavaPluginExtension> {
        // Java 17 bytecode keeps the shared modules usable on 1.20.x servers (Java 17)
        // as well as on 1.21.x servers (Java 21).
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
    }

    dependencies {
        "compileOnly"("org.jetbrains:annotations:24.1.0")
        "testCompileOnly"("org.jetbrains:annotations:24.1.0")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
        options.compilerArgs.addAll(listOf("-Xlint:-options"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
