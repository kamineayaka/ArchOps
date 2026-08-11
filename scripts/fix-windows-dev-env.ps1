#Requires -Version 5.1
<#
.SYNOPSIS
  Repair ArchOps local Windows dev env: JAVA_HOME, Docker, Gradle wrapper cache, Java truststore.

.DESCRIPTION
  Common failure modes on this machine:
  - Docker Desktop installed but engine not started
  - Gradle wrapper cannot HTTPS-download distributions (SteamTools / Watt Toolkit MITM
    issues a "BeyondDimension / SteamTools Certificate" for github.com; Java cacerts
    does not trust it by default → PKIX path building failed)
  - Windows curl revocation check (CRYPT_E_NO_REVOCATION_CHECK) against some CDNs

  This script is idempotent and safe to re-run.
#>
[CmdletBinding()]
param(
    [switch]$SkipDocker,
    [switch]$SkipTruststore
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# --- Java ---
Write-Step "Align JAVA_HOME to Microsoft JDK 21"
$msJdk = "C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
$oracleJdk = "C:\Program Files\Java\jdk-21.0.12"
$jdk = @(
    $msJdk,
    $oracleJdk,
    $env:JAVA_HOME
) | Where-Object { $_ -and (Test-Path "$_\bin\java.exe") } | Select-Object -First 1

if (-not $jdk) {
    throw "No JDK 21 found. Install Microsoft OpenJDK 21 or Oracle JDK 21."
}

[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
$env:JAVA_HOME = $jdk
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ([string]::IsNullOrEmpty($userPath)) { $userPath = "" }
if ($userPath -notlike "*$jdk\bin*") {
    [Environment]::SetEnvironmentVariable("Path", "$jdk\bin;$userPath", "User")
}
$env:Path = "$jdk\bin;$env:Path"
& "$jdk\bin\java.exe" -version

# --- Docker ---
if (-not $SkipDocker) {
    Write-Step "Ensure Docker Desktop engine is running"
    $dockerExe = @(
        "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1

    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        if (-not $dockerExe) { throw "Docker Desktop not found. Install it first." }
        Write-Host "Starting: $dockerExe"
        Start-Process $dockerExe | Out-Null
        $ready = $false
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 3
            docker info 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        }
        if (-not $ready) { throw "Docker engine did not become ready in time. Open Docker Desktop UI and retry." }
    }
    docker info --format "Docker engine OK: {{.ServerVersion}}"
}

# --- Gradle wrapper cache seed ---
Write-Step "Seed Gradle 8.12.1 wrapper cache (avoid HTTPS re-download)"
$distRoot = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-8.12.1-bin"
$manual = Join-Path $env:USERPROFILE ".gradle\caches\manual"
New-Item -ItemType Directory -Force -Path $manual | Out-Null

$good = Get-ChildItem $distRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName "gradle-8.12.1\bin\gradle.bat") } |
    Select-Object -First 1

if (-not $good) {
    Write-Warning "No extracted Gradle 8.12.1 found under $distRoot. If wrapper download fails, copy a working distribution here first."
} else {
    $zip = Join-Path $manual "gradle-8.12.1-bin.zip"
    if (-not (Test-Path $zip) -or (Get-Item $zip).Length -lt 1MB) {
        Push-Location $good.FullName
        & "$jdk\bin\jar.exe" -cMf $zip gradle-8.12.1
        Pop-Location
    }

    Get-ChildItem $distRoot -Directory | ForEach-Object {
        $hasDist = Test-Path (Join-Path $_.FullName "gradle-8.12.1\bin\gradle.bat")
        if (-not $hasDist) {
            Write-Host "Seeding $($_.Name)"
            Remove-Item -Recurse -Force $_.FullName -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Force -Path $_.FullName | Out-Null
            Copy-Item -Recurse (Join-Path $good.FullName "gradle-8.12.1") (Join-Path $_.FullName "gradle-8.12.1")
            Copy-Item $zip (Join-Path $_.FullName "gradle-8.12.1-bin.zip") -Force
            New-Item -ItemType File -Force -Path (Join-Path $_.FullName "gradle-8.12.1-bin.zip.ok") | Out-Null
        }
    }
}

# --- Java truststore for SteamTools MITM ---
if (-not $SkipTruststore) {
    Write-Step "Sync SteamTools / intercepted certs into a user Java truststore"
    $certsDir = Join-Path $manual "certs"
    $localTrust = Join-Path $manual "cacerts-archops"
    New-Item -ItemType Directory -Force -Path $certsDir | Out-Null
    Copy-Item (Join-Path $jdk "lib\security\cacerts") $localTrust -Force

    foreach ($location in @("CurrentUser", "LocalMachine")) {
        foreach ($storeName in @("Root", "CA")) {
            $store = New-Object System.Security.Cryptography.X509Certificates.X509Store($storeName, $location)
            try {
                $store.Open("ReadOnly")
                $store.Certificates |
                    Where-Object { $_.Subject -match "SteamTools|BeyondDimension|WattToolkit" } |
                    ForEach-Object {
                        $path = Join-Path $certsDir ("steamtools-" + $_.Thumbprint + ".cer")
                        [IO.File]::WriteAllBytes($path, $_.Export("Cert"))
                    }
                $store.Close()
            } catch {
                # LocalMachine may require elevation; ignore
            }
        }
    }

    Get-ChildItem $certsDir -Filter *.cer -ErrorAction SilentlyContinue | ForEach-Object {
        $alias = "archops-" + $_.BaseName
        if ($alias.Length -gt 100) { $alias = $alias.Substring(0, 100) }
        & "$jdk\bin\keytool.exe" -delete -alias $alias -keystore $localTrust -storepass changeit -noprompt 2>$null | Out-Null
        & "$jdk\bin\keytool.exe" -importcert -alias $alias -file $_.FullName -keystore $localTrust -storepass changeit -noprompt 2>$null | Out-Null
    }

    $toolOpts = "-Djavax.net.ssl.trustStore=$localTrust -Djavax.net.ssl.trustStorePassword=changeit"
    [Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS", $toolOpts, "User")
    $env:JAVA_TOOL_OPTIONS = $toolOpts
    Write-Host "JAVA_TOOL_OPTIONS set (needed so Gradle wrapper JVM trusts SteamTools MITM)."
    Write-Host "Alternative: disable GitHub acceleration / HTTPS MITM in SteamTools (Watt Toolkit)."
}

Write-Step "Verify gradlew"
Push-Location (Join-Path $PSScriptRoot "..\backend")
try {
    .\gradlew.bat --version
    if ($LASTEXITCODE -ne 0) { throw "gradlew --version failed" }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Done. Open a NEW terminal so User env vars reload." -ForegroundColor Green
Write-Host "Optional: docker compose -f deploy/compose/compose.yaml up -d postgres redis"
Write-Host "  If docker.io DNS is poisoned, pull via mirror then retag:"
Write-Host "    docker pull docker.m.daocloud.io/library/postgres:16"
Write-Host "    docker tag  docker.m.daocloud.io/library/postgres:16 postgres:16"
Write-Host "    docker pull docker.m.daocloud.io/library/redis:7-alpine"
Write-Host "    docker tag  docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine"
Write-Host "Optional: cd backend; .\gradlew.bat test"
Write-Host "Note: JAVA_TOOL_OPTIONS points Java at a user truststore that includes SteamTools CA."
Write-Host "      Prefer disabling GitHub HTTPS MITM in SteamTools if you do not need it."
