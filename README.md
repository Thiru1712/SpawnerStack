# SpawnerVault

**SpawnerVault** is a Paper plugin for virtual vanilla spawner production,
stacking, storage, and collection.

**Development team:** ForgeLabs  
**Company/brand:** Rypera  
**Branding:** Powered by Rypera

## Supported Platform

- Paper 26.2
- Java 25

## Features

- Every normal `minecraft:spawner` is adopted and managed by SpawnerVault, including spawners whose mob type was assigned by commands or other plugins.
- `minecraft:trial_spawner` is explicitly excluded and remains vanilla.
- The built-in vanilla-style production profiles currently cover:
  - Zombie
  - Skeleton
  - Spider
  - Cave Spider
  - Silverfish
  - Blaze
  - Magma Cube
- Other normal spawner mob types are still fully managed by SpawnerVault; they produce no invented drops until a production profile is configured.
- Virtual production without requiring production mobs.
- Shared production scheduling design for low overhead.
- Configurable production cooldown.
- Spawner stack limit of 64.
- Production scales with stack size.
- Virtual storage scales at **36 slots per spawner**.
- Normal Minecraft item stack rules are preserved, including non-stackable
  items consuming individual virtual slots.
- Paginated storage UI intended to be mobile/Geyser friendly.
- Separate virtual XP storage.
- Persistent spawner data.
- Silk Touch recovery using supported pickaxe tiers.
- Stacked spawners are represented as one stacked spawner item when recovered.
- Administration files are separated into `config.yml`, `spawners.yml`,
  `prices.yml`, and `messages.yml`.

## Installation

1. Download the JAR from the official project release.
2. Put `SpawnerVault-1.1.2.jar` in `plugins/`.
3. Start or restart the Paper server.
4. Check the console for a successful SpawnerVault enable message.
5. Review the generated plugin configuration.

See [INSTALL.md](INSTALL.md) for additional details.

## Admin Commands

SpawnerVault has no player-facing commands. Its command interface is restricted to `spawnervault.admin` (default: server operators).

- `/sv reload` — reload SpawnerVault configuration.
- `/sv give <player> <type> [amount]` — give a SpawnerVault-managed normal spawner stack.
- `/sv list` — open the paginated placed-spawner management GUI.

### Admin placed-spawner GUI

`/sv list` opens a chest-style GUI containing placed SpawnerVault spawners. Clicking one opens its details, including:

- spawner type
- stack size
- player who placed it
- placement date/time
- world and XYZ coordinates
- teleport button
- button to open the normal SpawnerVault GUI for management

The placed-spawner records are persisted across restarts and survive Silk Touch recovery/replacement.

## Configuration

### `config.yml`

General plugin behavior and performance-related settings.

### `spawners.yml`

Spawner-specific definitions and settings.

### `prices.yml`

Administrative item pricing configuration. Prices are applied when **Sell Loot**
is used. Player balances are not stored by SpawnerVault.

### `messages.yml`

Player-facing messages.

## Economy Integration

SpawnerVault does not provide or store its own player economy.

The optional **Sell Loot** feature integrates with server economies through the
[Vault API](https://github.com/MilkBowl/VaultAPI). Install:

1. SpawnerVault
2. Vault
3. A Vault-compatible economy provider

SpawnerVault automatically detects the active economy provider. When a sale
succeeds, the money is deposited into the player's real server balance. Loot
is removed only after the economy transaction succeeds.

If Vault or a compatible economy provider is unavailable, SpawnerVault remains
functional and only the Sell Loot feature is unavailable.

## Building From Source

The project uses Gradle and Java 25.

```bash
gradle clean build --no-daemon
```

The build output is `build/libs/SpawnerVault-1.1.2.jar`.

## Compatibility

SpawnerVault is developed for Paper. Compatibility with other Bukkit-derived
servers is not guaranteed unless explicitly tested and documented.

## Support

When reporting a problem, include the SpawnerVault version, Paper version,
Java version, relevant console output, and reproducible steps.

## License

SpawnerVault is licensed under the MIT License. See [LICENSE](LICENSE).

## Third-Party Notices

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Disclaimer

SpawnerVault is an independent community plugin and is not affiliated with,
endorsed by, or an official product of Mojang Studios or Microsoft.


## Spawner adoption

Every normal `minecraft:spawner` is adopted by SpawnerVault, including spawners created by commands or other plugins, and its mob type is preserved. `minecraft:trial_spawner` is deliberately excluded and remains fully vanilla.
