# Deployment: prompt_builder.py (komprimierte Regeln)
# Ziel: Bridge-Service auf 10.0.0.86
Set-Location $PSScriptRoot

Write-Host "1/4 Kopiere prompt_builder.py nach /tmp auf Zielhost..."
scp "chief-ai-service\chief_ai_service\prompt_builder.py" mc@10.0.0.86:"/tmp/prompt_builder.py"

Write-Host "2/4 Ersetze Produktivdatei aus /tmp..."
ssh mc@10.0.0.86 "cp /tmp/prompt_builder.py /opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py"

Write-Host "3/4 Berechtigungen setzen..."
ssh mc@10.0.0.86 "chmod 644 /opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py"

Write-Host "4/4 Bridge-Service neustarten..."
ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"

Write-Host "Deployment abgeschlossen."