plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "xyz.neontonight"
description = "Folia-safe fixed-price auction house with MongoDB and Redis sync."

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
    implementation("io.lettuce:lettuce-core:6.5.0.RELEASE")
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.mongodb", "xyz.neontonight.auction.libs.mongodb")
    relocate("io.lettuce", "xyz.neontonight.auction.libs.lettuce")
    relocate("com.google.gson", "xyz.neontonight.auction.libs.gson")
    relocate("reactor", "xyz.neontonight.auction.libs.reactor")
    relocate("org.reactivestreams", "xyz.neontonight.auction.libs.reactivestreams")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
