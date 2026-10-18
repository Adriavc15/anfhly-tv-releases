# Script para compilar y publicar automáticamente Anfhly TV en GitHub Releases
param(
    [string]$Version = "",
    [string]$Changelog = "",
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Publicador de Releases para Anfhly TV  " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Leer update.json para obtener versión si no se especificó
$updateJsonPath = "app/update.json"
if (Test-Path $updateJsonPath) {
    $updateData = Get-Content $updateJsonPath -Raw | ConvertFrom-Json
    if (-not $Version) { $Version = $updateData.versionName }
    if (-not $Changelog) { $Changelog = $updateData.changelog }
}

if (-not $Version) {
    $Version = "1.0.0"
}
$tagName = "v$Version"

Write-Host "-> Versión de la Release: $tagName" -ForegroundColor Yellow

# 2. Compilar APK si no se indicó omitir
$apkPath = "app/build/outputs/apk/debug/Anfhly Tv.apk"
if (-not $SkipBuild -or (-not (Test-Path $apkPath))) {
    Write-Host "-> Compilando APK debug (Anfhly Tv.apk)..." -ForegroundColor Yellow
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Fallo en la compilación de Gradle."
        exit 1
    }
}

if (-not (Test-Path $apkPath)) {
    Write-Error "No se encontró el archivo APK en: $apkPath"
    exit 1
}

$apkSizeMB = [math]::Round(((Get-Item $apkPath).Length / 1MB), 2)
Write-Host "-> APK listo: $apkPath ($apkSizeMB MB)" -ForegroundColor Green

# 3. Obtener credenciales de GitHub desde Git Credential Manager
Write-Host "-> Obteniendo autenticación con GitHub..." -ForegroundColor Yellow
$credsRaw = ("protocol=https`nhost=github.com`nusername=Adriavc15`n`n" | git credential fill)
$token = ($credsRaw | Select-String -Pattern "password=(.*)").Matches.Groups[1].Value

if (-not $token) {
    Write-Error "No se pudo obtener el token de autenticación de GitHub desde Git Credential Manager."
    exit 1
}

$headers = @{
    "Authorization" = "token $token"
    "User-Agent" = "AnfhlyTV-ReleasePublisher"
    "Accept" = "application/vnd.github.v3+json"
}

$repoOwner = "Adriavc15"
$repoName = "ANFHLYTV"

# 4. Verificar si ya existe un release con esta etiqueta
Write-Host "-> Verificando release en GitHub..." -ForegroundColor Yellow
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$tagName" -Headers $headers -Method Get
    Write-Host "-> Release $tagName existente encontrado (ID: $($release.id))." -ForegroundColor Cyan
} catch {
    # No existe todavía
}

if (-not $release) {
    Write-Host "-> Creando nueva release $tagName en GitHub..." -ForegroundColor Yellow
    $releaseData = [ordered]@{
        tag_name = $tagName
        target_commitish = "main"
        name = "Anfhly TV $tagName"
        body = if ($Changelog) { $Changelog } else { "Nueva versión de Anfhly TV ($tagName)." }
        draft = $false
        prerelease = $false
    }
    $jsonBytes = [System.Text.Encoding]::UTF8.GetBytes(($releaseData | ConvertTo-Json))
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Method Post -Headers $headers -Body $jsonBytes -ContentType "application/json; charset=utf-8"
    Write-Host "-> Release creada exitosamente (ID: $($release.id))." -ForegroundColor Green
}

# 5. Si ya había un asset con el mismo nombre, eliminarlo primero
$assetName = "Anfhly.Tv.apk"
$existingAsset = $release.assets | Where-Object { $_.name -eq $assetName -or $_.name -eq "Anfhly Tv.apk" }
if ($existingAsset) {
    Write-Host "-> Reemplazando asset existente $($existingAsset.id)..." -ForegroundColor Yellow
    Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/assets/$($existingAsset.id)" -Headers $headers -Method Delete
}

# 6. Subir Anfhly Tv.apk a la Release
Write-Host "-> Subiendo APK a GitHub Releases ($apkSizeMB MB)..." -ForegroundColor Yellow
$uploadUri = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets?name=$assetName"
$uploadedAsset = Invoke-RestMethod -Uri $uploadUri -Method Post -Headers $headers -InFile $apkPath -ContentType "application/vnd.android.package-archive" -TimeoutSec 300

Write-Host "==========================================" -ForegroundColor Green
Write-Host "¡Publicación completada con éxito!" -ForegroundColor Green
Write-Host "Versión: $tagName" -ForegroundColor Green
Write-Host "Descarga pública directa:" -ForegroundColor Cyan
Write-Host $uploadedAsset.browser_download_url -ForegroundColor White
Write-Host "==========================================" -ForegroundColor Green
