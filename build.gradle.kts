import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.vanish"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyMMdd-HHmm")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

version = (if (!hasProperty("ver")) {
    "1.0.0-${getTime()}-SNAPSHOT"
} else {
    val ver = property("ver") as String
    val base = if (ver.startsWith("v")) ver.drop(1) else ver.replace('/', '-')
    if (ver.startsWith("v") && !ver.lowercase().contains("-rc-")) base else "$base-SNAPSHOT"
}).uppercase()

allprojects {
    repositories {
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
            content {
                includeGroup("org.spigotmc")
                includeGroup("io.papermc.paper")
                includeGroup("net.md-5")
            }
        }
        maven {
            name = "codemc"
            url = uri("https://repo.codemc.io/repository/maven-public/")
            content {
                includeGroup("com.github.retrooper")
                includeGroup("io.github.retrooper")
            }
        }
        maven {
            name = "helpchat"
            url = uri("https://repo.helpch.at/releases/")
            content {
                includeGroup("me.clip")
            }
        }
        mavenCentral()
    }
}

dependencies {
    // Compile against the oldest supported Bukkit API. Anything newer is
    // reached through dev.vanish.bukkit.compat.Compat, never a hard link.
    compileOnly("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.26.1")

    // Optional integration; compile-only so the plugin loads without PlaceholderAPI.
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation(project(":common"))

    // Shaded into the plugin jar; server owners do not install PacketEvents.
    implementation("com.github.retrooper:packetevents-spigot:2.14.0")
    // PacketEvents' wrapper API uses Adventure components, but does not carry
    // the plain/gson serializers our policy and fallback messages need.
    implementation("net.kyori:adventure-api:4.26.1")
    implementation("net.kyori:adventure-text-serializer-gson:4.26.1")
    implementation("net.kyori:adventure-text-serializer-plain:4.26.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")

    testImplementation("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    // The Bukkit plugin loads on 1.8.8-era servers.
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand(mapOf("VERSION" to project.version))
    }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("Vanish")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    manifest {
        attributes(
            "Implementation-Title" to "Vanish",
            "Implementation-Version" to project.version,
        )
    }

    // Keep our PacketEvents entirely private so a server can also run Grim,
    // another PacketEvents plugin, or an older PacketEvents release.
    relocate("com.github.retrooper.packetevents", "dev.vanish.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "dev.vanish.libs.packetevents.platform")
    relocate("net.kyori", "dev.vanish.libs.kyori")
    relocate("com.google.gson", "dev.vanish.libs.gson")

    // PacketEvents' Spigot artifact ships its own plugin.yml, so duplicates
    // cannot be included wholesale. The Adventure provider registrations are
    // declared in src/main/resources/META-INF/services instead.
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(shadowJar)
}

tasks.register("printProjectName") {
    doLast {
        println(rootProject.name)
    }
}

tasks.register("release") {
    dependsOn("build")

    doLast {
        if (!version.toString().endsWith("-SNAPSHOT")) {
            val releaseJar = layout.buildDirectory.file("libs/Vanish.jar").get().asFile
            shadowJar.get().archiveFile.get().asFile.renameTo(releaseJar)
        }
    }
}

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        // The platform-independent core and the Bukkit loader stay on Java 8
        // bytecode. Mod loader modules override this to their server runtime.
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
