plugins {
    id("fabric-loom") version "1.16.3"
    id("com.gradleup.shadow")
}

val minecraftVersion = "1.21.11"
val fabricLoaderVersion = "0.20.1"
val fabricApiVersion = "0.141.6+1.21.11"

// Only these artifacts are bundled. Minecraft, Fabric Loader and Fabric API
// are provided by the server and must never be shaded into the mod.
val bundled: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = true
}

sourceSets.main {
    java.srcDir("../modloader/src/main/java")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common"))
    compileOnly("com.github.retrooper:packetevents-api:2.14.0")
    compileOnly("com.github.retrooper:packetevents-netty-common:2.14.0")

    bundled(project(":common"))
    bundled("com.github.retrooper:packetevents-api:2.14.0")
    bundled("com.github.retrooper:packetevents-netty-common:2.14.0")
    bundled("net.kyori:adventure-api:4.26.1")
    bundled("net.kyori:adventure-text-serializer-gson:4.26.1")
    bundled("net.kyori:adventure-text-serializer-plain:4.26.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

configurations.named("bundled") {
    // Netty and the logging API come from the Minecraft server.
    exclude(group = "io.netty")
    exclude(group = "org.slf4j")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.jar {
    archiveBaseName.set("Vanish-Fabric")
}

val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("Vanish-Fabric")
    archiveClassifier.set("unmapped")
    // The release artifact is remapJar's output; keep the intermediate out of libs/.
    destinationDirectory.set(layout.buildDirectory.dir("tmp/shadow"))
    configurations = listOf(bundled)

    relocate("com.github.retrooper.packetevents", "dev.vanish.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "dev.vanish.libs.packetevents.platform")
    relocate("net.kyori", "dev.vanish.libs.kyori")
    relocate("com.google.gson", "dev.vanish.libs.gson")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

// Remap the shaded jar so the mod runs under Fabric's intermediary names.
tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveBaseName.set("Vanish-Fabric")
    archiveClassifier.set("")
    inputFile.set(shadowJar.flatMap { it.archiveFile })
    dependsOn(shadowJar)
}

tasks.register("printProjectName") {
    doLast {
        println("Vanish-Fabric")
    }
}
