[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# ============================================
# Script de test Anti-IP Spoofing
# Application e-commerce Java TCP
# ============================================

$SERVER_HOST = "localhost"
$SERVER_PORT = 8080
$LOG_PATH = "D:\clones\vesion_alpha\logs\security-audit.log"

function Write-Title($text) {
    Write-Host ""
    Write-Host "==============================" -ForegroundColor Cyan
    Write-Host " $text" -ForegroundColor Cyan
    Write-Host "==============================" -ForegroundColor Cyan
}

function Write-Pass($text) {
    Write-Host "[PASS] $text" -ForegroundColor Green
}

function Write-Fail($text) {
    Write-Host "[FAIL] $text" -ForegroundColor Red
}

function Write-Info($text) {
    Write-Host "[INFO] $text" -ForegroundColor Yellow
}

# ============================================
# FONCTION : Lire les dernières lignes du log
# ============================================
function Get-LastLogs($count = 5) {
    if (Test-Path $LOG_PATH) {
        return Get-Content $LOG_PATH -Tail $count
    } else {
        Write-Info "Fichier log introuvable : $LOG_PATH"
        return @()
    }
}

# ============================================
# FONCTION : Vérifier si un mot-clé apparait
# dans les logs après un test
# ============================================
function Check-LogContains($keyword, $after) {
    Start-Sleep -Seconds 2
    if (Test-Path $LOG_PATH) {
        $lines = Get-Content $LOG_PATH | Where-Object { $_ -match $keyword }
        if ($lines.Count -gt 0) {
            return $true
        }
    }
    return $false
}

# ============================================
# TEST 1 : Connexion TCP simple (IpSpoofingDetector)
# ============================================
Write-Title "TEST 1 : Connexion TCP brute"
Write-Info "Tentative de connexion TCP sur ${SERVER_HOST}:${SERVER_PORT}"

try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.ConnectAsync($SERVER_HOST, $SERVER_PORT).Wait(3000) | Out-Null

    if ($client.Connected) {
        Write-Pass "Connexion TCP établie (serveur actif)"
        $client.Close()
    } else {
        Write-Fail "Connexion TCP échouée (serveur éteint ?)"
    }
} catch {
    Write-Fail "Erreur TCP : $_"
}

# ============================================
# TEST 2 : Vérifier que le log existe
# ============================================
Write-Title "TEST 2 : Vérification du fichier log"

if (Test-Path $LOG_PATH) {
    Write-Pass "security-audit.log trouvé"
    Write-Info "Dernières lignes du log :"
    Get-LastLogs 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
} else {
    Write-Fail "security-audit.log introuvable"
    Write-Info "Chemin vérifié : $LOG_PATH"
}
# ============================================
# TEST 3 : Connexions multiples rapides (SYN Flood / SecurityMonitor)
# ============================================
Write-Title "TEST 3 : Flood de connexions TCP (SecurityMonitor)"
Write-Info "Envoi de 15 connexions rapides..."

$before = (Get-Content $LOG_PATH | Measure-Object -Line).Lines

for ($i = 1; $i -le 15; $i++) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.ConnectAsync($SERVER_HOST, $SERVER_PORT).Wait(1000) | Out-Null
        $c.Close()
    } catch {}
    Start-Sleep -Milliseconds 50
}

Start-Sleep -Seconds 2
$newAlerts = (Get-Content $LOG_PATH | Select-String "Flood|DoS").Count

if ($newAlerts -gt 0) {
    Write-Pass "$newAlerts alertes flood detectees dans les logs"
} else {
    Write-Fail "Aucune alerte flood dans les logs"
}
# ============================================
# TEST 4 : Vérifier les mots-clés dans les logs
# ============================================
Write-Title "TEST 4 : Analyse des logs de sécurité"

$keywords = @(
    "ADMIN_EXTERNAL_IP_BLOCKED",
    "SESSION_IP_CHANGE_DETECTED",
    "UNRECOGNIZED_IP_2FA_REQUIRED",
    "TCP_CONNECT_INVALID_SOURCE",
    "TCP_CONNECT_NULL_SOCKET",
    "TCP_CONNECT_NO_ADDRESS",
    "TCP_CONNECT_EMPTY_IP"
)

if (Test-Path $LOG_PATH) {
    $content = Get-Content $LOG_PATH -Raw
    foreach ($kw in $keywords) {
        if ($content -match $kw) {
            Write-Pass "Trouvé dans les logs : $kw"
        } else {
            Write-Info "Pas encore déclenché : $kw"
        }
    }
} else {
    Write-Fail "Impossible de lire les logs"
}

# ============================================
# RÉSUMÉ
# ============================================
Write-Title "RÉSUMÉ"
Write-Host ""
Write-Host "Pour tester ADMIN_EXTERNAL_IP_BLOCKED :" -ForegroundColor White
Write-Host "  1. Dans ClientHandler.java, remplace getClientIpAddress() pour retourner '85.12.45.67'" -ForegroundColor Gray
Write-Host "  2. Lance ton client Java et connecte-toi en tant qu'admin" -ForegroundColor Gray
Write-Host "  3. Relance ce script pour voir le log" -ForegroundColor Gray
Write-Host ""
Write-Host "Pour voir les logs en temps réel :" -ForegroundColor White
Write-Host "  Get-Content '$LOG_PATH' -Wait" -ForegroundColor Gray
Write-Host ""
Write-Host "Script terminé." -ForegroundColor Cyan