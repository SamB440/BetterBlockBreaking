plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }

    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/groups/public/")
    }

    maven {
        url = uri("https://repo.md-5.net/content/groups/public/")
    }

    maven {
        url = uri("https://ci.frostcast.net/plugin/repository/everything")
    }

    maven {
        url = uri("https://repo.bukkit.org/content/groups/public/")
    }

    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("com.github.slimefun:Slimefun4:RC-21")
    compileOnly("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:4.6.0")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.5-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:21.0.1")
}

var ver by extra("7.0.0")
var versuffix by extra("-SNAPSHOT")
val versionsuffix: String? by project
if (versionsuffix != null) {
    versuffix = "-$versionsuffix"
}
version = ver + versuffix

group = "com.github.cheesesoftware"
description = "BetterBlockBreaking"

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}