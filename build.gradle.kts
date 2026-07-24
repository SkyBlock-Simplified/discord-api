plugins {
    id("java-library")
    id("application")
}

group = "dev.sbs"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal() // annotations is not yet published to Maven Central
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Annotations
    implementation("io.github.simplified-dev:annotations:2.5.0")
    annotationProcessor("io.github.simplified-dev:annotations:2.5.0")
    testAnnotationProcessor("io.github.simplified-dev:annotations:2.5.0")

    // Tests
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)

    // Test logging provider (log4j-api has no binding on the test classpath otherwise)
    testRuntimeOnly(libs.log4j.core)
    testRuntimeOnly(libs.log4j.slf4j2)

    // https://central.sonatype.com/artifact/com.discord4j/discord4j-core/versions
    api(libs.discord4j)

    // Simplified Libraries (extracted to github.com/simplified-dev)
    api("com.github.simplified-dev:collections") { version { strictly("c741e14") } }
    api("com.github.simplified-dev:utils") { version { strictly("a2f3ccd") } }
    api("com.github.simplified-dev:reflection") { version { strictly("c02511a") } }
    api("com.github.simplified-dev:scheduler") { version { strictly("f9b1bd4") } }
    api("com.github.simplified-dev:yaml") { version { strictly("586bc52") } }
    api("com.github.simplified-dev:client") { version { strictly("64ae978") } }
    api("com.github.simplified-dev:dataflow") { version { strictly("9e5906d") } }

    implementation(libs.sentry)
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("harness.debug", System.getProperty("harness.debug", "false"))
    }

    register<JavaExec>("generateDiagrams") {
        description = "Generates SVG hierarchy diagrams for context and component packages"
        group = "documentation"
        mainClass.set("dev.simplified.discordapi.diagram.DiagramGenerator")
        classpath = sourceSets["test"].runtimeClasspath
    }
}
