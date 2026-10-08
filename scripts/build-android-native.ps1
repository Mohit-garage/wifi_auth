$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$androidLibs = Join-Path $repoRoot "android\app\src\main\jniLibs"

New-Item -ItemType Directory -Force -Path $androidLibs | Out-Null

Write-Host "Building Rust libraries for Android..."

cargo ndk `
    -t arm64-v8a `
    -t x86_64 `
    -o $androidLibs `
    build --release

$expected = @(
    "arm64-v8a",
    "x86_64"
)

foreach ($abi in $expected) {
    $abiPath = Join-Path $androidLibs $abi
    $canonical = Join-Path $abiPath "libuniffi_wifi_auth.so"
    $oldName = Join-Path $abiPath "libwifi_auth.so"

    if (!(Test-Path $canonical) -and (Test-Path $oldName)) {
        Copy-Item $oldName $canonical
    }

    if (!(Test-Path $canonical)) {
        throw "Missing native library: $canonical"
    }

    if (Test-Path $oldName) {
        Remove-Item $oldName -Force
    }

    Write-Host "Prepared $canonical"
}

Write-Host "Android native libraries built successfully."