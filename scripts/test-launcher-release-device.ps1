param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$Mapping,
    [Parameter(Mandatory = $true)][string]$AndroidSdk,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Python = 'python',
    [string]$DeviceSerial,
    [string]$OutputDirectory
)

# Runs the actual minified APK signing code with a disposable shell-UID key.
# Does not install an APK, read the Manager key, hide icons, or obtain root.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$Apk = (Resolve-Path -LiteralPath $Apk).Path
$Mapping = (Resolve-Path -LiteralPath $Mapping).Path
if (!$OutputDirectory) { $OutputDirectory = Join-Path $root 'out/launcher-release-device' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$tools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'lib/d8.jar') } |
    Sort-Object Name -Descending | Select-Object -First 1
$platform = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'platforms') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'android.jar') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (!$tools -or !$platform -or !(Test-Path -LiteralPath $javac)) { throw 'JDK and Android SDK tools are required' }
$adbArguments = @()
if ($DeviceSerial) { $adbArguments = @('-s', $DeviceSerial) }
& $adb @adbArguments get-state
if ($LASTEXITCODE -ne 0) { throw 'Select one connected, authorized device' }

$lookup = @'
import json,re,sys
owner=None
methods=[]
model=None
with open(sys.argv[1],encoding='utf-8') as stream:
    for line in stream:
        if line and not line[0].isspace() and not line.startswith('#'):
            match=re.match(r'(.+) -> (.+):$',line.strip())
            if match:
                owner=match.group(2)
                if match.group(1)=='com.android.apksig.internal.x509.SubjectPublicKeyInfo':
                    model=owner
        if 'LauncherApkBuilderKt.signLauncherArchive(java.io.File,java.io.File,java.security.PrivateKey,java.security.cert.X509Certificate)' in line:
            item={'class':owner,'method':line.rsplit(' -> ',1)[1].strip()}
            if item not in methods:methods.append(item)
if not methods or not model:raise SystemExit('Signing entry point or ASN.1 model missing in mapping')
print(json.dumps({'methods':methods,'model':model}))
'@
$lookupJson = & $Python -c $lookup $Mapping
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve release signing methods' }
$names = $lookupJson | ConvertFrom-Json
if ($names.methods.Count -ne 1) { throw 'Ambiguous signing entry point; inspect the release mapping' }
$method = $names.methods[0]
foreach ($value in @($method.class, $method.method, $names.model)) {
    if ($value -notmatch '^[A-Za-z0-9_.$]+$') { throw 'Invalid mapped Java identifier' }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$classes = Join-Path $OutputDirectory 'classes'
$dex = Join-Path $OutputDirectory 'dex'
New-Item -ItemType Directory -Force -Path $classes, $dex | Out-Null
$androidJar = Join-Path $platform.FullName 'android.jar'
& $javac --release 17 -cp $androidJar -d $classes (Join-Path $PSScriptRoot 'tests/LauncherSigningReleaseCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Device harness compilation failed' }
& $java -cp (Join-Path $tools.FullName 'lib/d8.jar') com.android.tools.r8.D8 --release --min-api 31 `
    --lib $androidJar --output $dex (Join-Path $classes 'LauncherSigningReleaseCheck.class') `
    *> (Join-Path $OutputDirectory 'harness-build.log')
if ($LASTEXITCODE -ne 0) { throw 'Device harness DEX compilation failed' }
$archive = [IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $template = $archive.GetEntry('assets/launcher/template.apk')
    if (!$template) { throw 'Launcher template is missing from APK' }
    $input = $template.Open()
    $output = [IO.File]::Create((Join-Path $OutputDirectory 'unsigned.apk'))
    try { $input.CopyTo($output) } finally { $input.Dispose(); $output.Dispose() }
} finally { $archive.Dispose() }

$deviceDirectory = '/data/local/tmp/stersu-launcher-check-' + [Guid]::NewGuid().ToString('N')
try {
    & $adb @adbArguments shell "mkdir '$deviceDirectory'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare disposable device directory' }
    foreach ($pair in @(
        @((Join-Path $dex 'classes.dex'), 'check.dex'),
        @($Apk, 'manager.apk'),
        @((Join-Path $OutputDirectory 'unsigned.apk'), 'unsigned.apk')
    )) {
        & $adb @adbArguments push $pair[0] ($deviceDirectory + '/' + $pair[1])
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stage device signing test input' }
    }
    & $adb @adbArguments shell "chmod 444 '$deviceDirectory/check.dex' '$deviceDirectory/manager.apk'"
    $command = "CLASSPATH='$deviceDirectory/check.dex' app_process /system/bin LauncherSigningReleaseCheck " +
        "'$deviceDirectory' manager.apk '$($method.class)' '$($method.method)' '$($names.model)'"
    $diagnostic = @(& $adb @adbArguments shell $command 2>&1)
    $code = $LASTEXITCODE
    $diagnostic | Set-Content -LiteralPath (Join-Path $OutputDirectory 'device-signing.txt') -Encoding utf8
    $diagnostic | Write-Output
    if ($code -ne 0 -or ($diagnostic -join "`n") -notmatch 'Actual release DEX signing and verification PASSED' -or
        ($diagnostic -join "`n") -match 'not annotated|Caught a exception encoding') {
        throw 'Actual release signing or ASN.1 reflection regression failed; inspect device-signing.txt'
    }
    $signed = Join-Path $OutputDirectory 'device-signed.apk'
    & $adb @adbArguments pull "$deviceDirectory/release-signed.apk" $signed
    if ($LASTEXITCODE -ne 0) { throw 'Cannot retrieve signed test entry' }
    & $java -jar (Join-Path $tools.FullName 'lib/apksigner.jar') verify --verbose --min-sdk-version 31 $signed `
        *> (Join-Path $OutputDirectory 'device-apk-signature.txt')
    if ($LASTEXITCODE -ne 0) { throw 'SDK verifier rejected the device-signed entry' }
    Write-Output 'Release reflection, AndroidKeyStore signing and independent SDK verification passed.'
} finally {
    # Only these exact files in this test's unique directory are removed.
    & $adb @adbArguments shell "rm -f '$deviceDirectory/check.dex' '$deviceDirectory/manager.apk' '$deviceDirectory/unsigned.apk' '$deviceDirectory/release-signed.apk'"
    & $adb @adbArguments shell "rmdir '$deviceDirectory'"
}
