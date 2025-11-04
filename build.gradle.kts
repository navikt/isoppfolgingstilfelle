import com.adarshr.gradle.testlogger.theme.ThemeType
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

group = "no.nav.syfo"
version = "1.0.0"

val confluent = "8.0.0"
val flyway = "11.11.2"
val hikari = "6.3.0"
val jackson = "2.19.2"
val jedis = "5.2.0"
val kafka = "3.9.0"
val ktor = "3.3.0"
val logback = "1.5.18"
val logstashEncoder = "8.1"
val mockk = "1.14.5"
val nimbusJoseJwt = "10.4.2"
val micrometerRegistry = "1.12.13"
val postgres = "42.7.7"
val postgresEmbedded = "2.1.1"
val redisEmbedded = "0.7.3"
val postgresRuntimeVersion = "17.5.0"

plugins {
    kotlin("jvm") version "2.2.10"
    id("com.gradleup.shadow") version "8.3.7"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    id("com.adarshr.test-logger") version "4.0.0"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-server-auth-jwt:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-server-call-id:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-client-apache:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-jackson:$ktor")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoder")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistry")

    // Cache
    implementation("redis.clients:jedis:$jedis")
    testImplementation("it.ozimov:embedded-redis:$redisEmbedded")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson")

    // Database
    implementation("org.flywaydb:flyway-database-postgresql:$flyway")
    implementation("com.zaxxer:HikariCP:$hikari")
    implementation("org.postgresql:postgresql:$postgres")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbedded")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$postgresRuntimeVersion"))

    // JWT
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwt")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("io.confluent:kafka-avro-serializer:$confluent", excludeLog4j)
    constraints {
        implementation("org.apache.avro:avro") {
            because("io.confluent:kafka-avro-serializer:$confluent -> https://www.cve.org/CVERecord?id=CVE-2023-39410")
            version {
                require("1.12.0")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.27.1")
            }
        }
    }
    implementation("org.apache.kafka:kafka_2.13:$kafka", excludeLog4j)
    constraints {
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.apache.kafka:kafka_2.13:$kafka -> https://github.com/advisories/GHSA-6qvw-249j-h44c")
            version {
                require("0.9.6")
            }
        }
        implementation("commons-beanutils:commons-beanutils") {
            because("org.apache.kafka:kafka_2.13:$kafka -> https://www.cve.org/CVERecord?id=CVE-2025-48734")
            version {
                require("1.11.0")
            }
        }
    }

    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwt")
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-mock:$ktor")
    testImplementation("io.mockk:mockk:$mockk")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
        testlogger {
            theme = ThemeType.STANDARD_PARALLEL
            showFullStackTraces = true
            showPassed = false
        }
    }

    compileKotlin {
        dependsOn(":generateAvroJava")
    }

    withType<KtLintCheckTask> {
        dependsOn(":generateTestAvroJava")
    }
}
