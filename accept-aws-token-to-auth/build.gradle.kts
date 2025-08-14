plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jetty:jetty-server:12.0.15")
    implementation("org.eclipse.jetty:jetty-client:12.0.15")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.2")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass.set("net.portswigger.App")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}