/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/8.0.2/userguide/building_java_projects.html
 */


import org.gradle.api.tasks.JavaExec

tasks.withType(JavaExec::class.java) {
    jvmArgs("-Xms2048m", "-Xmx2048m")
}


plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")

    // This dependency is used by the application.
    // implementation("com.google.guava:guava:31.1-jre")
    implementation("ch.qos.logback:logback-classic:1.4.9") // Logback Classic
    implementation("org.slf4j:slf4j-api:2.0.7") // SLF4J API
    implementation("org.apache.jena", "apache-jena-libs", "4.9.0")
}

application {
    // Define the main class for the application.
    mainClass.set("jenacontroller.App")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("-Xmx2048m")
}
