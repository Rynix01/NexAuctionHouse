# Testing NexAuctionHouse

The repository has two complementary test layers.

## Automated feature suite

Run all unit and integration tests with Java 21:

```powershell
.\gradlew.bat clean test --no-daemon
```

The suite starts the plugin in MockBukkit, registers an in-memory Vault economy,
creates a temporary SQLite database, simulates players and inventories, and tests
market, GUI, command, delivery, migration, API and concurrency behavior.

The HTML report is generated at `build/reports/tests/test/index.html`.

## Real Paper smoke test

Run the reproducible Paper 1.21.4 startup environment on Windows:

```powershell
.\scripts\paper-smoke-test.ps1
```

The script builds the plugin, downloads an official stable Paper build plus the
official Vault 1.7.3 and EssentialsX 2.21.2 releases, accepts the EULA for this
temporary local server, starts it without a GUI, verifies Vault/economy, SQLite
and NexAuctionHouse startup, then terminates the disposable test process. All runtime files stay under
the ignored `build/paper-smoke-1.21.4` directory.

## External integration coverage

MySQL, MongoDB, Redis, BungeeCord/Velocity, PlaceholderAPI and custom-item or
alternate-economy plugins require their own running services or third-party
plugin binaries. They remain explicit manual/infrastructure tests in
`TEST_CHECKLIST.md`; they are not reported as passing unless those dependencies
are installed and the scenarios are executed.
