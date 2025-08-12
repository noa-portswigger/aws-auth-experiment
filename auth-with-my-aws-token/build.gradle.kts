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
    implementation("org.eclipse.jetty:jetty-client:12.0.15")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("info.picocli:picocli:4.7.6")
    implementation("software.amazon.awssdk:auth:2.32.20")
    implementation("software.amazon.awssdk:core:2.32.20")
    implementation("software.amazon.awssdk:sdk-core:2.32.20")
    implementation("software.amazon.awssdk:sso:2.32.20")
    implementation("software.amazon.awssdk:ssooidc:2.32.20")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass.set("net.portswigger.Client")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}