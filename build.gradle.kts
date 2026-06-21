plugins {
    java
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
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Configure bootRun to use the main TAC application by default
// Use -PmainClass=... to override
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set(project.findProperty("mainClass") as String?
        ?: "com.twilio.agentconnect.TwilioAgentConnectApplication")
}
