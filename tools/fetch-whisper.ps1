$ErrorActionPreference = "Stop"
$dest = "C:\Users\user\joshcode\slide-dictate-android\slide-dictate\app\src\main\cpp\whisper.cpp"
$repo = "https://github.com/ggerganov/whisper.cpp.git"
$api = "https://api.github.com/repos/ggerganov/whisper.cpp/contents"

if ((Test-Path (Join-Path $dest "whisper.cpp")) -or (Test-Path (Join-Path $dest "src/whisper.cpp"))) {
  Write-Host "whisper.cpp already present, skipping clone."
  Get-ChildItem $dest | Select-Object -First 12 Name
  exit 0
}

function FilesAt($tag, $dir) {
  $url = if ($dir) { "$api/$dir`?ref=$tag" } else { "$api`?ref=$tag" }
  $json = curl.exe -sL $url
  if ($json -match '"message": "Not Found"') { return @() }
  return @($json | Select-String '"name": "([^"]+)"' -AllMatches).ForEach({ $_.Matches }).ForEach({ $_.Groups[1].Value })
}

# Tags whose tree matches app CMakeLists (flat root: whisper.cpp/h + ggml*.c).
foreach ($tag in @("v1.5.4", "v1.6.2", "v1.6.1")) {
  $have = git ls-remote --tags $repo $tag
  if (-not $have) { Write-Host "${tag}: tag not found"; continue }
  $root = FilesAt $tag ""
  $need = @("whisper.cpp", "whisper.h", "ggml.c", "ggml-alloc.c", "ggml-backend.c", "ggml-quants.c")
  $missing = @($need | Where-Object { $_ -notin $root })
  Write-Host "${tag}: missing=$($missing -join ',')"
  if ($missing.Count -eq 0) {
    Write-Host "Cloning ${tag} (shallow)..."
    git clone --depth 1 --branch $tag $repo $dest
    Write-Host "Top level:"; Get-ChildItem $dest | Select-Object Name
    exit 0
  }
}
Write-Host "NO MATCHING TAG - not cloning. Update CMakeLists to the current tree first."
exit 1
