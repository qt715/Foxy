plugins {
    id("dev.prism")
}

group = "com.leclowndu93150"
version = "1.1.0"

prism {
    metadata {
        modId = "foxy"
        name = "Foxy"
        description = "Loads Voxy, Voxy World Gen V2 and other unmodified Fabric mods on NeoForge by bridging their Fabric entrypoints and APIs."
        license = "MIT"
        author("leclowndu93150")
    }

    modrinthMaven()
    maven("NeoForged", "https://maven.neoforged.net/releases")

    version("26.1.2") {
        neoforge {
            loaderVersion = "26.1.2.48-beta"
            loaderVersionRange = "[4,)"

            dependencies {
                // Development runtime only: Foxy compiles against none of these.
                runtimeOnly("maven.modrinth:sodium:mc26.1.2-0.9.2-neoforge")
                runtimeOnly("maven.modrinth:voxy:Zt3LPI0b") // 0.2.18-beta for 26.1.2
                runtimeOnly("maven.modrinth:voxy-worldgen:tRiQxKkc") // Voxy World Gen V2 2.2.4-26.1.2

                // Chunky auto-ingest mixin references Voxy's VoxelIngestService.
                compileOnly("maven.modrinth:voxy:0.2.16-beta")
                compileOnly("maven.modrinth:chunky:hEXc6nbN")
                compileOnly("net.neoforged.fancymodloader:loader:11.0.13")
            }
        }
    }

}
