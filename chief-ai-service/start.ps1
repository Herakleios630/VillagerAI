$pythonCommand = $null

if (Get-Command py -ErrorAction SilentlyContinue) {
    $pythonCommand = "py"
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    $pythonCommand = "python"
}

if (-not $pythonCommand) {
    Write-Error "Python wurde nicht gefunden. Installiere Python 3 oder aktiviere den Python-Launcher."
    exit 1
}

& $pythonCommand .\server.py
