$ErrorActionPreference = "Stop"

# sdkmanager/gradle need Java but agent shells often lack JAVA_HOME and PATH entries.
if (-not $env:JAVA_HOME) {
  foreach ($cand in @('C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot', 'C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot')) {
    if (Test-Path (Join-Path $cand 'bin\java.exe')) { $env:JAVA_HOME = $cand; break }
  }
}
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$toolsDir = Join-Path $sdk "cmdline-tools\latest"
$tmp = Join-Path $env:TEMP "slide-dictate-bootstrap"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
New-Item -ItemType Directory -Force -Path $sdk | Out-Null

function Download($url, $out) {
  Write-Host "Downloading $url"
  if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
    curl.exe -L -o $out $url
  } else {
    Invoke-WebRequest -Uri $url -OutFile $out
  }
}

if (-not (Test-Path (Join-Path $toolsDir "bin\sdkmanager.bat"))) {
  $zip = Join-Path $tmp "cmdtools.zip"
  Download "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" $zip
  $extract = Join-Path $tmp "cmdtools"
  if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
  Expand-Archive -Path $zip -DestinationPath $extract -Force
  $src = Join-Path $extract "cmdline-tools"
  New-Item -ItemType Directory -Force -Path (Split-Path $toolsDir) | Out-Null
  if (Test-Path $toolsDir) { Remove-Item -Recurse -Force $toolsDir }
  Move-Item -Path $src -Destination $toolsDir
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:Path = "$toolsDir\bin;$sdk\platform-tools;$env:Path"
$yes = ("y`n" * 30)

Write-Host "Installing SDK packages (this downloads ~1-2 GB)..."
echo $yes | & (Join-Path $toolsDir "bin\sdkmanager.bat") --licenses | Out-Null
& (Join-Path $toolsDir "bin\sdkmanager.bat") "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125" "cmake;3.22.1"

$gradleDir = Join-Path $tmp "gradle-8.7"
if (-not (Test-Path (Join-Path $gradleDir "bin\gradle.bat"))) {
  $gzip = Join-Path $tmp "gradle-8.7-bin.zip"
  Download "https://services.gradle.org/distributions/gradle-8.7-bin.zip" $gzip
  Expand-Archive -Path $gzip -DestinationPath $tmp -Force
}

$proj = Join-Path $PSScriptRoot "slide-dictate"
Write-Host "Building Slide Dictate..."
& (Join-Path $gradleDir "bin\gradle.bat") -p $proj wrapper --gradle-version 8.7
& (Join-Path $proj "gradlew.bat") -p $proj assembleDebug

$apk = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) { Write-Host "APK ready: $apk" } else { throw "Build finished but APK not found at $apk" }
