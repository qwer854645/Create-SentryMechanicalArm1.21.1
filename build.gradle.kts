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
    version = providers.gradleProperty("devNeoForgeVersion").getOrElse(libs.versions.neoforge.get())

    runs {
        configureEach {
            systemProperty("forge.logging.console.level", "debug")
            if (providers.gradleProperty("devRunDirectory").isPresent) {
                gameDirectory.set(file(providers.gradleProperty("devRunDirectory").get()))
            }
        }

        register("client") {
            client()
            if (providers.gradleProperty("sentryClientSmoke").isPresent) {
                systemProperty("sentry.clientSmoke", "true")
                if (providers.gradleProperty("casingClientSmoke").isPresent) systemProperty("sentry.casingClientSmoke", "true")
                if (providers.gradleProperty("ponderSmoke").isPresent) systemProperty("sentry.ponderSmoke", "true")
                if (providers.gradleProperty("gunpackClientSmoke").isPresent) systemProperty("sentry.gunpackClientSmoke", "true")
                if (providers.gradleProperty("sentryClientReloadSmoke").isPresent) systemProperty("sentry.clientReloadSmoke", "true")
                programArgument("--quickPlaySingleplayer")
                programArgument(providers.gradleProperty("clientSmokeWorld").getOrElse("phase3-validation"))
            }
        }
        register("server") {
            server()
            programArgument("--nogui")
            if (providers.gradleProperty("sentrySmoke").isPresent) {
                val suite = providers.gradleProperty("sentrySmoke").get()
                if (providers.gradleProperty("casingIntegrationSmoke").isPresent) systemProperty("sentry.casingIntegrationSmoke", "true")
                if (providers.gradleProperty("casingSmoke").isPresent) {
                    systemProperty("sentry.casingSmoke", "true")
                    programArgument("--universe")
                    programArgument(file("../casing-worlds").absolutePath)
                    programArgument("--world")
                    val casingWorld = providers.gradleProperty("casingWorld").getOrElse("alpha11-"+System.currentTimeMillis())
                    require(casingWorld.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")))
                    programArgument(casingWorld)
                }
                systemProperty("sentry.smoke", if (suite in listOf("phase2", "phase3", "compat", "train", "depot", "restart", "gunpacks", "randompacks", "casingconfig", "reloading", "release")) suite else "true")
                if (providers.gradleProperty("trainDepotSmoke").isPresent) systemProperty("sentry.trainDepot","true")
                if (suite == "casingconfig" || suite == "reloading" || suite == "release") {
                    programArgument("--universe")
                    programArgument(file("../casing-config-worlds").absolutePath)
                    programArgument("--world")
                    programArgument("validation-"+System.currentTimeMillis())
                }
                if (suite == "randompacks") {
                    val namespace=providers.gradleProperty("randomNamespace").get()
                    require(namespace.matches(Regex("[a-z0-9_]+")))
                    systemProperty("sentry.randomNamespace",namespace)
                    programArgument("--universe")
                    programArgument(file("../randompack-worlds").absolutePath)
                    programArgument("--world")
                    programArgument(namespace+"-"+System.currentTimeMillis())
                }
                if (suite == "gunpacks") {
                    programArgument("--universe")
                    programArgument(file("../gunpack-worlds").absolutePath)
                    programArgument("--world")
                    programArgument("ars-applied-"+System.currentTimeMillis())
                }
                if (suite == "restart") {
                    systemProperty("sentry.restartPhase",providers.gradleProperty("restartPhase").getOrElse("prepare"))
                    programArgument("--universe")
                    programArgument(file("../restart-worlds").absolutePath)
                    programArgument("--world")
                    val validationWorld = providers.gradleProperty("restartWorld").getOrElse("restart-alpha8-validation")
                    require(validationWorld.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))) { "Use a simple restart world name without paths" }
                    programArgument(validationWorld)
                    systemProperty("sentry.trainDepot","true")
                }
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
    if (!providers.gradleProperty("withoutJei").isPresent) {
        val devJeiJar = providers.gradleProperty("devJeiJar")
        if (devJeiJar.isPresent && file(devJeiJar.get()).isFile) runtimeOnly(files(devJeiJar.get()))
        else runtimeOnly(libs.jei.runtime)
    }

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
    from("LICENSE")
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
