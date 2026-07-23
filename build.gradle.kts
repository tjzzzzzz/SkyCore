plugins {
    id("net.fabricmc.fabric-loom") version "1.17.16"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

val modId: String = property("mod_id") as String
val modVersion: String = property("mod_version") as String
val mavenGroup: String = property("maven_group") as String
val archivesBaseName: String = property("archives_base_name") as String

val minecraftVersion: String = property("minecraft_version") as String
val loaderVersion: String = property("loader_version") as String
val fabricApiVersion: String = property("fabric_api_version") as String
val fabricKotlinVersion: String = property("fabric_kotlin_version") as String
val ktorVersion: String = property("ktor_version") as String
val yaclVersion: String = property("yacl_version") as String
val clothConfigVersion: String = property("cloth_config_version") as String
val modMenuVersion: String = property("modmenu_version") as String
val hmApiVersion: String = property("hm_api_version") as String
val devAuthVersion: String = property("devauth_version") as String
val catharsisVersion: String = property("catharsis_version") as String

version = "$modVersion+$minecraftVersion"
group = mavenGroup
base.archivesName.set(archivesBaseName)

repositories {
    // bundled jars we host ourselves, e.g. the exact catharsis 26.2 build
    flatDir { dirs("libs") }

    // yacl. quiltmc parsers comes along with it
    maven("https://maven.isxander.dev/releases") {
        name = "Xander"
        content {
            includeGroup("dev.isxander")
            includeGroup("org.quiltmc.parsers")
        }
    }

    // cloth config
    exclusiveContent {
        forRepository { maven("https://maven.shedaniel.me/") { name = "Shedaniel" } }
        filter {
            includeGroup("me.shedaniel")
            includeGroup("me.shedaniel.cloth")
        }
    }

    // hm-api
    maven("https://maven.azureaaron.net/releases") {
        name = "Aaron"
        content { includeGroup("net.azureaaron") }
    }

    // devauth, dev environment only
    exclusiveContent {
        forRepository {
            maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") {
                name = "DevAuth"
            }
        }
        filter { includeGroup("me.djtheredstoner") }
    }

    // modmenu's own maven is down, modrinth serves it instead
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }

    mavenCentral()
}

// everything in here gets nested into the final jar. loom writes synthetic mod
// metadata for plain library jars, so ktor works without pulling in shadow.
val bundled: Configuration = configurations.create("bundled") {
    isTransitive = true
}

configurations.implementation.get().extendsFrom(bundled)

// excludes go on each dependency, not on the configuration. configuration level
// excludes get inherited by implementation and take the kotlin stdlib with them.
fun ExternalModuleDependency.slim() {
    // flk ships the kotlin runtime already
    exclude(group = "org.jetbrains.kotlin")
    // minecraft brings its own slf4j binding
    exclude(group = "org.slf4j")
}

dependencies {
    // 26.x ships unobfuscated, so there is no mappings line and no
    // modImplementation. mods go on the normal configurations.
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // config ui. yacl draws the screens, cloth is compile only so features can
    // interop with mods that expose cloth entries without hard depending on it.
    include(implementation("dev.isxander:yet-another-config-lib:$yaclVersion")!!)
    // hypixel mod api, the primary location source
    include(implementation("net.azureaaron:hm-api:$hmApiVersion")!!)
    // resource pack config mod (MIT). bundled because the legacy skyblock pack
    // declares catharsis:config conditions and vanilla refuses to read the pack
    // without something providing that key. pulled from libs/ as the exact
    // -26.2 build, because modrinth publishes a 26.1 and a 26.2 jar under the
    // same version and the maven coordinate resolves to the wrong (26.1) one.
    include(implementation(":Catharsis:$catharsisVersion-26.2")!!)
    implementation("maven.modrinth:modmenu:$modMenuVersion")
    compileOnly("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    // async http for the hypixel api / auction house polling
    bundled("io.ktor:ktor-client-core:$ktorVersion") { slim() }
    bundled("io.ktor:ktor-client-cio:$ktorVersion") { slim() }
    bundled("io.ktor:ktor-client-content-negotiation:$ktorVersion") { slim() }
    bundled("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion") { slim() }

    // never shipped, only present in runClient so we can log into a real account
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:$devAuthVersion")

    testImplementation("net.fabricmc:fabric-loader-junit:$loaderVersion")
}

// resolve the bundled graph once and hand every artifact to loom's jar-in-jar
afterEvaluate {
    bundled.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
        dependencies.add("include", artifact.moduleVersion.id.toString())
    }
}

loom {
    accessWidenerPath.set(file("src/main/resources/skylite.classtweaker"))

    runs {
        named("client") {
            // hypixel spams the log, keep our own output readable
            vmArgs(
                "-Dskylite.dev=true",
                "-Dmixin.debug.export=true",
                // devauth logs the dev client into a real account. credentials
                // never touch this repo, it runs an oauth flow and caches the
                // token in ~/.devauth
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=main"
            )
        }
        // this mod is client only, no point keeping a server run config around
        remove(getByName("server"))
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "minecraft_version" to minecraftVersion,
        "loader_version" to loaderVersion,
        "fabric_kotlin_version" to fabricKotlinVersion
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_$archivesBaseName" }
    }
}

tasks.test {
    useJUnitPlatform()
}
