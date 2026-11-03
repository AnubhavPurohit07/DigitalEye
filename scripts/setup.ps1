# DigitalEye one-time setup.
# Asks for your passwords and writes the three PRIVATE settings files:
#   digitaleye-database\src\main\resources\db.properties
#   digitaleye-backend\src\main\resources\db.properties
#   digitaleye-agent\src\main\resources\agent.properties
# These files are ignored by git, so your passwords are never uploaded.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Read-Secret([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    return $plain
}

function Read-Password([string]$prompt) {
    while ($true) {
        $p = Read-Secret $prompt
        if ([string]::IsNullOrEmpty($p)) { Write-Host 'The password cannot be empty.'; continue }
        if ($p -match '[^\x20-\x7E]') { Write-Host 'Use only plain English letters, digits and symbols (no accents or emoji).'; continue }
        if ($p -ne $p.Trim()) { Write-Host 'The password must not start or end with a space.'; continue }
        return $p
    }
}

# In a .properties file a backslash must be written twice.
function Escape-Prop([string]$v) { return $v.Replace('\', '\\') }

function Confirm-Overwrite([string]$path) {
    if (-not (Test-Path $path)) { return $true }
    $shown = $path.Replace($root + '\', '')
    $answer = Read-Host ($shown + ' already exists. Replace it? (y/N)')
    return ($answer -eq 'y' -or $answer -eq 'Y')
}

function Save-Text([string]$path, [string[]]$lines) {
    [IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), $utf8)
}

Write-Host ''
Write-Host '=== DigitalEye setup ==='
Write-Host 'Your typing is hidden when you enter a password. Nothing is sent anywhere.'
Write-Host ''
Write-Host '1 of 2 - MySQL (the password you set when you installed MySQL)'
$dbUser = Read-Host 'MySQL username [root]'
if ([string]::IsNullOrWhiteSpace($dbUser)) { $dbUser = 'root' }
$dbPass = Read-Password 'MySQL password'

$dbLines = @(
    '# Private settings. Never commit this file (it is ignored by git).',
    'db.url=jdbc:mysql://localhost:3306/digitaleye',
    ('db.username=' + $dbUser),
    ('db.password=' + (Escape-Prop $dbPass))
)
foreach ($module in @('digitaleye-database', 'digitaleye-backend')) {
    $dir = Join-Path $root ($module + '\src\main\resources')
    $path = Join-Path $dir 'db.properties'
    if (Confirm-Overwrite $path) {
        Save-Text $path $dbLines
        Write-Host ('Wrote ' + $module + '\src\main\resources\db.properties')
    }
}

Write-Host ''
Write-Host '2 of 2 - the login the agent uses to talk to the backend'
Write-Host '(Choose any username and password. scripts\create-users.cmd creates this account later.)'
$agentUser = Read-Host 'Agent username [employee1]'
if ([string]::IsNullOrWhiteSpace($agentUser)) { $agentUser = 'employee1' }
$agentPass = Read-Password 'Agent password'

$agentDir = Join-Path $root 'digitaleye-agent\src\main\resources'
$example = Join-Path $agentDir 'agent.properties.example'
$agentFile = Join-Path $agentDir 'agent.properties'
if (Confirm-Overwrite $agentFile) {
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in [IO.File]::ReadAllLines($example)) {
        if ($line -like 'agent.username=*') { $out.Add('agent.username=' + $agentUser) }
        elseif ($line -like 'agent.password=*') { $out.Add('agent.password=' + (Escape-Prop $agentPass)) }
        else { $out.Add($line) }
    }
    Save-Text $agentFile $out.ToArray()
    Write-Host 'Wrote digitaleye-agent\src\main\resources\agent.properties'
}

Write-Host ''
Write-Host 'Setup finished. Next: scripts\create-database.cmd'
