$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $root "slide-dictate\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Host "NOT BUILT YET: $apk"; exit 1 }

$sizeMb = [math]::Round((Get-Item $apk).Length / 1MB, 1)
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
Write-Host "APK: $apk"
Write-Host "Size: ${sizeMb} MB"
Write-Host "SHA-256: $sha"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
  $sos = @($zip.Entries | Where-Object { $_.FullName -like "lib/*/libwhisper_jni.so" } | ForEach-Object { $_.FullName })
  Write-Host "whisper_jni ABIs: $($sos -join ', ')"
  if ($sos.Count -eq 0) { Write-Host "NOTE: stub build, no native whisper lib inside." }
} finally { $zip.Dispose() }

$aapt = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools\34.0.0\aapt2.exe"
if (Test-Path $aapt) {
  & $aapt dump badging $apk | Select-String "^(package|sdkVersion|targetSdkVersion|native-code|uses-permission:)"
} else {
  Write-Host "aapt2 not found, skipping manifest dump."
}
