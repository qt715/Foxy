<div align="center">

<img src="versions/26.1.2/src/main/resources/assets/foxy/icon.png" width="256" alt="Foxy">

# Foxy

Loads Voxy and Voxy World Gen V2 on NeoForge.

</div>

Voxy is a Fabric LoD rendering mod and Voxy World Gen V2 is its Fabric companion for passive
chunk generation. Foxy runs both on NeoForge without modifying them: it reads the unmodified
Fabric jars at runtime and bridges the Fabric loader and Fabric API surface they use over to
NeoForge equivalents. Drop Foxy into `mods/` next to the Fabric jars; there are no other steps.

## Requirements

- Minecraft 26.1.2
- NeoForge 26.1.2 (tested on 26.1.2.116)
- Sodium for NeoForge (required by Voxy, tested with 0.9.2)
- Voxy for Minecraft 26.1.x, placed in the mods folder
- Optional: Voxy World Gen V2 for Minecraft 26.1.x, placed in the mods folder

Forgified Fabric API and Sinytra Connector are not needed.

## Supported versions

| Mod | Versions | Status |
| --- | --- | --- |
| Voxy | 0.2.18-beta (26.1.2) | Tested in game (Better MC 6 pack, Sodium 0.9.2, Iris 1.11.4) |
| Voxy | 0.2.16-beta | Tested in game (NeoForge + Sodium 0.8.12 only) |
| Voxy | 0.2.14-alpha, 0.2.15-beta | Same Fabric API surface as 0.2.16; not tested in game |
| Voxy World Gen V2 | 2.2.4 (26.1.2) | Tested in game with both Voxy versions above |

Nothing is pinned to a Voxy or Voxy World Gen version. Foxy translates whatever
`fabric.mod.json` it finds, so newer builds work as long as they stay within the Fabric API
surface listed below.

## How it works

- **Metadata.** Every Fabric-only jar in the mods folder (it has `fabric.mod.json`, no
  `neoforge.mods.toml`, and is not nested inside another mod) gets a generated
  `neoforge.mods.toml`: name, version, authors, icon, license, mixin configs (honouring each
  config's `environment`), `custom` values and non-lifecycle entrypoints as mod properties (this
  is how Sodium finds Voxy's options page via `sodium:config_api_user`), `depends` as required
  dependencies with the lowest declared lower bound (platform ids such as `minecraft`,
  `fabricloader` and `fabric-api` are skipped), and `breaks` as incompatibilities.
- **Entrypoints.** Fabric `main` entrypoints run during common setup and `client` entrypoints
  during client setup, on the main thread, in mod order. `FabricLoader.getEntrypoints` works for
  any key.
- **Bundled libraries.** The jar-in-jar libraries of converted mods are extracted as game
  libraries. Natives for other platforms are skipped, and so is any library whose classes the
  game already provides (Voxy bundles an older lz4-java than Minecraft ships).
- **Access wideners** of converted mods are applied live as a class processor (`accessible`,
  `extendable`, `mutable`), plus a small supplement for NeoForge-only access gaps.
- **Fabric API shim.** Minimal Fabric loader and Fabric API classes so the Fabric bytecode links.
  They live in `com.leclowndu93150.foxy.fabricmc`, and the converted mods' `net.fabricmc`
  references are rewritten to that package in Foxy's temporary copy of the jar. Nothing named
  `net.fabricmc` exists on the game class path, so multi-loader libraries that probe for Fabric
  (MixinConstraints, AsyncParticles, ...) still detect NeoForge, and real Fabric API ports
  cannot clash with the shim:
  - Loader: `FabricLoader`, `ModContainer`, `ModMetadata`, `Version`, `CustomValue`, `EnvType`,
    `ModInitializer`, `ClientModInitializer`.
  - Events: `Event`, `EventFactory.createArrayBacked`.
  - Client commands: `ClientCommandRegistrationCallback`, `ClientCommands`,
    `FabricClientCommandSource` (mixed into `CommandSourceStack`), fired from
    `RegisterClientCommandsEvent`. Voxy's own `/voxy` command is registered this way.
  - Lifecycle: `ServerLifecycleEvents`, `ServerTickEvents`, `ServerChunkEvents`,
    `ClientTickEvents`, `ServerPlayConnectionEvents`, `ClientPlayConnectionEvents`.
  - Networking: `PayloadTypeRegistry` (`serverboundPlay`/`clientboundPlay`),
    `ServerPlayNetworking`, `ClientPlayNetworking`, `PacketSender`. Payload types are registered
    as optional NeoForge play payloads, so servers and clients without the mod still connect.
- **Chunky.** A mixin into Chunky's `NeoForgeWorld` feeds chunks Chunky generates into Voxy.

## Building

```
./gradlew :26.1.2:build
```

The jar is written to `versions/26.1.2/build/libs/`.

## License

MIT. This does not relicense Voxy or Voxy World Gen V2; both keep their own licenses and are not
redistributed.
