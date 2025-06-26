plugins {
    id("java")
    id("application")
}

repositories {
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // IntelliJ Annotations
    implementation(group = "org.jetbrains", name = "annotations", version = "24.0.1")

    // Resource Checker Annotations
    implementation(group = "dev.sbs", name = "simplified-annotations", version = "1.0.2")
    annotationProcessor(group = "dev.sbs", name = "simplified-annotations", version = "1.0.2")

    // Lombok Annotations
    compileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.30")
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.30")
    testCompileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.30")
    testAnnotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.30")

    // Tests
    testImplementation(group = "org.hamcrest", name = "hamcrest", version = "2.2")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.0")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.0")

    // Logging
    implementation(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.20.0")
    implementation(group = "org.apache.logging.log4j", name = "log4j-slf4j-impl", version = "2.20.0")

    // https://central.sonatype.com/artifact/com.discord4j/discord4j-core/versions
    implementation(group = "com.discord4j", name = "discord4j-core", version = "3.3.0-SNAPSHOT")
    implementation(optionalProject(":api", "com.github.skyblock-simplified:api:master-SNAPSHOT"))
}

fun Project.optionalProject(path: String, fallbackDependency: String): Any =
    if (project.rootProject.findProject(path) != null) {
        project(path)
    } else {
        fallbackDependency
    }

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    test {
        useJUnitPlatform()
    }
}
