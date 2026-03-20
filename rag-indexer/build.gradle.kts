plugins {
    kotlin("jvm") version "2.3.0"
    application
    id("com.gradleup.shadow") version "9.0.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // PDF parsing
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // DOC/DOCX parsing
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-scratchpad:5.3.0")   // .doc (legacy Word)
    implementation("org.apache.poi:poi-ooxml:5.3.0")         // .docx

    // HTTP client for Ollama API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("ragindexer.MainKt")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "ragindexer.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
