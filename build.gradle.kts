plugins {
    java
    id("org.springframework.boot") version "4.0.0-RC1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "6.25.0"
}

ext {
    set("springCloudVersion", "2025.1.0-M4")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0-M4")
    }
}

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        eclipse()
        indentWithSpaces(4)
        importOrder("java", "javax", "org", "com", " ")
        removeUnusedImports()
        endWithNewline()
        trimTrailingWhitespace()
    }
    groovyGradle {
        target("*.gradle")
        greclipse()
    }
}

tasks.compileJava {
    dependsOn("spotlessApply")
}

tasks.compileTestJava {
    dependsOn("spotlessApply")
}

group = "team.washer"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Develop Environment Tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // Database Drivers
    runtimeOnly("com.mysql:mysql-connector-j")

    // Jakarta EE
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation("jakarta.transaction:jakarta.transaction-api")

    // QueryDSL
    implementation("io.github.openfeign.querydsl:querydsl-jpa:7.0")

    // JSON
    implementation("net.minidev:json-smart:2.6.0")

    // OpenFeign
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.openfeign:feign-jackson:13.5")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0-M1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
