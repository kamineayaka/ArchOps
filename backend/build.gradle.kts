plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.archops"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.9")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Later (vertical slice): Apache MINA SSHD — plan execution / workbench SSH (ADR-0043)
    // implementation("org.apache.sshd:sshd-core:2.14.0")
    // implementation("org.apache.sshd:sshd-common:2.14.0")

    // Later (vertical slice): WebClient for controlled AI egress (ADR-0041 / ADR-0043)
    // implementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Real PostgreSQL for HTTP acceptance without requiring a local Docker daemon.
    testImplementation("io.zonky.test:embedded-database-spring-test:2.6.0")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
