param([Parameter(Mandatory = $true)][string]$OutputPath)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repoRoot
$archivePath = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $archivePath) { throw 'Choose a new archive name; existing packages are preserved.' }
if ([IO.Path]::GetExtension($archivePath) -ne '.zip') { throw 'Output must be a ZIP file.' }
$files = @(git -c core.quotepath=false ls-files --cached --others --exclude-standard | Sort-Object -Unique | Where-Object {
    $_ -notmatch '(^|/)(\.git|node_modules|target|dist|work|secrets)(/|$)|(^|/)\.env($|\.)|\.(pem|key|p12|pfx|zip)$' -and
    (Test-Path -LiteralPath $_ -PathType Leaf)
})
$secrets = @()
if (Test-Path -LiteralPath '.env') {
    $secrets = @(Get-Content -LiteralPath '.env' | ForEach-Object {
        if ($_ -match '^([A-Z0-9_]*(PASSWORD|TOKEN|SECRET)[A-Z0-9_]*)=(.+)$') { $Matches[3].Trim().Trim('"').Trim("'") }
    } | Where-Object Length -ge 16)
}
foreach ($file in $files) {
    if ((Get-Item -LiteralPath $file).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked file: $file" }
    $body = [IO.File]::ReadAllText((Join-Path $repoRoot $file))
    foreach ($secret in $secrets) {
        if ($body.Contains($secret)) { throw "Private environment value found in source file: $file" }
    }
}
Add-Type -AssemblyName System.IO.Compression
$archive = [IO.Compression.ZipFile]::Open($archivePath, [IO.Compression.ZipArchiveMode]::Create)
$manifest = @()
try {
    foreach ($file in $files) {
        $source = Join-Path $repoRoot $file
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $source, $file.Replace('\','/')) | Out-Null
        $manifest += [pscustomobject]@{ path = $file; sha256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash }
    }
    $evidenceDir = Join-Path $repoRoot 'work/member2-evidence'
    if (Test-Path -LiteralPath $evidenceDir) {
        foreach ($picture in Get-ChildItem -LiteralPath $evidenceDir -Filter '*.png' -File) {
            $entryPath = 'verification/' + $picture.Name
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $picture.FullName, $entryPath) | Out-Null
            $manifest += [pscustomobject]@{ path = $entryPath; sha256 = (Get-FileHash -LiteralPath $picture.FullName -Algorithm SHA256).Hash }
        }
    }
    $entry = $archive.CreateEntry('snapshot-manifest.json')
    $writer = [IO.StreamWriter]::new($entry.Open())
    try {
        $writer.Write((@{
            baseCommit = (git rev-parse HEAD)
            branch = (git branch --show-current)
            uncommittedSnapshot = $true
            createdAt = [DateTimeOffset]::Now.ToString('o')
            files = $manifest
        } | ConvertTo-Json -Depth 5))
    } finally { $writer.Dispose() }
} finally { $archive.Dispose() }
Write-Output "Created $archivePath ($($manifest.Count) files plus manifest)."
