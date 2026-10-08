plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.cyclonedx.bom") version "1.10.0"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
}

group = "com.nova"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("software.amazon.awssdk:s3:2.28.29")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("org.testcontainers:minio:1.20.3")
}

// 공급망 방어: 의존성 잠금. 최초 생성은 `./gradlew dependencies --write-locks`.
dependencyLocking {
    lockAllConfigurations()
}

springBoot {
    buildInfo()
}

tasks.withType<Test> {
    useJUnitPlatform()
    // CI 로그에서 실패 원인(예외 메시지·cause)이 보이도록 한다. 기본값은 예외 클래스명만 출력한다.
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
