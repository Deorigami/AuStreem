$version = "1.0.0"
$appName = "AuStreem"
$msiPath = "composeApp\build\compose\binaries\main-release\msi\$appName-$version.msi"

Write-Host "--- AuStreem Scoop Release Helper ---"
Write-Host "Looking for MSI at: $msiPath"

if (Test-Path $msiPath) {
    $hash = (Get-FileHash -Path $msiPath -Algorithm SHA256).Hash
    Write-Host "`nBuild Verification Success!" -ForegroundColor Green
    Write-Host "SHA256 Hash: $hash" -ForegroundColor Cyan
    
    Write-Host "`nNext Steps:"
    Write-Host "1. Upload the MSI to GitHub Release v$version"
    Write-Host "2. Ensure scoop/au-streem.json has version '$version' and this hash."
    Write-Host "3. Push changes to GitHub."
    
    # Check if manifest matches
    $manifestPath = "scoop/au-streem.json"
    if (Test-Path $manifestPath) {
        $manifestContent = Get-Content $manifestPath -Raw | ConvertFrom-Json
        if ($manifestContent.hash -eq $hash) {
            Write-Host "`n[v] Manifest hash matches!" -ForegroundColor Green
        } else {
            Write-Host "`n[!] Manifest hash does NOT match. Updating manifest..." -ForegroundColor Yellow
            $manifestContent.hash = $hash
            $manifestContent | ConvertTo-Json -Depth 20 | Set-Content $manifestPath
            Write-Host "[v] Manifest updated with new hash." -ForegroundColor Green
        }
    }
} else {
    Write-Host "`n[!] MSI not found. Please build it from the IDE (packageReleaseMsi) first." -ForegroundColor Red
}
