pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
    }
}

plugins {
    // ForgeGradle 7 needs a JDK 25 build JVM while Minecraft mods compile
    // against a JDK 21 toolchain; let Gradle provision the missing one.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Vanish"

// Platform-independent policy and packet projections.
include("common")

// Fabric, Forge and NeoForge reuse the core in :common and the shared mod
// service under modloader/; each module only supplies loader events, commands
// and a shaded packet transport.
include("fabric")
include("forge")
include("neoforge")
