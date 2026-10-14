# Contributing to SpawnerVault

Thank you for contributing to SpawnerVault.

## Development

- Target platform: Paper
- Current target: Paper 26.2
- Current Java toolchain: Java 25
- Build system: Gradle

Run a clean build before submitting changes:

```bash
gradle clean build --no-daemon
```

## Pull Requests

Please keep pull requests focused and describe:

1. What changed
2. Why it changed
3. How it was tested
4. Any configuration or migration impact

For gameplay changes, test at minimum:

- placing and breaking spawners
- stacked spawners
- restart persistence
- virtual storage and XP
- invalid/valid Silk Touch tools
- Paper 26.2 startup and shutdown

Do not commit secrets, populated server configuration, player data, API keys,
or private credentials.

## Code of Conduct

Contributors are expected to communicate professionally and avoid harassment,
discrimination, malicious code, or intentionally harmful changes.
