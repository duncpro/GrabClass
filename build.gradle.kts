plugins {
    kotlin("jvm") version "1.9.22"
    application
    kotlin("plugin.serialization") version "1.9.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.seleniumhq.selenium:selenium-java:4.16.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}

application {
    mainClass.set("MainKt")
}

