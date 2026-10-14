# Changelog

## 1.1.3
- Fixed GUI item movement/dragging with explicit click, drag, and creative interaction cancellation.
- Fixed storage navigation and collection handling.
- Removed the unused Upgrade button.
- `/sv list` now shows only spawners marked as player-placed; automatically adopted world spawners are hidden.
- Same-type SpawnerVault spawners can stack directly when placed against/onto another managed spawner, up to the configured maximum.
- Added a branded SpawnerVault main GUI layout.

## 1.1.2
- Fixed BlockPlaceEvent null handling.
- Every normal `minecraft:spawner` is adopted by SpawnerVault regardless of mob type.
- Trial Spawners are explicitly excluded.
- Added periodic loaded-chunk adoption for command/plugin-created normal spawners.
- Removed the hard 7-type adoption restriction.
- Kept the seven configured vanilla loot profiles; unconfigured mob types remain managed but produce no invented drops until a profile is configured.
- Preserved Silk Touch recovery rules and virtual-only spawning.

## 1.1.1
- Fixed BlockPlaceEvent NullPointerException when a spawner type is absent.
- Normal `minecraft:spawner` blocks are automatically adopted by SpawnerVault.
- Trial Spawners remain untouched.
- Existing normal spawners are adopted on startup and chunk load.
- Added persistent managed-spawner block metadata.

## 1.1.0
- Added optional Vault economy integration for Sell Loot.
- Removed SpawnerVault's internal player balance storage.
- Added admin placed-spawner management GUI under `/sv list`.
- Added persistent owner, placement timestamp, location, teleport, and management details.
- Restricted SpawnerVault commands to `spawnervault.admin`.

## 1.1.4
- Protected managed spawners from creeper/TNT/block explosions.
- Fixed GUI session replacement so navigation controls remain protected.
- Added drop/swap protection while SpawnerVault GUIs are open.
- Reworked the main menu into a 54-slot SpawnerVault-style interface.
- Removed the upgrade control completely.
- Added visible production cooldown and next-cycle timer.
- Default production cooldown is now 30 seconds, configurable up to 60 seconds.
- Preserved same-type stacking by right-clicking the existing spawner with a SpawnerVault spawner item; no proximity auto-merge.
