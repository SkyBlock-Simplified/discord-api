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
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Annotations
    annotationProcessor(libs.simplified.annotations)

    // Lombok Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Tests
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)

    // https://central.sonatype.com/artifact/com.discord4j/discord4j-core/versions
    api(libs.discord4j)
    api("dev.sbs:api:0.1.0")
    implementation(libs.sentry)
}

tasks {
    test {
        useJUnitPlatform()
    }

    register<JavaExec>("generateDiagrams") {
        description = "Generates SVG hierarchy diagrams for context and component packages"
        group = "documentation"
        mainClass.set("dev.sbs.discordapi.diagram.DiagramGenerator")
        classpath = sourceSets["test"].runtimeClasspath
    }
}
