dependencies {
    // The core only sees PacketEvents' wrapper API; the loader shades the
    // implementation at runtime.
    compileOnly("com.github.retrooper:packetevents-api:2.14.0")
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.26.1")

    testImplementation("com.github.retrooper:packetevents-api:2.14.0")
    testImplementation("net.kyori:adventure-api:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-gson:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.26.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("net.bytebuddy:byte-buddy:1.14.18")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.jar {
    archiveBaseName.set("vanish-common")
}
