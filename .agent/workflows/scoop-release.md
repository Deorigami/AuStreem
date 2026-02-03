---
description: Build and release a new version of AuStreem to Scoop
---

### 1. Build the MSI
// turbo
Run the Gradle task to generate the MSI installer:
```powershell
./gradlew :composeApp:packageReleaseMsi
```

### 2. Verify and Update Scoop Manifest
// turbo
Run the release helper script to calculate the hash and update `scoop/au-streem.json`:
```powershell
powershell -ExecutionPolicy Bypass -File scoop/build_release.ps1
```

### 3. Upload to GitHub
1. Go to [GitHub Releases](https://github.com/Deorigami/AuStreem/releases).
2. Create a new release (or edit existing v0.0.1).
3. Upload the MSI from `composeApp\build\compose\binaries\main-release\msi\AuStreem-0.0.1.msi`.
4. Publish the release.

### 4. Push Manifest Changes
// turbo
Commit and push the updated manifest:
```powershell
git add scoop/au-streem.json
git commit -m "chore: update scoop manifest hash"
git push
```

### 5. Install/Update via Scoop
To test locally:
```powershell
scoop install ./scoop/au-streem.json
```
After pushing to GitHub, you can add your repo as a bucket:
```powershell
scoop bucket add austreem https://github.com/Deorigami/AuStreem
scoop install austreem/au-streem
```
