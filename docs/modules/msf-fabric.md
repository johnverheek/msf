# Module: msf-fabric

## Purpose

Fabric 1.21.1 mod bridge that resolves MSF data against Minecraft registries and provides in-game commands. Contains zero MSF parsing logic — delegates entirely to msf-core. Published to Modrinth and CurseForge as `msf-fabric:1.0.0+1.21.1`.

## Package Structure
```
dev.msf.fabric
  bridge/       BlockStateBridge, EntityBridge, BiomeBridge
  world/        RegionExtractor, RegionPlacer
  validation/   BlockStateValidator, DataVersionChecker
  command/      MsfCommand (/msf extract, /msf place)
```

## In-Game Commands

| Command | Description |
|---------|-------------|
| `/msf extract` | Extract a world region to an .msf file |
| `/msf place` | Place an .msf file into the world |

File paths resolve relative to the server run directory (`msf-schematics/` folder). `/msf place` uses all layers and the player's current facing direction.

## Key Invariants

- Any Minecraft version change touches only this module
- Registry resolution bridges MSF opaque strings to live game objects
- No MSF binary format logic — all delegated to msf-core

## Target Versions

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.5+ |
| Fabric API | 0.102.0+1.21.1 |
