$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location $repo
$url = 'http://127.0.0.1:18081/'
function Test-Demo {
    try {
        $response = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -eq 200 -and $response.Content -match 'MEMBER 01'
    } catch { return $false }
}
if (-not (Test-Demo)) {
    & wsl.exe -d Ubuntu-20.04 -- env COMPOSE_PROJECT_NAME=campus-member-01 docker compose up -d --no-build --wait
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. See docs/runbooks/member-01-windows.md.' }
    $wslRepo = (& wsl.exe -d Ubuntu-20.04 -- wslpath -a $repo).Trim()
    New-Item -ItemType Directory -Force -Path .local | Out-Null
    $process = Start-Process -FilePath wsl.exe -ArgumentList @('-d', 'Ubuntu-20.04', '--cd', $wslRepo, '--exec', 'env', 'COMPOSE_PROJECT_NAME=campus-member-01', 'python3.9', 'scripts/member01-demo/server.py') -WindowStyle Hidden -RedirectStandardOutput "$repo/.local/member01-demo-server.log" -RedirectStandardError "$repo/.local/member01-demo-server-error.log" -PassThru
    $process.Id | Set-Content .local/member01-demo-server.pid
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-Demo) { break }
        Start-Sleep -Seconds 1
    }
    if (-not (Test-Demo)) { throw 'Demo startup failed. Check .local/member01-demo-server-error.log.' }
}
Start-Process $url
