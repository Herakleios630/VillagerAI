#!/usr/bin/env python3
"""Apply chiefLocation changes to ConversationService.java"""
import pathlib

filepath = pathlib.Path("src/main/java/de/ajsch/villagerai/service/ConversationService.java")
content = filepath.read_text(encoding="utf-8")

old_block = """        String combinedReputationSummary = reputationService.getCombinedSummary(
            playerUuid,
            session.chief().villageId(),
            session.chief().chiefId());

        AIRequest request = new AIRequest("""

new_block = """        String combinedReputationSummary = reputationService.getCombinedSummary(
            playerUuid,
            session.chief().villageId(),
            session.chief().chiefId());

        String chiefLocation = "Der Häuptling " + session.chief().displayName()
            + " (" + session.chief().role() + ") steht bei "
            + (int) session.chief().x() + ", "
            + (int) session.chief().y() + ", "
            + (int) session.chief().z()
            + " in der Welt " + session.chief().world() + ".";

        AIRequest request = new AIRequest("""

if old_block not in content:
    print("ERROR: old_block not found!")
else:
    content = content.replace(old_block, new_block, 1)
    filepath.write_text(content, encoding="utf-8")
    print("Done: chiefLocation variable inserted.")

# Now add chiefLocation argument in the AIRequest constructor
content = filepath.read_text(encoding="utf-8")

# Find the line after villageMourning that now needs chiefLocation
old_arg = "mourningService.isVillageInMourning(session.chief().villageId()),\n                playerUuid,"
new_arg = "mourningService.isVillageInMourning(session.chief().villageId()),\n                chiefLocation,\n                playerUuid,"

if old_arg not in content:
    print("ERROR: old_arg not found!")
else:
    content = content.replace(old_arg, new_arg, 1)
    filepath.write_text(content, encoding="utf-8")
    print("Done: chiefLocation argument inserted into AIRequest constructor.")