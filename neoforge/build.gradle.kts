plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("com.gradleup.shadow")
}

val minecraftVersion = "1.21.11"
val neoForgeVersion = "21.11.48"

val bundled: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = true
}

sourceSets.main {
    java.srcDir("../modloader/src/main/java")
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.github.retrooper:packetevents-api:2.14.0")
    compileOnly("com.github.retrooper:packetevents-netty-common:2.14.0")

    bundled(project(":common"))
    bundled("com.github.retrooper:packetevents-api:2.14.0")
    bundled("com.github.retrooper:packetevents-netty-common:2.14.0")
    bundled("net.kyori:adventure-api:5.2.0")
    bundled("net.kyori:adventure-text-serializer-gson:5.2.0")
    bundled("net.kyori:adventure-text-serializer-plain:5.2.0")
}

configurations.named("bundled") {
    exclude(group = "io.netty")
    exclude(group = "org.slf4j")
}

neoForge {
    version = neoForgeVersion
    mods {
        register("vanish") {
            sourceSet(sourceSets.main.get())
        }
    }
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    inputs.property("neoforge", neoForgeVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft" to minecraftVersion,
                "neoforge" to neoForgeVersion,
            ))
    }
}

tasks.jar {
    // Only the shaded mod jar is a release artifact.
    enabled = false
}

val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("Vanish-NeoForge")
    archiveClassifier.set("")
    configurations = listOf(bundled)

    relocate("com.github.retrooper.packetevents", "dev.vanish.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "dev.vanish.libs.packetevents.platform")
    relocate("net.kyori", "dev.vanish.libs.kyori")
    relocate("com.google.gson", "dev.vanish.libs.gson")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(shadowJar)
}

tasks.register("printProjectName") {
    doLast {
        println("Vanish-NeoForge")
    }
}
