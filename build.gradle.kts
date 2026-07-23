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

    flatDir { dirs("libs") }

    maven("https://maven.isxander.dev/releases") {
        name = "Xander"
        content {
            includeGroup("dev.isxander")
            includeGroup("org.quiltmc.parsers")
        }
    }

    exclusiveContent {
        forRepository { maven("https://maven.shedaniel.me/") { name = "Shedaniel" } }
        filter {
            includeGroup("me.shedaniel")
            includeGroup("me.shedaniel.cloth")
        }
    }

    maven("https://maven.azureaaron.net/releases") {
        name = "Aaron"
        content { includeGroup("net.azureaaron") }
    }

    exclusiveContent {
        forRepository {
            maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") {
                name = "DevAuth"
            }
        }
        filter { includeGroup("me.djtheredstoner") }
    }

    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }

    mavenCentral()
}

val bundled: Configuration = configurations.create("bundled") {
    isTransitive = true
}

configurations.implementation.get().extendsFrom(bundled)

fun ExternalModuleDependency.slim() {

    exclude(group = "org.jetbrains.kotlin")

    exclude(group = "org.slf4j")
}

dependencies {

    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    include(implementation("dev.isxander:yet-another-config-lib:$yaclVersion")!!)

    include(implementation("net.azureaaron:hm-api:$hmApiVersion")!!)

    include(implementation(":Catharsis:$catharsisVersion-26.2")!!)
    implementation("maven.modrinth:modmenu:$modMenuVersion")
    compileOnly("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    bundled("io.ktor:ktor-client-core:$ktorVersion") { slim() }
    bundled("io.ktor:ktor-client-cio:$ktorVersion") { slim() }
    bundled("io.ktor:ktor-client-content-negotiation:$ktorVersion") { slim() }
    bundled("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion") { slim() }

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:$devAuthVersion")

    testImplementation("net.fabricmc:fabric-loader-junit:$loaderVersion")
}

afterEvaluate {
    bundled.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
        dependencies.add("include", artifact.moduleVersion.id.toString())
    }
}

loom {
    accessWidenerPath.set(file("src/main/resources/skycore.classtweaker"))

    runs {
        named("client") {

            vmArgs(
                "-Dskycore.dev=true",
                "-Dmixin.debug.export=true",

                "-Ddevauth.enabled=true",
                "-Ddevauth.account=main"
            )
        }

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
