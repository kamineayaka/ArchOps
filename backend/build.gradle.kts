plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.archops"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/spring")
    maven("https://maven.aliyun.com/repository/google")
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

    // Apache MINA SSHD — production adapter lives on the 执行引擎 (ADR-0044).
    // Control-plane CI keeps a recording fake; production 代发 is gRPC (ADR-0045).
    implementation("org.apache.sshd:sshd-core:2.14.0")
    implementation("org.apache.sshd:sshd-common:2.14.0")

    implementation("io.grpc:grpc-netty-shaded:1.68.2")
    implementation("io.grpc:grpc-protobuf:1.68.2")
    implementation("io.grpc:grpc-stub:1.68.2")
    implementation("io.grpc:grpc-services:1.68.2")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Real PostgreSQL for HTTP acceptance without requiring a local Docker daemon.
    testImplementation("io.zonky.test:embedded-database-spring-test:2.6.0")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.2"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("archops.jar")
    mainClass.set("com.archops.ArchOpsApplication")
}

tasks.register<org.springframework.boot.gradle.tasks.bundling.BootJar>("executorBootJar") {
    group = "build"
    description = "Fat jar for the 执行引擎 process"
    archiveFileName.set("archops-executor.jar")
    mainClass.set("com.archops.executor.ExecutorApplication")
    classpath = sourceSets.main.get().runtimeClasspath
}

tasks.named("assemble") {
    dependsOn("executorBootJar")
}
