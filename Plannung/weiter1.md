# VillagerAI – Aktuelle Arbeitsnotiz

Diese Datei ersetzt die fruehere lose Alt-Zusammenfassung und spiegelt den aktuellen Stand kompakt wider.

## Aktueller Kernstand

- Plugin spricht ueber eine lokale HTTP-Bridge mit umschaltbaren Providern `ollama` und `deepseek`.
- Normale Villager besitzen stabile Sprecher-IDs, Berufsprofile und koennen funktional als Questgeber dienen.
- Der erweiterte Dorfkontext liefert aktuell:
  - Dorfname
  - Dorfbeschreibung
  - grobe Dorfmerkmale
  - Dorfbiom
  - geschaetzte Bewohnerzahl
  - wichtiges Dorfereignis
- `/chief debug` und `/chief debug watch` zeigen diesen Kontext bereits im Spiel.

## Laufzeit-Modellpfad

- Das Plugin bleibt auf `ai.provider: http`.
- Die eigentliche Modellauswahl passiert in `chief-ai-service/config.json`.
- DeepSeek laeuft ueber den Bridge-Dienst mit `DEEPSEEK_API_KEY` aus der systemd-Umgebung.

## Naechste offene Codepunkte

- Phase 5 ist jetzt bis auf weitere spaetere Welt-/Historien-Signale praktisch abgeschlossen.
- Offene groessere Themen liegen aktuell eher bei:
  - weiterem Smalltalk-Feinschliff fuer normale Villager
  - spaeterer Anfrage-Queue in Phase 5b
  - mehr Questtypen und Reward-Breite
  - spaeteren Wissenspaketen fuer glaubwuerdiges Minecraft-Weltwissen

## Wichtiger Betriebsunterschied

- Neues Plugin-JAR: braucht Paper-/Crafty-Neustart.
- Neue YAML-/Config-Daten: oft per `/chief reload` ausreichend.
- Neuer Bridge-Python-Code unter `/opt/villagerai/chief-ai-service/chief_ai_service/`: braucht Bridge-Neustart via `systemctl restart villagerai-chief`.
