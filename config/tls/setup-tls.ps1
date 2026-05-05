# =============================================================================
# ChriOnline - TLS / TrustStore bootstrap (Task 1)
# =============================================================================
# Reproduit, de maniere automatique, les 4 etapes documentees dans le diagramme :
#
#  1. Exporter le certificat serveur avec keytool (-exportcert)
#  2. Creer le TrustStore cote client et importer le certificat
#  3. (Le client utilise ensuite ce TrustStore via TlsSupport.buildClientContext)
#  4. (Le client etablit la connexion SSLSocket via TlsSupport.createClientSocket)
#
# Pre-requis :
#   - keytool sur le PATH (fourni par tout JDK)
#   - PowerShell 5+ ou PowerShell Core 7+
#
# Usage :
#   powershell -ExecutionPolicy Bypass -File config/tls/setup-tls.ps1
#
# Variables :
#   - $StorePass : mot de passe utilise pour les deux stores (defaut : changeit)
#   - $Validity  : validite du certificat en jours (defaut : 365)
# =============================================================================

[CmdletBinding()]
param(
    [string]$StorePass = "changeit",
    [int]$Validity     = 365,
    [string]$Hostname  = "localhost"
)

$ErrorActionPreference = "Stop"

$root        = Split-Path -Parent $PSScriptRoot
$tlsDir      = Join-Path $root "config\tls"
$keystore    = Join-Path $tlsDir "server-keystore.p12"
$truststore  = Join-Path $tlsDir "client-truststore.p12"
$certFile    = Join-Path $tlsDir "server-cert.pem"

if (-not (Test-Path $tlsDir)) {
    New-Item -ItemType Directory -Path $tlsDir | Out-Null
}

Write-Host "==> [1/4] Generation du keystore serveur (RSA 2048, SAN=$Hostname)" -ForegroundColor Cyan
if (Test-Path $keystore) {
    Write-Host "    keystore deja present : $keystore (suppression)" -ForegroundColor Yellow
    Remove-Item $keystore -Force
}
keytool -genkeypair `
    -alias chrionline-server `
    -keyalg RSA -keysize 2048 `
    -dname "CN=$Hostname, OU=ChriOnline, O=ENSATe, L=Tetouan, C=MA" `
    -validity $Validity `
    -keystore $keystore `
    -storetype PKCS12 `
    -storepass $StorePass `
    -keypass $StorePass `
    -ext "SAN=DNS:$Hostname,IP:127.0.0.1"

Write-Host ""
Write-Host "==> [2/4] Export du certificat serveur (keytool -exportcert)" -ForegroundColor Cyan
if (Test-Path $certFile) { Remove-Item $certFile -Force }
keytool -exportcert `
    -alias chrionline-server `
    -file $certFile `
    -keystore $keystore `
    -storepass $StorePass `
    -rfc

Write-Host ""
Write-Host "==> [3/4] Creation du TrustStore client + import du certificat" -ForegroundColor Cyan
if (Test-Path $truststore) {
    Write-Host "    truststore deja present : $truststore (suppression)" -ForegroundColor Yellow
    Remove-Item $truststore -Force
}
keytool -importcert `
    -alias chrionline-server `
    -file $certFile `
    -keystore $truststore `
    -storetype PKCS12 `
    -storepass $StorePass `
    -noprompt

Write-Host ""
Write-Host "==> [4/4] Verification du contenu du TrustStore" -ForegroundColor Cyan
keytool -list -v -keystore $truststore -storepass $StorePass | Select-Object -First 30

Write-Host ""
Write-Host "TLS bootstrap termine avec succes." -ForegroundColor Green
Write-Host " - keystore   : $keystore"
Write-Host " - truststore : $truststore"
Write-Host " - cert (PEM) : $certFile"
Write-Host ""
Write-Host "Le client lit ces fichiers via les variables CHRIONLINE_TLS_* (.env)."
Write-Host "TlsSupport.createClientSocket(...) configure ensuite SSLContext + SSLSocket."
