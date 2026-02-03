$version = "1.0.0"
$appName = "Attendees Check App"
$msiPath = "composeApp\build\compose\binaries\main\msi\$appName-$version.msi"

Write-Host "Building MSI for Version $version..."
./gradlew :composeApp:packageMsi

if (Test-Path $msiPath) {
    $hash = Get-FileHash -Path $msiPath -Algorithm SHA256
    Write-Host "Build Success!" -ForegroundColor Green
    Write-Host "MSI Location: $msiPath"
    Write-Host "SHA256 Hash: $($hash.Hash)" -ForegroundColor Cyan
    Write-Host "`nUpdate scoop/au-streem.json with this hash!"
} else {
    Write-Host "Build Failed or MSI not found at expected path: $msiPath" -ForegroundColor Red
    # Try to find it if name differs
    $found = Get-ChildItem -Path "composeApp\build\compose\binaries\main\msi" -Filter "*.msi" -Recurse | Select-Object -First 1
    if ($found) {
        $hash = Get-FileHash -Path $found.FullName -Algorithm SHA256
        Write-Host "Found MSI at: $($found.FullName)"
        Write-Host "SHA256 Hash: $($hash.Hash)" -ForegroundColor Cyan
    }
}
