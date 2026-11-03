# Creates the two accounts DigitalEye needs, through the running backend:
#   1. an ADMIN account  - you log in to the dashboard with it
#   2. a USER account    - the agent logs in with it (username/password from agent.properties)
# The backend must be running (scripts\start-backend.cmd) before you run this.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

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
        if ($p -match '[^\x20-\x7E]') { Write-Host 'Use only plain English letters, digits and symbols.'; continue }
        return $p
    }
}

function Register-Account([string]$baseUrl, [string]$user, [string]$pass, [string]$fullName, [string]$email, [string]$role) {
    $body = @{ username = $user; password = $pass; fullName = $fullName; email = $email; role = $role } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri ($baseUrl + '/api/auth/register') -Method Post -ContentType 'application/json' -Body $body | Out-Null
        Write-Host ("Created the " + $role + " account '" + $user + "'.")
    }
    catch {
        Write-Host ("Could not create '" + $user + "': " + $_.Exception.Message)
        Write-Host '  If that account already exists, this is fine: just use it.'
        Write-Host '  If the message says the connection failed, start the backend first (scripts\start-backend.cmd).'
    }
}

$agentFile = Join-Path $root 'digitaleye-agent\src\main\resources\agent.properties'
if (-not (Test-Path $agentFile)) {
    Write-Host 'agent.properties was not found. Run scripts\setup.cmd first.'
    exit 1
}

$props = @{}
foreach ($line in [IO.File]::ReadAllLines($agentFile)) {
    if ($line -match '^\s*([A-Za-z0-9_.\-]+)\s*=(.*)$') { $props[$Matches[1]] = $Matches[2] }
}
$baseUrl = $props['backend.url']
if ([string]::IsNullOrWhiteSpace($baseUrl)) { $baseUrl = 'http://localhost:9090' }
$baseUrl = $baseUrl.Trim().TrimEnd('/')
$agentUser = $props['agent.username']
$agentPass = $props['agent.password']
if ([string]::IsNullOrEmpty($agentUser) -or [string]::IsNullOrEmpty($agentPass) -or $agentPass -eq 'CHANGE_ME') {
    Write-Host 'agent.properties still has the placeholder password. Run scripts\setup.cmd first.'
    exit 1
}
$agentPass = $agentPass.Replace('\\', '\')

Write-Host ''
Write-Host ('Backend: ' + $baseUrl)
Write-Host ''
Write-Host 'Admin account (you will use it to log in to the dashboard)'
$adminUser = Read-Host 'Admin username [admin]'
if ([string]::IsNullOrWhiteSpace($adminUser)) { $adminUser = 'admin' }
$adminPass = Read-Password 'Admin password'

Register-Account $baseUrl $adminUser $adminPass 'Administrator' ($adminUser + '@digitaleye.local') 'ADMIN'
Register-Account $baseUrl $agentUser $agentPass 'Monitored Employee' ($agentUser + '@digitaleye.local') 'USER'

Write-Host ''
Write-Host 'Done. Next: scripts\start-dashboard.cmd and scripts\start-agent.cmd'
