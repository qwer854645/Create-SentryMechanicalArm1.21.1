plugins {
    id("java-library")
    alias(libs.plugins.moddev)
}

group = "euphy.upo.sentrymechanicalarm"
version = (findProperty("mod_version") as String?) ?: "1.3.7"

base {
    archivesName = "sentrymechanicalarm-neoforge-1.21.1"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// Optional integration smoke test; never packaged in a normal build.
if (providers.gradleProperty("sentrySmoke").isPresent || providers.gradleProperty("sentryClientSmoke").isPresent) {
    sourceSets.main { java.srcDir("src/smoke/java") }
}
if (providers.gradleProperty("sentrySmoke").getOrElse("") == "phase3") {
    sourceSets.main { resources.srcDir("src/smoke/resources") }
}

repositories {
    mavenCentral()
    maven {
        name = "NeoForge Maven"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        name = "Create Maven"
        url = uri("https://maven.createmod.net")
    }
    maven {
        name = "Registrate Maven"
        url = uri("https://maven.ithundxr.dev/snapshots")
    }
    maven {
        name = "Tterrag Maven"
        url = uri("https://maven.tterrag.com")
    }
    maven {
        name = "BlameJared Maven (JEI)"
        url = uri("https://maven.blamejared.com")
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        name = "CurseForge"
        url = uri("https://cursemaven.com")
    }
    maven {
        name = "FTB Maven"
        url = uri("https://maven.ftb.dev/releases")
    }
    maven {
        name = "Architectury Maven"
        url = uri("https://maven.architectury.dev/releases")
    }
    exclusiveContent {
        forRepository {
            maven { url = uri("https://maven.ryanhcode.dev/releases") }
        }
        filter { includeGroup("dev.ryanhcode.sable-companion") }
    }
}

neoForge {
    version = "${libs.versions.neoforge.get()}"

    runs {
        configureEach {
            systemProperty("forge.logging.console.level", "debug")
        }

        register("client") {
            client()
            if (providers.gradleProperty("sentryClientSmoke").isPresent) {
                systemProperty("sentry.clientSmoke", "true")
                programArgument("--quickPlaySingleplayer")
                programArgument("phase3-validation")
            }
        }
        register("server") {
            server()
            programArgument("--nogui")
            if (providers.gradleProperty("sentrySmoke").isPresent) {
                val suite = providers.gradleProperty("sentrySmoke").get()
                systemProperty("sentry.smoke", if (suite in listOf("phase2", "phase3", "compat", "train", "depot")) suite else "true")
                if (providers.gradleProperty("trainDepotSmoke").isPresent) systemProperty("sentry.trainDepot","true")
            }
        }
        register("data") {
            data()
        }
    }

    mods {
        register("sentrymechanicalarm") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // Optional local Sable runtime for integration verification; not bundled or required.
    if (providers.gradleProperty("sableJar").isPresent) runtimeOnly(files(providers.gradleProperty("sableJar").get()))
    // NeoForge
    implementation(libs.neoforge)

    // Create - slim jar without transitive deps to avoid missing Curios/CC/FTB/Architectury
    implementation(libs.create) {
        isTransitive = false
    }

    // Registrate (Create's dependency, types exposed through Create's API)
    compileOnly("com.tterrag.registrate:Registrate:MC1.21-1.3.0+67")

    // Ponder
    implementation(libs.ponder)

    // Flywheel
    implementation(libs.flywheel.api)
    runtimeOnly(libs.flywheel.runtime)

    // JEI
    compileOnly(libs.jei.api)
    runtimeOnly(libs.jei.runtime)

    // EMI - recipe viewer
    compileOnly(libs.emi)

    // LuaJ - for script support, bundled via jarJar
    compileOnly("org.luaj:luaj-jse:3.0.1")

    // Official TaCZ 1.2.0, NeoForge 1.21.1 (CurseForge file 9454361).
    implementation(libs.tacz)

    // Sable Companion — multi-loader compatibility library, bundled via jarJar
    implementation("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0")
    jarJar("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Specification-Title"] = "Sentry Mechanical Arm"
        attributes["Specification-Vendor"] = "Euphy"
        attributes["Specification-Version"] = "1"
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Vendor"] = "Euphy"
    }
}

tasks.named<ProcessResources>("processResources") {
    val modVersion = project.version.toString()
    inputs.property("mod_version", modVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("mod_version" to modVersion)
    }
}
