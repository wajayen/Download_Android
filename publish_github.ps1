param(
    [string]$RepoName = "Download_Android",
    [string]$Visibility = "public",
    [int]$VersionCode = 0,
    [string]$VersionName = "",
    [string]$ApkPath = "",
    [string]$TagName = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
if ($null -ne (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue)) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$gh = "C:\Program Files\GitHub CLI\gh.exe"
if (!(Test-Path -LiteralPath $gh)) {
    throw "GitHub CLI not found: $gh"
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $repoRoot

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string[]]$Arguments = @()
    )
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $FilePath @Arguments
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $commandText = ($Arguments | ForEach-Object { if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ } }) -join ' '
        throw "Command failed with exit code ${exitCode}: $FilePath $commandText"
    }
}

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string[]]$Arguments = @()
    )
    $commandText = (($Arguments | ForEach-Object { if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ } }) -join ' ').Trim()
    if ($DryRun) {
        Write-Host ("[DryRun] {0} {1}" -f $FilePath, $commandText).Trim()
        return
    }
    Invoke-CheckedCommand -FilePath $FilePath -Arguments $Arguments
}

function Get-OriginRepoSlug {
    $originUrl = git -c safe.directory=$repoRoot remote get-url origin 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $originUrl) {
        return $null
    }
    $originUrl = "$originUrl".Trim()
    if ($originUrl -match 'github\.com[:/](.+?)(?:\.git)?$') {
        return $Matches[1]
    }
    return $null
}

function Test-GitHubReleaseExists {
    param([Parameter(Mandatory = $true)][string]$ReleaseTag)
    $repoSlug = Get-OriginRepoSlug
    if (-not $repoSlug) {
        return $false
    }
    $apiUrl = "https://api.github.com/repos/$repoSlug/releases/tags/$ReleaseTag"
    try {
        Invoke-RestMethod -Uri $apiUrl -Headers @{ "User-Agent" = "download-android-publisher" } | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-RemoteTagVisibility {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ReleaseTag,
        [int]$MaxAttempts = 10
    )
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $remoteRefs = git -c safe.directory=$repoRoot -c http.sslBackend=openssl ls-remote --tags origin $ReleaseTag 2>$null
        if ($LASTEXITCODE -eq 0 -and $remoteRefs) {
            return
        }
        if ($attempt -lt $MaxAttempts) {
            Start-Sleep -Seconds 2
        }
    }
    throw "Remote tag did not become visible in time: $ReleaseTag"
}

function Ensure-GhRelease {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ReleaseTag,
        [Parameter(Mandatory = $true)]
        [string]$AssetPath,
        [Parameter(Mandatory = $true)]
        [string]$ReleaseNotes
    )
    $repoSlug = Get-OriginRepoSlug
    if (-not $repoSlug) {
        throw "Could not determine GitHub repo slug from origin"
    }
    if (Test-GitHubReleaseExists -ReleaseTag $ReleaseTag) {
        Invoke-CheckedCommand -FilePath $gh -Arguments @("release", "upload", $ReleaseTag, $AssetPath, "--clobber", "-R", $repoSlug)
        Invoke-CheckedCommand -FilePath $gh -Arguments @("release", "edit", $ReleaseTag, "--title", $ReleaseTag, "--notes", $ReleaseNotes, "-R", $repoSlug)
        return
    }
    Invoke-CheckedCommand -FilePath $gh -Arguments @("release", "create", $ReleaseTag, $AssetPath, "--title", $ReleaseTag, "--notes", $ReleaseNotes, "-R", $repoSlug)
}

if ($VersionCode -le 0) {
    throw "VersionCode is required"
}
if (-not $TagName) {
    $TagName = "v$VersionCode"
}
if (-not $ApkPath) {
    if (-not $VersionName) {
        throw "VersionName or ApkPath is required"
    }
    $ApkPath = Join-Path $repoRoot ("dist\downloader-android-{0}-debug.apk" -f $VersionName)
}
if (!(Test-Path -LiteralPath $ApkPath)) {
    throw "APK not found: $ApkPath"
}

if ($DryRun) {
    Write-Host "[DryRun] git -c safe.directory=$repoRoot add -A"
} else {
    git -c safe.directory=$repoRoot add -A
}
$hasTrackedChanges = $false
if ($DryRun) {
    $hasTrackedChanges = $true
} else {
    git -c safe.directory=$repoRoot diff --cached --quiet
    $diffExitCode = $LASTEXITCODE
    if ($diffExitCode -eq 1) {
        $hasTrackedChanges = $true
    } elseif ($diffExitCode -ne 0) {
        throw "git diff --cached --quiet failed with exit code $diffExitCode"
    }
}
if ($hasTrackedChanges) {
    Invoke-LoggedCommand -FilePath "git" -Arguments @("-c", "safe.directory=$repoRoot", "commit", "-m", ("Release {0}" -f $TagName))
}

$originExists = $false
git -c safe.directory=$repoRoot remote get-url origin | Out-Null
if ($LASTEXITCODE -eq 0) {
    $originExists = $true
} elseif ($LASTEXITCODE -ne 2 -and $LASTEXITCODE -ne 128) {
    throw "git remote get-url origin failed with exit code $LASTEXITCODE"
}

if (-not $originExists) {
    Invoke-LoggedCommand -FilePath $gh -Arguments @("repo", "create", $RepoName, "--$Visibility", "--source", ".", "--remote", "origin", "--push")
} else {
    Invoke-LoggedCommand -FilePath "git" -Arguments @("-c", "safe.directory=$repoRoot", "-c", "http.sslBackend=openssl", "push", "-u", "origin", "HEAD")
}

Invoke-LoggedCommand -FilePath "git" -Arguments @("-c", "safe.directory=$repoRoot", "tag", "-f", $TagName)
Invoke-LoggedCommand -FilePath "git" -Arguments @("-c", "safe.directory=$repoRoot", "-c", "http.sslBackend=openssl", "push", "origin", ("refs/tags/{0}" -f $TagName), "--force")
if ($DryRun) {
    Write-Host ("[DryRun] wait for remote tag visibility: {0}" -f $TagName)
} else {
    Wait-RemoteTagVisibility -ReleaseTag $TagName
}

$releaseNotes = if ($VersionName) {
    "Automated Android release for version $VersionName (versionCode $VersionCode)"
} else {
    "Automated Android release for versionCode $VersionCode"
}
if ($DryRun) {
    Write-Host ("[DryRun] gh release create/upload {0} {1}" -f $TagName, $ApkPath)
} else {
    Ensure-GhRelease -ReleaseTag $TagName -AssetPath $ApkPath -ReleaseNotes $releaseNotes
}
