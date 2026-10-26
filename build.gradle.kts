plugins {
    `java-library`
}

group = "mc.gupe.revive"
version = "1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper pulls in Adventure, Gson, Guava and the JetBrains annotations transitively.
    // The oldest supported Paper, so the jar also runs on 26.2 and 26.3 (compilar.sh does the same).
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    // Optional (softdepend): only used to show the player's rank.
    compileOnly("net.luckperms:api:5.4")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("UltraRevive")
    archiveVersion.set("1.0")
}
