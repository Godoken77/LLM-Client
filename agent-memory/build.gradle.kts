plugins {
    kotlin("jvm") version "2.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(17)
}
