---
title: "ConversationService: realChief aus ChiefAttributes bauen"
status: in-progress
---

## Auftrag
In `ConversationService.java` die Stelle korrigieren, wo `realChief` aus `findActiveByVillageId` abgeleitet wird:

```java
Chief realChief = session.chief().isChief()
    ? session.chief()
    : chiefRepository.findActiveByVillageId(session.chief().villageId())
            .map(attrs -> session.chief())  // ← falsch, müsste toChief(attrs, villager) sein
            .orElse(null);
```

Da `toChief()` noch in ChiefService fehlt (09a), hier zunächst den Workaround belassen ODER durch null ersetzen, wenn der Session-Chief kein Chief ist.

## Betroffen
- `src/main/java/de/ajsch/villagerai/service/ConversationService.java`

## ToDo
1. `realChief`-Logik prüfen: `.map(attrs -> session.chief())` ist Unsinn – wenn kein Chief, dann null
2. Build mit `.\gradlew.bat compileJava`