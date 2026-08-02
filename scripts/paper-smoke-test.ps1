param(
    [string]$MinecraftVersion = "1.21.4",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $repositoryRoot "build\paper-smoke-$MinecraftVersion"
$pluginsRoot = Join-Path $runtimeRoot "plugins"
$paperJar = Join-Path $runtimeRoot "paper.jar"
$userAgent = "NexAuctionHouse-TestHarness/1.0 (https://github.com/Rynix01/NexAuctionHouse)"

function Download-FileIfMissing {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (Test-Path -LiteralPath $Destination) {
        return
    }

    Write-Host "Downloading $([IO.Path]::GetFileName($Destination))..."
    Invoke-WebRequest -Uri $Url -OutFile $Destination -Headers @{ "User-Agent" = $userAgent }
}

Write-Host "Building NexAuctionHouse..."
& (Join-Path $repositoryRoot "gradlew.bat") shadowJar --no-daemon
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
Copy-Item -LiteralPath (Join-Path $repositoryRoot "build\libs\NexAuctionHouse-1.0.0.jar") `
    -Destination $pluginJar -Force

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
Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
$process = Start-Process -FilePath $java `
    -ArgumentList @("-Xms512M", "-Xmx1G", "-jar", $paperJar, "--nogui") `
    -WorkingDirectory $runtimeRoot `
    -RedirectStandardOutput $stdoutFile `
    -RedirectStandardError $stderrFile `
    -WindowStyle Hidden `
    -PassThru

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$enabled = $false
$economyReady = $false
$fatal = $false

try {
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $serverLog) {
            $logText = Get-Content -LiteralPath $serverLog -Raw
            $economyReady = $logText -match "Economy provider registered: Money"
            $enabled = $logText -match "NexAuctionHouse v.+ has been enabled"
            $fatal = $logText -match "Failed to establish database connection|No economy provider available"
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
if ($fatal) {
    throw "NexAuctionHouse reported a fatal startup error. See $logFile"
}

Write-Host "PASS: Paper, Vault, EssentialsX, SQLite and NexAuctionHouse started successfully."
Write-Host "Log: $logFile"
