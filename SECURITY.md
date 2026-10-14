# Security Policy

## Supported Version

The latest published release is the primary supported version.

## Reporting a Vulnerability

Please do not publicly disclose a suspected security vulnerability before it
has been reviewed.

Report security issues privately to the SpawnerVault maintainers through the
project's official support or repository contact channel. Include:

- SpawnerVault version
- Paper version
- Java version
- Steps to reproduce
- Relevant console errors or stack traces
- Whether the issue can cause item duplication, item loss, permission bypass,
  or server instability

Do not include passwords, API keys, player tokens, or other secrets in a report.

## Scope

Particular attention should be given to:

- Spawner placement/breaking and Silk Touch validation
- Stacked-spawner item serialization
- Virtual storage persistence
- Loot/XP collection and duplication
- Permission enforcement
- Data corruption after restart or crash
