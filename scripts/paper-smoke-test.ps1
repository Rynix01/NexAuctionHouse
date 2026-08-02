param(
    [string]$MinecraftVersion = "1.21.4",
    [int]$TimeoutSeconds = 180,
    [switch]$UseExternalServices,
    [switch]$UseMySql,
    [switch]$UseOptionalPlugins,
    [string]$MongoUri = "mongodb://127.0.0.1:27018",
    [string]$MongoDatabase = "nexah_paper_smoke",
    [string]$RedisHost = "127.0.0.1",
    [int]$RedisPort = 6379,
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3307,
    [string]$MySqlDatabase = "nexah_test",
    [string]$MySqlUsername = "nexah",
    [string]$MySqlPassword = "nexah_test_password"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
if (@($UseExternalServices, $UseMySql, $UseOptionalPlugins).Where({ $_ }).Count -gt 1) {
    throw "UseExternalServices, UseMySql and UseOptionalPlugins are separate smoke modes; select only one."
}
$runtimeSuffix = if ($UseOptionalPlugins) {
    "$MinecraftVersion-optional"
} elseif ($UseMySql) {
    "$MinecraftVersion-mysql"
} elseif ($UseExternalServices) {
    "$MinecraftVersion-external"
} else {
    $MinecraftVersion
}
$runtimeRoot = Join-Path $repositoryRoot "build\paper-smoke-$runtimeSuffix"
$pluginsRoot = Join-Path $runtimeRoot "plugins"
$paperJar = Join-Path $runtimeRoot "paper.jar"
$userAgent = "NexAuctionHouse-TestHarness/1.0 (https://github.com/Rynix01/NexAuctionHouse)"

function Download-FileIfMissing {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Destination,
        [string]$ExpectedSha256 = ""
    )

    if (Test-Path -LiteralPath $Destination) {
        if ($ExpectedSha256 -and (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash -ne $ExpectedSha256) {
            throw "Checksum mismatch for existing file $Destination"
        }
        return
    }

    Write-Host "Downloading $([IO.Path]::GetFileName($Destination))..."
    Invoke-WebRequest -Uri $Url -OutFile $Destination -Headers @{ "User-Agent" = $userAgent }
    if ($ExpectedSha256 -and (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash -ne $ExpectedSha256) {
        throw "Checksum mismatch for downloaded file $Destination"
    }
}

Write-Host "Building NexAuctionHouse..."
$gradleTasks = @("shadowJar")
if ($UseOptionalPlugins) {
    $gradleTasks += "optionalSmokeProbeJar"
}
& (Join-Path $repositoryRoot "gradlew.bat") @gradleTasks --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed."
}

New-Item -ItemType Directory -Force -Path $pluginsRoot | Out-Null

$paperBuilds = Invoke-RestMethod `
    -Uri "https://fill.papermc.io/v3/projects/paper/versions/$MinecraftVersion/builds" `
    -Headers @{ "User-Agent" = $userAgent }
$stableBuild = @($paperBuilds | Where-Object { $_.channel -eq "STABLE" }) | Select-Object -First 1
if ($null -eq $stableBuild) {
    throw "No stable Paper build exists for Minecraft $MinecraftVersion."
}
$paperDownload = $stableBuild.downloads.'server:default'
Download-FileIfMissing -Url $paperDownload.url -Destination $paperJar

$vaultJar = Join-Path $pluginsRoot "Vault.jar"
$essentialsJar = Join-Path $pluginsRoot "EssentialsX.jar"
$pluginJar = Join-Path $pluginsRoot "NexAuctionHouse.jar"
Download-FileIfMissing `
    -Url "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" `
    -Destination $vaultJar
Download-FileIfMissing `
    -Url "https://github.com/EssentialsX/Essentials/releases/download/2.21.2/EssentialsX-2.21.2.jar" `
    -Destination $essentialsJar
if ($UseOptionalPlugins) {
    Download-FileIfMissing `
        -Url "https://hangarcdn.papermc.io/plugins/HelpChat/PlaceholderAPI/versions/2.12.3/PAPER/PlaceholderAPI-2.12.3.jar" `
        -Destination (Join-Path $pluginsRoot "PlaceholderAPI.jar") `
        -ExpectedSha256 "FDE03259F5AF6938F3C33EEB4D814000A1ADABF1D2304CE14970BE81F609A437"
    Download-FileIfMissing `
        -Url "https://cdn.modrinth.com/data/bPX4jcVd/versions/Jgohk2ua/PlayerPoints-3.3.5.jar" `
        -Destination (Join-Path $pluginsRoot "PlayerPoints.jar") `
        -ExpectedSha256 "4B15BA1654463A3F7363DC3D9B7330D5675B800D55EFE70631B32EED03669AC3"
    Download-FileIfMissing `
        -Url "https://cdn.modrinth.com/data/Y4NRwMW5/versions/1uVZSFRI/nightcore-2.9.4.jar" `
        -Destination (Join-Path $pluginsRoot "nightcore.jar") `
        -ExpectedSha256 "94FE8E46C8FCC8022C93E84A7CF541B452135534423AC6232AB2369D64EE9645"
    Download-FileIfMissing `
        -Url "https://cdn.modrinth.com/data/r0FB9U1e/versions/G0BJaAkm/CoinsEngine-2.6.0.jar" `
        -Destination (Join-Path $pluginsRoot "CoinsEngine.jar") `
        -ExpectedSha256 "9319808FBD1AD6C24AC4D5C75949F839474C2926829E0EC279C7404E7199B864"
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "build\test-plugins\NexAuctionHouse-optional-smoke-probe.jar") `
        -Destination (Join-Path $pluginsRoot "NexAuctionHouse-optional-smoke-probe.jar") -Force
}
Copy-Item -LiteralPath (Join-Path $repositoryRoot "build\libs\NexAuctionHouse-1.0.0.jar") `
    -Destination $pluginJar -Force

if ($UseExternalServices -or $UseMySql -or $UseOptionalPlugins) {
    $pluginDataRoot = Join-Path $pluginsRoot "NexAuctionHouse"
    New-Item -ItemType Directory -Force -Path $pluginDataRoot | Out-Null
    $configText = Get-Content -LiteralPath (Join-Path $repositoryRoot "src\main\resources\config.yml") -Raw
    if ($UseOptionalPlugins) {
        $configText = $configText -replace '(?m)(^    playerpoints:\r?\n      enabled:) false', '$1 true'
        $configText = $configText -replace '(?m)(^    coinsengine:\r?\n      enabled:) false', '$1 true'
        $configText = [regex]::Replace(
            $configText,
            '(?ms)(^    coinsengine:.*?^      plugin-currency:) "gems"',
            '$1 "money"')
    } elseif ($UseMySql) {
        $configText = $configText.Replace("  type: sqlite", "  type: mysql")
        $configText = $configText.Replace("    host: localhost", "    host: $MySqlHost")
        $configText = $configText.Replace("    port: 3306", "    port: $MySqlPort")
        $configText = $configText.Replace("    database: nexauctionhouse", "    database: $MySqlDatabase")
        $configText = $configText.Replace("    username: root", "    username: $MySqlUsername")
        $configText = $configText.Replace("    password: ''", "    password: '$MySqlPassword'")
    } else {
        $configText = $configText.Replace("  type: sqlite", "  type: mongodb")
        $configText = $configText.Replace('connection-string: "mongodb://localhost:27017"', "connection-string: `"$MongoUri`"")
        $configText = $configText.Replace("    database: nexauctionhouse", "    database: $MongoDatabase")
        $configText = $configText.Replace("  enabled: false`r`n`r`n  # Unique identifier for this server instance", "  enabled: true`r`n`r`n  # Unique identifier for this server instance")
        $configText = $configText.Replace("    host: localhost", "    host: $RedisHost")
        $configText = $configText.Replace("    port: 6379", "    port: $RedisPort")
        $configText = $configText.Replace("    message-secret: ''", "    message-secret: 'nexah-paper-smoke-secret-at-least-32-characters'")
        $configText = $configText.Replace("    database: 0", "    database: 15")
        $configText = $configText.Replace('    channel-prefix: "nexah"', '    channel-prefix: "nexah-paper-smoke"')
    }
    Set-Content -LiteralPath (Join-Path $pluginDataRoot "config.yml") -Encoding UTF8 -Value $configText
}

if ($UseOptionalPlugins) {
    foreach ($engineFile in @(
        (Join-Path $pluginsRoot "nightcore\engine.yml"),
        (Join-Path $pluginsRoot "CoinsEngine\engine.yml")
    )) {
        if (Test-Path -LiteralPath $engineFile) {
            $engineText = Get-Content -LiteralPath $engineFile -Raw
            $engineText = $engineText -replace '(?m)^  Language: .+$', '  Language: en'
            Set-Content -LiteralPath $engineFile -Encoding UTF8 -Value $engineText
        }
    }
}

Set-Content -LiteralPath (Join-Path $runtimeRoot "eula.txt") -Encoding ASCII -Value "eula=true"
Set-Content -LiteralPath (Join-Path $runtimeRoot "server.properties") -Encoding ASCII -Value @(
    "server-port=0"
    "online-mode=false"
    "enable-query=false"
    "enable-rcon=false"
    "spawn-protection=0"
    "max-players=2"
    "view-distance=2"
    "simulation-distance=2"
    "motd=NexAuctionHouse smoke test"
)

$javaCandidates = @(
    Get-ChildItem -Path (Join-Path $env:ProgramFiles "Java\*\bin\java.exe") -ErrorAction SilentlyContinue
)
$java21 = $javaCandidates | Where-Object { $_.FullName -match "\\jdk-21(?:\\|[.-])" } | Select-Object -First 1
if ($null -ne $java21) {
    $java = $java21.FullName
} elseif ($javaCandidates.Count -gt 0) {
    $java = $javaCandidates[0].FullName
} else {
    $java = (Get-Command java -ErrorAction Stop).Source
}
Write-Host "Starting Paper $MinecraftVersion smoke server..."
$stdoutFile = Join-Path $runtimeRoot "console-output.log"
$stderrFile = Join-Path $runtimeRoot "console-error.log"
$serverLog = Join-Path $runtimeRoot "logs\latest.log"
Remove-Item -LiteralPath $stdoutFile, $stderrFile, $serverLog -Force -ErrorAction SilentlyContinue
$process = Start-Process -FilePath $java `
    -ArgumentList @("-Duser.language=en", "-Duser.country=US", "-Xms512M", "-Xmx1G", "-jar", $paperJar, "--nogui") `
    -WorkingDirectory $runtimeRoot `
    -RedirectStandardOutput $stdoutFile `
    -RedirectStandardError $stderrFile `
    -WindowStyle Hidden `
    -PassThru

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$enabled = $false
$economyReady = $false
$mongoReady = -not $UseExternalServices
$redisReady = -not $UseExternalServices
$mysqlReady = -not $UseMySql
$placeholderReady = -not $UseOptionalPlugins
$playerPointsReady = -not $UseOptionalPlugins
$coinsEngineReady = -not $UseOptionalPlugins
$optionalProbeReady = -not $UseOptionalPlugins
$fatal = $false

try {
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $serverLog) {
            $logText = Get-Content -LiteralPath $serverLog -Raw
            $economyReady = $logText -match "Economy provider registered: Money"
            $mongoReady = $mongoReady -or $logText -match "MongoDB connection established"
            $redisReady = $redisReady -or $logText -match "Redis connection established"
            $mysqlReady = $mysqlReady -or $logText -match "Database connection established\. \(MySQL\)"
            $placeholderReady = $placeholderReady -or $logText -match "PlaceholderAPI hook registered\."
            $playerPointsReady = $playerPointsReady -or $logText -match "Economy provider registered: Points \(currency: points\)"
            $coinsEngineReady = $coinsEngineReady -or $logText -match "Economy provider registered: Gems \(currency: gems\)"
            $optionalProbeReady = $optionalProbeReady -or $logText -match "NEXAH_OPTIONAL_PROBE_PASS pointsDelta=15 coinsFormat=true totalListings=\d+"
            $enabled = $logText -match "NexAuctionHouse v.+ has been enabled"
            $fatal = $logText -match "Failed to establish database connection|Failed to connect to MongoDB|Failed to connect to Redis|Failed to hook into PlayerPoints API|Failed to hook into CoinsEngine API|No economy provider available|nexauction expansion could not be registered"
        }

        if ($enabled -or $fatal) {
            break
        }
        Start-Sleep -Milliseconds 200
    }
}
finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        $process.WaitForExit()
    }
}

$logFile = Join-Path $runtimeRoot "smoke-output.log"
$captured = @()
if (Test-Path -LiteralPath $serverLog) {
    $captured += Get-Content -LiteralPath $serverLog
}
if (Test-Path -LiteralPath $stderrFile) {
    $captured += Get-Content -LiteralPath $stderrFile
}
Set-Content -LiteralPath $logFile -Encoding UTF8 -Value $captured

if (-not $economyReady) {
    throw "Vault economy integration did not become ready. See $logFile"
}
if (-not $enabled) {
    throw "NexAuctionHouse did not enable before the timeout. See $logFile"
}
if (-not $mongoReady) {
    throw "MongoDB integration did not become ready. See $logFile"
}
if (-not $redisReady) {
    throw "Redis integration did not become ready. See $logFile"
}
if (-not $mysqlReady) {
    throw "MySQL integration did not become ready. See $logFile"
}
if (-not $placeholderReady) {
    throw "PlaceholderAPI integration did not become ready. See $logFile"
}
if (-not $playerPointsReady) {
    throw "PlayerPoints integration did not become ready. See $logFile"
}
if (-not $coinsEngineReady) {
    throw "CoinsEngine integration did not become ready. See $logFile"
}
if (-not $optionalProbeReady) {
    throw "Optional integration transaction/placeholder probe did not pass. See $logFile"
}
if ($fatal) {
    throw "NexAuctionHouse reported a fatal startup error. See $logFile"
}

$databaseLabel = if ($UseOptionalPlugins) {
    "SQLite, PlaceholderAPI, PlayerPoints, CoinsEngine"
} elseif ($UseMySql) {
    "MySQL"
} elseif ($UseExternalServices) {
    "MongoDB, Redis cross-server mode"
} else {
    "SQLite"
}
Write-Host "PASS: Paper, Vault, EssentialsX, $databaseLabel and NexAuctionHouse started successfully."
Write-Host "Log: $logFile"
