"$file = \"src\\main\\java\\de\\ajsch\\villagerai\\service\\ConversationService.java\"
$content = Get-Content $file -Raw -Encoding UTF8

$oldBlock = @'
                plugin.getConfig().getStringList(\"memory.trigger-fallback-phrases\"),
                isSmalltalk);
'@

$newBlock = @'
                plugin.getConfig().getStringList(\"memory.trigger-fallback-phrases\"),
                isSmalltalk,
                session.visibility());
'@

if ($content.Contains($oldBlock)) {
    $content = $content.Replace($oldBlock, $newBlock)
    $content | Set-Content $file -Encoding UTF8 -NoNewline
    Write-Host \"[OK] AIRequest call updated with conversationVisibility\"
} else {
    Write-Host \"[FAIL] AIRequest call block not found\"
    # Search for isSmalltalk to debug
    $lines = $content -split \"`n\"
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match 'isSmalltalk') {
            Write-Host \"  Found 'isSmalltalk' at line $($i+1): $($lines[$i].Trim())\"
            if ($i+1 -lt $lines.Count) {
                Write-Host \"  Next line: $($lines[$i+1].Trim())\"
            }
        }
    }
}
"