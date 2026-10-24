param(
    [Parameter(Mandatory=$true)][string]$Capture,
    [Parameter(Mandatory=$true)][string]$Scene,
    [string]$StudioRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$JavaExecutable = $(if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java.exe'})
)
$ErrorActionPreference='Stop'
$StudioRoot=(Resolve-Path -LiteralPath $StudioRoot).Path
$runtime=(Resolve-Path -LiteralPath (Join-Path $StudioRoot '../pixscape-runtime')).Path
$capturePath=(Resolve-Path -LiteralPath $Capture).Path
$scenePath=(Resolve-Path -LiteralPath $Scene).Path
$java=(Get-Command $JavaExecutable -ErrorAction Stop).Source
$cpFile=Join-Path $StudioRoot 'build/local-validation/runtime-classpath.txt'
if(-not(Test-Path -LiteralPath $cpFile)){throw 'Generate writeLocalValidationClasspath with local-validation.init.gradle and the Runtime composite first.'}
$cp=(Get-Content -LiteralPath $cpFile -Raw).Trim()
$runtimeEntries=@($cp.Split(';') | Where-Object {$_ -match 'pixscape-runtime[^\\/]*\.jar$'})
if($runtimeEntries.Count -ne 1 -or -not $runtimeEntries[0].StartsWith($runtime+'\build\libs\',[StringComparison]::OrdinalIgnoreCase)){
    throw 'Refusing a published or ambiguous Runtime classpath.'
}
if($cp -match 'shadow-volume-silhouette'){throw 'Prototype classpath is not an integration validation.'}
if(-not(Test-Path -LiteralPath (Join-Path $capturePath 'READY'))){throw 'Incomplete capture'}
if(([int64](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB)-lt 4GB){throw 'Memory admission refused (4 GiB)'}
$busy=@(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'PixscapeStudioApplication|PreviewDesktopApplication|FrozenPoseReplay|GradleDaemon|TwoDepthShadowFixtureGlCheck'})
if($busy.Count){throw 'Existing Studio/Preview/replay/build process: refused'}
$run=Join-Path $StudioRoot ('build/local-validation/replay-'+[datetime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ'))
New-Item -ItemType Directory -Path $run | Out-Null
$arguments=@('-Xmx768m',('-Dpixscape.diagnostic.replayOutput="'+$run+'\images"'),
    '-Dpixscape.diagnostic.groupSweep=true',('-Dpixscape.diagnostic.wallGroupScene="'+$scenePath+'"'),
    '-cp',('"'+$cp+'"'),'games.pixscape.studio.ui.preview.FrozenPoseReplay',('"'+(Join-Path $capturePath 'pose.json')+'"'))
$process=$null
try {
    $process=Start-Process $java -WindowStyle Hidden -PassThru -WorkingDirectory $StudioRoot -ArgumentList $arguments -RedirectStandardOutput "$run/stdout.log" -RedirectStandardError "$run/stderr.log"
    Write-Output "Owned PID=$($process.Id) VSync=true FPS=30 deadline=45s output=$run"
    $deadline=[datetime]::UtcNow.AddSeconds(45)
    while(-not $process.HasExited -and [datetime]::UtcNow -lt $deadline){
        if(([int64](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB)-lt 2GB){throw 'Memory safety stop'}
        Start-Sleep -Milliseconds 500
    }
    if(-not $process.HasExited){throw 'Replay deadline exceeded'}
    $process.WaitForExit()
    if($process.ExitCode -ne 0){throw "Replay exited $($process.ExitCode)"}
    if(-not(Test-Path -LiteralPath "$run/images/mode-0-move-7.png")){throw 'Incomplete replay'}
} finally {
    if($null -ne $process -and -not $process.HasExited){$process.Kill();$process.WaitForExit()}
    Write-Output 'Owned replay closed; diagnostic output retained.'
}
