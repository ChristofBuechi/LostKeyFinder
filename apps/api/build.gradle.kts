import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    jacoco
}

group = "ch.lostkeyfinder"
version = "0.1.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
repositories { mavenCentral() }
ktlint { version = "1.8.0" }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("de.bwaldvogel:mongo-java-server:1.47.0")
    // MemoryBackend's extension API exposes Netty Channel (otherwise runtime-only).
    testImplementation("io.netty:netty-transport")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
tasks.withType<Test> { useJUnitPlatform() }
tasks.test { exclude("**/OpenApiExportTest.class", "**/FirestoreIntegrationTest.class") }

tasks.register<Test>("httpTest") {
    description = "Offline MVC contract tests using real Spring configuration and fake health contributors"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/FoundationHttpTest.class")
}

val exportOpenApi by tasks.registering(Test::class) {
    description = "Export the real Spring MVC OpenAPI contract without a database or HTTP server"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/OpenApiExportTest.class")
    outputs.file(layout.projectDirectory.file("openapi.json"))
    outputs.upToDateWhen { false }
}

tasks.register<Test>("integrationTest") {
    description = "Explicit real Firestore transaction smoke test; requires FIRESTORE_MONGODB_URI"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/FirestoreIntegrationTest.class")
    outputs.upToDateWhen { false }
}

jacoco { toolVersion = "0.8.14" }
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
tasks.bootJar { archiveFileName = "api.jar" }
