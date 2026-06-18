$file = "src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java"
$content = [System.IO.File]::ReadAllText((Resolve-Path $file).Path)
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Resolve-Path $file).Path, $content, $utf8NoBom)
Write-Host "BOM removed from $file"
