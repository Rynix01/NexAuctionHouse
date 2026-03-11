plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "net.nexuby"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://mvn.lumine.io/repository/maven-public/") // MythicMobs, ModelEngine
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.3-beta-14")
    compileOnly("io.lumine:Mythic-Dist:5.7.2")
    compileOnly("io.lumine:MythicCrucible-Dist:2.1.0")

    // MongoDB driver (shaded into the plugin jar)
    implementation("org.mongodb:mongodb-driver-sync:5.1.0")

    // Redis client (shaded into the plugin jar)
    implementation("redis.clients:jedis:5.2.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("NexAuctionHouse-${project.version}.jar")
        relocate("com.mongodb", "net.nexuby.nexauctionhouse.libs.mongodb")
        relocate("org.bson", "net.nexuby.nexauctionhouse.libs.bson")
        relocate("redis.clients", "net.nexuby.nexauctionhouse.libs.redis")
        relocate("org.apache.commons.pool2", "net.nexuby.nexauctionhouse.libs.pool2")
        relocate("org.json", "net.nexuby.nexauctionhouse.libs.json")
        exclude("META-INF/native-image/**")
    }

    register<Jar>("apiJar") {
        archiveClassifier.set("api")
        archiveFileName.set("NexAuctionHouse-${project.version}-api.jar")
        from(sourceSets.main.get().output) {
            include("net/nexuby/nexauctionhouse/api/**")
            include("net/nexuby/nexauctionhouse/model/AuctionItem.class")
            include("net/nexuby/nexauctionhouse/model/AuctionStatus.class")
            include("net/nexuby/nexauctionhouse/model/AuctionType.class")
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
