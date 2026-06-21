plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.twilio"
version = "0.1.0-SNAPSHOT"

java {
    // Supports Java 17+
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Twilio
    implementation("com.twilio.sdk:twilio:10.1.5")

    // Reactive
    implementation("io.projectreactor:reactor-core")
    implementation("io.projectreactor.netty:reactor-netty")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.1.0")
    implementation("io.github.resilience4j:resilience4j-reactor:2.1.0")

    // JSON
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // OpenAI SDK (optional - for examples)
    implementation("com.theokanning.openai-gpt3-java:service:0.18.2")

    // Lombok removed due to Java 24 incompatibility
    // Use manual getters/setters instead

    // Logging
    implementation("org.slf4j:slf4j-api")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")

    // Mock web server for WebClient-based context clients
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Mockito + ByteBuddy versions that understand the Java 24 (class v68) runtime.
    // The versions pulled in transitively by spring-boot-starter-test (Mockito 5.7 /
    // ByteBuddy 1.14.x) fail with "OpenedClassReader" errors on JDK 24.
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("net.bytebuddy:byte-buddy:1.15.11")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.15.11")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Mockito/ByteBuddy need experimental mode to generate mocks on the Java 24
    // (class file major version 68) runtime.
    systemProperty("net.bytebuddy.experimental", "true")
    finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco {
    toolVersion = "0.8.13"  // 0.8.13+ supports Java 24 (class file major version 68)
}

// Coverage excludes: the runnable example app and the Spring Boot entrypoint are
// wiring/demo, not library logic.
val coverageExclusions = listOf(
    "com/twilio/agentconnect/examples/**",
    "com/twilio/agentconnect/TwilioAgentConnectApplication.class"
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

// Fail `./gradlew check` if coverage drops below the thresholds above.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Two @SpringBootApplication classes exist (the SDK app + the OpenAI example),
// so the main class must be declared explicitly for jar packaging / resolution.
springBoot {
    mainClass.set("com.twilio.agentconnect.TwilioAgentConnectApplication")
}

// Configure bootRun to use the main TAC application by default
// Use -PmainClass=... to override (e.g. to run the OpenAI example).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set(project.findProperty("mainClass") as String?
        ?: "com.twilio.agentconnect.TwilioAgentConnectApplication")
}
