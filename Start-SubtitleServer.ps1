# COMP3011 Assignment 3: start SubtitleServer with the examiner's USB key file.
# Do not print the key, paste it into Eclipse, or copy it into the project.
param(
    [Parameter(Mandatory = $true)]
    [string] $KeyFile,
    [string] $JavaHome
)

$ErrorActionPreference = 'Stop'
$serverDirectory = Join-Path $PSScriptRoot 'SubtitleServer'
$mavenWrapper = Join-Path $serverDirectory 'mvnw.cmd'

if (-not (Test-Path -LiteralPath $KeyFile -PathType Leaf)) {
    throw "Key file not found at the specified path. Check the USB drive letter."
}
if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Missing SubtitleServer/mvnw.cmd next to this script."
}

# This reads from the original file on the USB; no secret is written to disk.
# Do not use Get-Content without assigning it, as that would print the key.
$apiKey = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $KeyFile).ProviderPath).Trim()
if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -match '\s') {
    throw "The key file must contain a single non-empty API key."
}

$previousKey = [Environment]::GetEnvironmentVariable('OPENAI_API_KEY', 'Process')
$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
try {
    if ($JavaHome) {
        if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe') -PathType Leaf)) {
            throw "JavaHome does not point to a Windows JDK."
        }
        $env:JAVA_HOME = $JavaHome
    }

    $env:OPENAI_API_KEY = $apiKey
    $apiKey = $null
    Write-Host "Starting SubtitleServer using the key file supplied on the USB. Key not shown."
    Push-Location $serverDirectory
    try {
        & $mavenWrapper spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw "Maven exited with code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    # Keep the secret out of this shell after Spring Boot stops.
    if ($null -eq $previousKey) {
        Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
    } else {
        $env:OPENAI_API_KEY = $previousKey
    }
    if ($null -eq $previousJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $previousJavaHome
    }
    $apiKey = $null
}
