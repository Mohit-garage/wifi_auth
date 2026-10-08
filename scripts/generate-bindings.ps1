$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$androidSource = Join-Path $repoRoot "android\app\src\main\java"
$tempDir = Join-Path $repoRoot ".binding-output"
$tempUdl = Join-Path $repoRoot "src\.binding-shell.udl"
$nativeLib = Join-Path $repoRoot "android\app\src\main\jniLibs\arm64-v8a\libuniffi_wifi_auth.so"

New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

Set-Content $tempUdl "namespace wifi_auth {};"

cargo run --bin uniffi-bindgen -- `
    generate `
    --language kotlin `
    --crate wifi_auth `
    --out-dir $tempDir `
    --lib-file $nativeLib `
    $tempUdl

$generated = Join-Path $tempDir "uniffi\wifi_auth\wifi_auth.kt"
$destination = Join-Path $androidSource "uniffi\wifi_auth\wifi_auth.kt"

if (!(Test-Path $generated)) {
    throw "Generated Kotlin binding was not found."
}

New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
Copy-Item $generated $destination -Force

Remove-Item $tempUdl -Force
Remove-Item $tempDir -Recurse -Force

Write-Host "Kotlin UniFFI binding generated successfully."