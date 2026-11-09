param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'dist'),
    [string]$JdkHome = '',
    [string]$Version = '1.0.0'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $JdkHome) {
    $compiler = Get-Command javac.exe -ErrorAction Stop
    $JdkHome = Split-Path (Split-Path $compiler.Source -Parent) -Parent
}
foreach ($tool in @('java','javac','jar','jlink','jpackage')) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome "bin/$tool.exe"))) {
        throw "Missing $tool.exe. Use a Windows x64 JDK 17 or later with jpackage."
    }
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$buildRoot = Join-Path $PSScriptRoot ('.build/' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $buildRoot 'classes'
$inputDirectory = Join-Path $buildRoot 'input'
$runtimeDirectory = Join-Path $buildRoot 'runtime'
$imageDirectory = Join-Path ([IO.Path]::GetTempPath()) ('raspored-package-' + [Guid]::NewGuid().ToString('N'))
foreach ($directory in @($classes,$inputDirectory,$imageDirectory,$OutputDirectory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
function Invoke-JdkTool([string]$Tool, [string[]]$ToolArgs) {
    & (Join-Path $JdkHome "bin/$Tool.exe") "-J-Djava.io.tmpdir=$buildRoot" @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE." }
}
$javaSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
Write-Output '1/5 Compiling Java source...'
Invoke-JdkTool 'javac' (@('--release','17','--module-path',(Join-Path $PSScriptRoot 'lib'),'--add-modules','javafx.controls,javafx.graphics','-encoding','UTF-8','-d',$classes) + $javaSources)
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'src/rasporedcasova/ui/app.css') -Destination (Join-Path $classes 'rasporedcasova/ui/app.css')
Invoke-JdkTool 'jar' @('--create','--file',(Join-Path $inputDirectory 'raspored-casova.jar'),'--main-class','rasporedcasova.App','-C',$classes,'.')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'tests') -Destination $inputDirectory -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'example.json') -Destination $inputDirectory
Write-Output '2/5 Creating a bundled Java + JavaFX runtime...'
$modulePath = (Join-Path $JdkHome 'jmods') + ';' + (Join-Path $PSScriptRoot 'lib')
Invoke-JdkTool 'jlink' @('--module-path',$modulePath,'--add-modules','javafx.controls,javafx.graphics','--output',$runtimeDirectory,'--strip-debug','--no-header-files','--no-man-pages')
# Maven's Windows JavaFX JARs contain native DLLs. Keep them beside java.exe
# so the bundled runtime does not depend on JavaFX's per-user extraction cache.
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($library in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'lib') -Filter '*.jar' -File) {
    $archive = [IO.Compression.ZipFile]::OpenRead($library.FullName)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('.dll') -and $entry.FullName -notmatch '[/\\]') {
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $runtimeDirectory ('bin/' + $entry.Name)), $true)
            }
        }
    } finally { $archive.Dispose() }
}
Write-Output '3/5 Packaging the Windows application...'
Invoke-JdkTool 'jpackage' @('--type','app-image','--name','RasporedCasova','--app-version',$Version,'--vendor','Jovan Markovic','--description','Automatic school timetabling / Automatsko sastavljanje rasporeda casova','--input',$inputDirectory,'--main-jar','raspored-casova.jar','--main-class','rasporedcasova.App','--runtime-image',$runtimeDirectory,'--java-options','--add-modules=javafx.controls,javafx.graphics','--java-options','-Duser.dir=$APPDIR','--dest',$imageDirectory)
$appDirectory = Join-Path $imageDirectory 'RasporedCasova'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'QUICKSTART.txt') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'THIRD-PARTY-NOTICES.md') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'tests') -Destination $appDirectory -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'example.json') -Destination $appDirectory
Write-Output '4/5 Compressing the portable application...'
$zipPath = Join-Path $OutputDirectory "RasporedCasova-$Version-windows-x64.zip"
Compress-Archive -LiteralPath $appDirectory -DestinationPath $zipPath -Force
$hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $([IO.Path]::GetFileName($zipPath))" | Set-Content -LiteralPath ($zipPath + '.sha256') -Encoding ascii
Write-Output '5/5 Done. Extract the entire ZIP before opening RasporedCasova.exe.'
Write-Output "APP_DIRECTORY=$appDirectory"
Write-Output "DEMO_ZIP=$zipPath"

