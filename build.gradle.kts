plugins {
    id("java-library")
    id("application")
}

group = "dev.sbs"
version = "0.1.0"

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

    // https://central.sonatype.com/artifact/com.discord4j/discord4j-core/versions
    api(libs.discord4j)
    api("dev.sbs:api:0.1.0")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    test {
        useJUnitPlatform()
    }
}
