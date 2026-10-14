# SpawnerVault Installation

## Requirements

- Paper 26.2 or a compatible supported Paper build
- Java 25
- No external runtime plugin dependency is required by the current 1.1.0 JAR

## Installation

1. Download `SpawnerVault-1.1.2.jar`.
2. Place the JAR in the server's `plugins/` directory.
3. Start or restart the server.
4. Confirm that SpawnerVault enables without errors.
5. Review the generated/configured files under `plugins/SpawnerVault/`.

## Configuration Files

- `config.yml` — general plugin settings
- `spawners.yml` — supported spawner definitions/settings
- `prices.yml` — configurable item sell prices
- `messages.yml` — configurable messages

## Important

Back up your server before installing or upgrading a plugin that stores
persistent spawner data.

For PowerupStack, the plugin must be installed through a method permitted by
the selected hosting plan. Do not rename the JAR or upload the source ZIP as a
plugin.

## Administrator access

SpawnerVault has no player-facing commands. Server operators/admins with `spawnervault.admin` can use:

```text
/sv reload
/sv give <player> <type> [amount]
/sv list
```

`/sv list` opens the placed-spawner management GUI.
