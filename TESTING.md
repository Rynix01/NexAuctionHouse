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

The normal `check`/`build` lifecycle also creates
`build/libs/NexAuctionHouse-1.0.0-api.jar` and compiles the source under
`src/apiConsumerTest` using only that API JAR plus the Paper compile API. This
prevents public API classes from accidentally depending on plugin internals.

## Live MySQL, MongoDB and Redis integration suite

Run the provider contract tests against local Docker services:

```powershell
.\gradlew.bat externalIntegrationTest --no-daemon `
  "-Dnexah.mongo.uri=mongodb://127.0.0.1:27018" `
  "-Dnexah.redis.host=127.0.0.1" `
  "-Dnexah.redis.port=6379" `
  "-Dnexah.mysql.host=127.0.0.1" `
  "-Dnexah.mysql.port=3307" `
  "-Dnexah.mysql.database=nexah_test" `
  "-Dnexah.mysql.username=nexah" `
  "-Dnexah.mysql.password=nexah_test_password"
```

This task creates a uniquely named temporary MongoDB database, uses an isolated
Redis key/channel prefix, and confines MySQL operations to the configured test
database. Temporary MongoDB data and MySQL table contents are cleaned after each
test. It is excluded from the normal CI task because it needs live services.

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

To start the same Paper harness with MongoDB and Redis cross-server mode:

```powershell
.\scripts\paper-smoke-test.ps1 -UseExternalServices
```

The external Paper run uses the dedicated `nexah_paper_smoke` MongoDB database.

To start the real Paper harness with MySQL:

```powershell
.\scripts\paper-smoke-test.ps1 -UseMySql
```

To verify the real PlaceholderAPI expansion plus PlayerPoints and CoinsEngine
economy providers:

```powershell
.\scripts\paper-smoke-test.ps1 -UseOptionalPlugins
```

This mode downloads PlaceholderAPI 2.12.3 from the official PaperMC Hangar CDN;
PlayerPoints 3.3.5, CoinsEngine 2.6.0 and NightCore 2.9.4 from their official
Modrinth distributions. All downloaded optional JARs are SHA-256 pinned. It
requires both the `nexauction` expansion and `points` economy provider to register,
then runs a disposable probe plugin that verifies placeholder resolution and a
real PlayerPoints deposit/balance/withdraw round trip. CoinsEngine must bind its
configured `money` currency through the reflected API and format a value. The
probe is not included in the production plugin or API JAR.

## External integration coverage

BungeeCord/Velocity and the remaining custom-item or alternate-economy plugins
require their own running services or third-party plugin binaries. They remain
explicit manual/infrastructure tests in `TEST_CHECKLIST.md`; they are not reported
as passing unless those dependencies are installed and the scenarios are executed.
