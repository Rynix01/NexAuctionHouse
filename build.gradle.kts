plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.3-beta-14")

    // The other optional plugins are accessed through Bukkit metadata/PDC or
    // reflection. Keep them as soft dependencies instead of bundling their APIs.

    // MongoDB driver (shaded into the plugin jar)
    implementation("org.mongodb:mongodb-driver-sync:5.1.0")

    // Redis client (shaded into the plugin jar)
    implementation("redis.clients:jedis:5.2.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "com.google.code.gson", module = "gson")
    }

    // Used directly by cross-server messages and Discord webhooks.
    implementation("com.google.code.gson:gson:2.11.0") {
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.40.2")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1")
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation("com.mysql:mysql-connector-j:9.1.0")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
        relocate("com.google.gson", "net.nexuby.nexauctionhouse.libs.gson")
        exclude("org/slf4j/**")
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

    register("verifyShadedJar") {
        dependsOn(shadowJar)
        doLast {
            val jarFile = shadowJar.get().archiveFile.get().asFile
            val forbidden = zipTree(jarFile).matching {
                include("org/slf4j/**")
                include("com/google/gson/**")
            }.files
            check(forbidden.isEmpty()) {
                "Unisolated runtime dependency classes found in ${jarFile.name}: $forbidden"
            }

            val relocatedGson = zipTree(jarFile).matching {
                include("net/nexuby/nexauctionhouse/libs/gson/Gson.class")
            }.files
            check(relocatedGson.size == 1) { "Relocated Gson runtime is missing from ${jarFile.name}" }
        }
    }

    check {
        dependsOn("verifyShadedJar")
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform {
            excludeTags("external")
        }
    }

    register<Test>("externalIntegrationTest") {
        description = "Runs MongoDB and Redis integration tests against local services."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform {
            includeTags("external")
        }
        systemProperty("nexah.mongo.uri",
                providers.systemProperty("nexah.mongo.uri").orElse("mongodb://127.0.0.1:27018").get())
        systemProperty("nexah.redis.host",
                providers.systemProperty("nexah.redis.host").orElse("127.0.0.1").get())
        systemProperty("nexah.redis.port",
                providers.systemProperty("nexah.redis.port").orElse("6379").get())
        systemProperty("nexah.redis.password",
                providers.systemProperty("nexah.redis.password").orElse("").get())
        systemProperty("nexah.mysql.host",
                providers.systemProperty("nexah.mysql.host").orElse("127.0.0.1").get())
        systemProperty("nexah.mysql.port",
                providers.systemProperty("nexah.mysql.port").orElse("3307").get())
        systemProperty("nexah.mysql.database",
                providers.systemProperty("nexah.mysql.database").orElse("nexah_test").get())
        systemProperty("nexah.mysql.username",
                providers.systemProperty("nexah.mysql.username").orElse("nexah").get())
        systemProperty("nexah.mysql.password",
                providers.systemProperty("nexah.mysql.password").orElse("nexah_test_password").get())
        shouldRunAfter(test)
    }
}
