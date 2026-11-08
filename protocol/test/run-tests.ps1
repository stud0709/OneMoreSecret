param([string]$Fixtures = "$PSScriptRoot/../test-vectors/fixtures.json")
$ErrorActionPreference = 'Stop'
$omsSource = "$PSScriptRoot/../../app/src/main/java/org/onemoresecret/protocol"
if (!(Test-Path -LiteralPath $omsSource)) { $omsSource = "$PSScriptRoot/../../omscompanion/src/main/java/org/onemoresecret/protocol" }
$omsOutput = Join-Path ([System.IO.Path]::GetTempPath()) ('oms-protocol-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $omsOutput | Out-Null
try {
    javac --release 17 -d $omsOutput "$omsSource/OmsProtocol.java" "$omsSource/NostrTransport.java" "$omsSource/BackupCodec.java" "$omsSource/NostrPairing.java" "$PSScriptRoot/ProtocolTest.java"
    if ($LASTEXITCODE -ne 0) { throw 'Protocol test compilation failed' }
    java -cp $omsOutput ProtocolTest --verify $Fixtures
    if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }
} finally {
    $omsResolved = [System.IO.Path]::GetFullPath($omsOutput)
    if (!$omsResolved.StartsWith([System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())) -or [System.IO.Path]::GetFileName($omsResolved) -notlike 'oms-protocol-*') { throw 'Unexpected temporary output path' }
    Remove-Item -LiteralPath $omsResolved -Force -Recurse
}
