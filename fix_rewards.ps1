$path = 'src/main/resources/quest-rewards.yml'
$content = Get-Content $path -Raw

# Retinue-Block nach explore einfuegen, doppeltes secure entfernen
$retinue = @"

  retinue:
    experience-points: 50
    emeralds: 10
    reputation-boost: 10
    bonus-items:
      - material: DIAMOND
        amount: 3
        quality-tiers:
          - EMERALD
          - NETHERITE_SCRAP
      - material: ENCHANTED_GOLDEN_APPLE
        amount: 2
        quality-tiers:
          - TOTEM_OF_UNDYING
      - reward-type: RANDOM_ENCHANTED_BOOK
        amount: 2
        enchantments:
          - mending
          - unbreaking
          - protection
          - sharpness
"@

$legendary = @"

  legendary:
    experience-points: 100
    emeralds: 16
    reputation-boost: 25
    bonus-items:
      - material: ELYTRA
        amount: 1
      - material: ENCHANTED_GOLDEN_APPLE
        amount: 2
        quality-tiers:
          - TOTEM_OF_UNDYING
      - material: NETHERITE_SWORD
        amount: 1
      - material: NETHERITE_INGOT
        amount: 3
        quality-tiers:
          - NETHERITE_BLOCK
      - material: BEACON
        amount: 1
      - reward-type: RANDOM_ENCHANTED_BOOK
        amount: 3
        enchantments:
          - mending
          - unbreaking
          - protection
          - sharpness
          - efficiency
          - fortune
          - looting
          - sweeping_edge
"@

# Zweites secure (nach explore) entfernen
$lines = $content -split "`n"
$out = @()
$inSecondSecure = $false
$skipUntilNextKey = $false
foreach ($line in $lines) {
    if ($line -match '^  secure:' -and $inSecondSecure) {
        $skipUntilNextKey = $true
        continue
    }
    if ($line -match '^  secure:') {
        $inSecondSecure = $true
        $out += $line
        continue
    }
    if ($skipUntilNextKey) {
        if ($line -match '^  [a-z]') { $skipUntilNextKey = $false; $out += $retinue; $out += $legendary; $out += $line }
        continue
    }
    $out += $line
}
$out -join "`n" | Set-Content $path -NoNewline
