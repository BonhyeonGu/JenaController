import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.1.2"
	id("io.spring.dependency-management") version "1.1.2"
	kotlin("jvm") version "1.8.22"
	kotlin("plugin.spring") version "1.8.22"
}

group = "9Bon"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("ch.qos.logback:logback-classic:1.4.9") // Logback Classic
	implementation("ch.qos.logback:logback-core:1.4.9")
    //implementation("org.slf4j:slf4j-api:2.0.7") // SLF4J API
    implementation("org.apache.jena", "apache-jena-libs", "4.9.0")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType(JavaExec::class.java) {
    jvmArgs("-Xms1024m", "-Xmx1024m")
	//export JAVA_OPTS="-Xms64g -Xmx64g
}

tasks.register<JavaExec>("runDebug") {
    group = "application"
    mainClass.set("JenaController.Application")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--spring.profiles.active=dev")
    jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
}