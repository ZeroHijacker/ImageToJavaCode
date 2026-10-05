param(
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$PythonHome,
    [Parameter(Mandatory=$true)][string]$WixHome,
    [string]$Destination = (Join-Path $PSScriptRoot 'dist')
)
$ErrorActionPreference = 'Stop'
# Use a fresh staging directory; never remove a caller-provided directory.
$stage = Join-Path $env:TEMP ('portrait-package-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $stage | Out-Null
$classes = Join-Path $stage 'classes'
$inputDir = Join-Path $stage 'input'
New-Item -ItemType Directory -Path $classes,$inputDir -Force | Out-Null
& "$JdkHome/bin/javac.exe" --release 17 -d $classes (Get-ChildItem "$PSScriptRoot/*.java").FullName
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
Copy-Item "$PSScriptRoot/AutoAnalysis.py","$PSScriptRoot/PortraitRuntime.java" $classes
& "$JdkHome/bin/jar.exe" --create --file "$inputDir/PortraitStudio.jar" --main-class AutomaticStudio -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed.' }
Copy-Item $PythonHome "$inputDir/python" -Recurse
Get-ChildItem $PSScriptRoot -File | Where-Object { $_.Extension -in '.md','.html','.txt','.json' } | Copy-Item -Destination $inputDir
Copy-Item "$PSScriptRoot/licenses" $inputDir -Recurse
& "$JdkHome/bin/jlink.exe" --add-modules java.desktop,java.logging,java.compiler,jdk.compiler,jdk.unsupported,jdk.zipfs,jdk.charsets --strip-debug --no-header-files --no-man-pages --compress=2 --output "$stage/runtime"
if ($LASTEXITCODE -ne 0) { throw 'Runtime creation failed.' }
& "$JdkHome/bin/jpackage.exe" --type app-image --input $inputDir --dest "$stage/image" --name 'Portrait Studio' --main-jar PortraitStudio.jar --main-class AutomaticStudio --app-version 0.2.0 --vendor 'Portrait Studio' --runtime-image "$stage/runtime" --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'Application image creation failed.' }
$oldPath = $env:PATH
try {
    $env:PATH = "$WixHome;$oldPath"
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    & "$JdkHome/bin/jpackage.exe" --type msi --app-image "$stage/image/Portrait Studio" --dest $Destination --name 'Portrait Studio' --app-version 0.2.0 --vendor 'Portrait Studio' --description 'Local image to Java 2D converter' --win-per-user-install --win-dir-chooser --win-menu --win-menu-group 'Portrait Studio' --win-shortcut --win-upgrade-uuid '7c5328de-b660-4cb0-9358-e8e575da3e75' --license-file "$PSScriptRoot/LICENSE.txt"
    if ($LASTEXITCODE -ne 0) { throw 'MSI creation failed.' }
} finally { $env:PATH = $oldPath }
Write-Output "Installer created in $Destination. Build intermediates are retained at $stage for inspection."
