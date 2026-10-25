param(
    [Parameter(Mandatory=$true)][string]$Capture,
    [Parameter(Mandatory=$true)][string]$Scene,
    [string]$StudioRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$JavaExecutable = $(if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java.exe'}),
    [ValidateSet('legacy','forward')][string]$Renderer='legacy',
    [string]$SeparateSupport
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
# Bound this frozen replay, not an interactive Studio. The previous 4-GiB gate
# was a precaution, not a recorded peak. Include texture residency conservatively
# even on discrete GPUs; native/driver memory is not covered by Xmx or direct limits.
$textureBytes=0L
foreach($texture in Get-ChildItem -LiteralPath $capturePath -Filter 'texture-*.png'){
    $header=[IO.File]::ReadAllBytes($texture.FullName)
    $w=[int64]$header[16]*16777216+[int64]$header[17]*65536+[int64]$header[18]*256+$header[19]
    $h=[int64]$header[20]*16777216+[int64]$header[21]*65536+[int64]$header[22]*256+$header[23]
    $textureBytes+=$w*$h*4
}
$systemReserve=1GB
# Heap + direct buffers + metaspace + code + native reserve + texture/array/mips
# and 128 MiB for this replay's world targets, shadow attachments and readbacks.
$processBudget=512MB+256MB+192MB+128MB+256MB+[int64]($textureBytes*4)+128MB
$available=[int64](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB
if($available-lt ($processBudget+$systemReserve)){throw "Memory admission refused: available=$available required=$($processBudget+$systemReserve)"}
$allJava=@(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^java(w)?\.exe$'})
$busy=@($allJava | Where-Object {$_.CommandLine -match 'PixscapeStudioApplication|PreviewDesktopApplication|FrozenPoseReplay|TwoDepthShadowFixtureGlCheck'})
if($busy.Count){throw 'Existing Studio/Preview/replay/build process: refused'}
$daemonLogs=@()
foreach($daemon in $allJava | Where-Object {$_.CommandLine -match 'GradleDaemon'}){
    $logs=@(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/daemon') -Directory | ForEach-Object {Get-ChildItem -LiteralPath $_.FullName -Filter "daemon-$($daemon.ProcessId).out.log" -File})
    if($logs.Count-ne 1){throw 'Cannot verify existing Gradle daemon state'}
    $daemonLogs+=$logs[0].FullName
}
function Assert-DaemonsIdle {
    foreach($log in $daemonLogs){
        $state=Select-String -LiteralPath $log -Pattern 'DaemonRegistryUpdater.*Marking the daemon as' | Select-Object -Last 1
        if(!$state -or $state.Line -notmatch 'as idle'){throw 'Concurrent Gradle build or unknown daemon state: refused'}
    }
}
Assert-DaemonsIdle
$run=Join-Path $StudioRoot ('build/local-validation/replay-'+[datetime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ'))
New-Item -ItemType Directory -Path $run | Out-Null
$arguments=@('-Xmx512m','-XX:MaxDirectMemorySize=256m','-XX:MaxMetaspaceSize=192m','-XX:ReservedCodeCacheSize=128m',('-Dpixscape.diagnostic.renderer='+$Renderer),('-Dpixscape.diagnostic.replayOutput="'+$run+'\images"'),
    '-Dpixscape.diagnostic.groupSweep=true',('-Dpixscape.diagnostic.wallGroupScene="'+$scenePath+'"'),
    '-cp',('"'+$cp+'"'),'games.pixscape.studio.ui.preview.FrozenPoseReplay',('"'+(Join-Path $capturePath 'pose.json')+'"'))
if($SeparateSupport){$arguments=@(('-Dpixscape.diagnostic.separateSupport='+$SeparateSupport))+$arguments}
"available=$available processBudget=$processBudget reserve=$systemReserve textureBytes=$textureBytes renderer=$Renderer separate=$SeparateSupport" | Set-Content "$run/budget.txt"
$process=$null
try {
    $process=Start-Process $java -WindowStyle Hidden -PassThru -WorkingDirectory $StudioRoot -ArgumentList $arguments -RedirectStandardOutput "$run/stdout.log" -RedirectStandardError "$run/stderr.log"
    Write-Output "Owned PID=$($process.Id) VSync=true FPS=30 deadline=45s output=$run"
    $deadline=[datetime]::UtcNow.AddSeconds(45)
    while(-not $process.HasExited -and [datetime]::UtcNow -lt $deadline){
        Assert-DaemonsIdle
        $process.Refresh()
        $free=[int64](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory*1KB
        [pscustomobject]@{utc=[datetime]::UtcNow.ToString('o');pid=$process.Id;available=$free;workingSet=$process.WorkingSet64;privateBytes=$process.PrivateMemorySize64;peakWorkingSet=$process.PeakWorkingSet64} | Export-Csv "$run/memory.csv" -Append -NoTypeInformation
        if($free-lt $systemReserve -or $process.PrivateMemorySize64-gt $processBudget){throw 'Memory safety stop'}
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
