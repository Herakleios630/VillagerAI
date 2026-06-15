"""Unit tests for prompt_builder.py.

Covers:
- Prompt ohne Memory-Treffer → Memories-Sektion fehlt
- Prompt mit Memory-Treffer → 3 Erinnerungen eingefügt
- Prompt mit Summary → Summary-Sektion vorhanden
- Prompt ohne Summary → Summary-Sektion fehlt
- Sektionsreihenfolge: Ground-Truth → Persönlichkeit → Dorf-Details → Ruf → Status → Knowledge → [Fakten] → [Memories] → [Summary] → Regeln → Spieler-Nachricht
- Leere Sektionen werden nicht gerendert
- Prompt-Gesamtlänge wird geloggt
"""

import unittest
import sys
import os
import logging

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from chief_ai_service import prompt_builder


class CheckMemoryTriggerTests(unittest.TestCase):
    """Tests for check_memory_trigger()."""

    SAMPLE_TRIGGERS = [
        "erinnerst du dich",
        "weißt du noch",
        "damals",
        "vorhin",
        "vergessen",
        "letztes mal",
        "neulich",
        "kennst du mich noch",
    ]

    def test_trigger_match_returns_true(self):
        # All these messages should trigger
        triggers = self.SAMPLE_TRIGGERS
        self.assertTrue(
            prompt_builder.check_memory_trigger(
                "erinnerst du dich an das Geschenk?", triggers
            )
        )
        self.assertTrue(
            prompt_builder.check_memory_trigger(
                "Weißt du noch, was ich gesagt habe?", triggers
            )
        )
        self.assertTrue(
            prompt_builder.check_memory_trigger(
                "das war schon damals so", triggers
            )
        )

    def test_no_trigger_returns_false(self):
        triggers = self.SAMPLE_TRIGGERS
        self.assertFalse(
            prompt_builder.check_memory_trigger("Hallo, guten Tag!", triggers)
        )
        self.assertFalse(
            prompt_builder.check_memory_trigger("Was kostet das Brot?", triggers)
        )
        self.assertFalse(
            prompt_builder.check_memory_trigger("gib mir einen Auftrag", triggers)
        )

    def test_empty_input_returns_false(self):
        triggers = self.SAMPLE_TRIGGERS
        self.assertFalse(
            prompt_builder.check_memory_trigger("", triggers)
        )
        self.assertFalse(
            prompt_builder.check_memory_trigger("   ", triggers)
        )
        self.assertFalse(
            prompt_builder.check_memory_trigger("any", [])
        )
        self.assertFalse(
            prompt_builder.check_memory_trigger("any", None)
        )

    def test_case_insensitive_matching(self):
        triggers = self.SAMPLE_TRIGGERS
        self.assertTrue(
            prompt_builder.check_memory_trigger("ERINNERST DU DICH", triggers)
        )
        self.assertTrue(
            prompt_builder.check_memory_trigger("Weißt Du NoCh?", triggers)
        )

    def test_subword_boundary_matches(self):
        """'vergessen' should match inside 'vergessenheit'? Yes, re.search
        with escaped pattern will match anywhere in string. That's fine."""
        triggers = self.SAMPLE_TRIGGERS
        self.assertTrue(
            prompt_builder.check_memory_trigger("ich habe es vergessenheit", triggers)
        )

    def test_special_characters_in_phrases(self):
        triggers = ["was hast du?", "wie geht's"]
        self.assertTrue(
            prompt_builder.check_memory_trigger(
                "sag mal, was hast du? ich warte", triggers
            )
        )
        self.assertTrue(
            prompt_builder.check_memory_trigger("wie geht's dir heute?", triggers)
        )


class BuildContextPromptWithoutMemoryTests(unittest.TestCase):
    """Tests for build_context_prompt() WITHOUT memories or summary."""

    @classmethod
    def setUpClass(cls):
        cls.minimal_payload = {
            "chiefId": "chief-1",
            "villageId": "v-1",
            "chiefName": "Gerald",
            "chiefRole": "Dorfhaeuptling",
            "chiefPersonality": "grimmig",
            "chiefGreeting": "Nanu?",
            "villageBiome": "PLAINS",
            "villageName": "Weidenfeld",
            "villagePopulationEstimate": 12,
            "villageEventSummary": "kein Ereignis",
            "villageAttributes": "klein, freundlich",
            "villagerProfession": "NONE",
            "villagerType": "PLAINS",
            "currentBiome": "PLAINS",
            "worldName": "world",
            "isDay": True,
            "isRaining": False,
            "isThundering": False,
            "playerMessage": "Hallo, wer bist du?",
            "playerUuid": "uuid-123",
        }
        # Empty config with knowledge packets
        cls.empty_config = {
            "knowledge_packets": {
                "always": ["Ein Dorfbewohner kennt sein Handwerk."],
            }
        }

    def test_prompt_has_no_memories_section_without_memories(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload, self.empty_config
        )
        self.assertNotIn("--- Memories ---", prompt)
        self.assertNotIn("Erinnerungen:", prompt)

    def test_prompt_has_no_summary_section_without_summary(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload, self.empty_config
        )
        self.assertNotIn("--- Summary ---", prompt)

    def test_prompt_has_minimal_sections(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload, self.empty_config
        )
        self.assertIn("--- Ground-Truth ---", prompt)
        self.assertIn("--- Knowledge ---", prompt)
        self.assertIn("--- Dorf-Details ---", prompt)
        self.assertIn("--- Persoenlichkeit ---", prompt)
        self.assertIn("--- Ruf ---", prompt)
        self.assertIn("--- Status ---", prompt)
        self.assertIn("--- Regeln ---", prompt)
        self.assertIn("--- Spieler-Nachricht ---", prompt)

    def test_section_order_is_correct(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload, self.empty_config
        )
        # Check order of sections: Ground-Truth -> Persoenlichkeit -> Dorf-Info -> Ruf -> Status -> Knowledge -> Regeln -> Spieler-Nachricht
        sections = [
            "--- Ground-Truth ---",
            "--- Persoenlichkeit ---",
            "--- Dorf-Details ---",
            "--- Ruf ---",
            "--- Status ---",
            "--- Knowledge ---",
            "--- Regeln ---",
            "--- Spieler-Nachricht ---",
        ]
        positions = []
        for sec in sections:
            pos = prompt.find(sec)
            self.assertNotEqual(pos, -1, f"Missing section: {sec}")
            positions.append(pos)
        self.assertEqual(
            positions,
            sorted(positions),
            f"Sections are not in expected order! Positions: {positions}\nSections: {sections}\nPrompt:\n{prompt}",
        )

    def test_prompt_contains_player_message(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload, self.empty_config
        )
        self.assertIn("--- Spieler-Nachricht ---", prompt)
        self.assertIn("Hallo, wer bist du?", prompt)

    def test_empty_player_message_section_present(self):
        payload = dict(self.minimal_payload)
        payload["playerMessage"] = ""
        prompt = prompt_builder.build_context_prompt(payload, self.empty_config)
        # Spieler-Nachricht section exists but body is empty, so it should be skipped
        self.assertNotIn("--- Spieler-Nachricht ---", prompt,
                         "Empty Spieler-Nachricht section should not render")

    def test_empty_summary_not_rendered(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            summary_text="",
        )
        self.assertNotIn("--- Summary ---", prompt)

    def test_summary_none_not_rendered(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            summary_text=None,
        )
        self.assertNotIn("--- Summary ---", prompt)

    def test_prompt_length_is_logged(self):
        with self.assertLogs(
            "chief_ai_service.prompt_builder", level="INFO"
        ) as log_ctx:
            prompt_builder.build_context_prompt(
                self.minimal_payload, self.empty_config
            )
        self.assertTrue(
            any("Prompt length:" in msg for msg in log_ctx.output),
            f"Expected 'Prompt length:' in log, got: {log_ctx.output}",
        )


class BuildContextPromptWithMemoryTests(unittest.TestCase):
    """Tests for build_context_prompt() WITH memories."""

    @classmethod
    def setUpClass(cls):
        cls.minimal_payload = {
            "chiefId": "chief-1",
            "villageId": "v-1",
            "chiefName": "Gerald",
            "chiefRole": "Dorfhaeuptling",
            "chiefPersonality": "grimmig",
            "chiefGreeting": "Nanu?",
            "villageBiome": "PLAINS",
            "villageName": "Weidenfeld",
            "villagePopulationEstimate": 12,
            "villageEventSummary": "kein Ereignis",
            "villageAttributes": "klein, freundlich",
            "villagerProfession": "NONE",
            "villagerType": "PLAINS",
            "currentBiome": "PLAINS",
            "worldName": "world",
            "isDay": True,
            "isRaining": False,
            "isThundering": False,
            "playerMessage": "Weißt du noch, was ich gesagt habe?",
            "playerUuid": "uuid-123",
        }
        cls.empty_config = {
            "knowledge_packets": {
                "always": ["Ein Dorfbewohner kennt sein Handwerk."],
            }
        }

    def test_memories_section_added_when_memories_provided(self):
        memories = [
            "Du hast mir einst von den Bergen erzählt.",
            "Ich sagte, die Minen sind gefährlich.",
            "Du fragtest nach dem besten Handelsweg.",
        ]
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=memories,
        )
        self.assertIn("--- Memories ---", prompt)
        for mem in memories:
            self.assertIn(mem, prompt)

    def test_memories_section_position_correct(self):
        """Memories should appear after Status but before Spieler-Nachricht."""
        memories = ["Erinnerung 1", "Erinnerung 2", "Erinnerung 3"]
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=memories,
        )
        # Verify order
        pos_status = prompt.find("--- Status ---")
        pos_memories = prompt.find("--- Memories ---")
        pos_spieler = prompt.find("--- Spieler-Nachricht ---")

        self.assertLess(pos_status, pos_memories,
                        "Status must appear before Memories")
        self.assertLess(pos_memories, pos_spieler,
                        "Memories must appear before Spieler-Nachricht")

    def test_empty_memories_list_not_rendered(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=[],
        )
        self.assertNotIn("--- Memories ---", prompt)

    def test_memories_with_empty_strings_skipped(self):
        """Empty strings in memories list should not create bullet points."""
        memories = ["gültig", "", "   ", "auch gültig"]
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=memories,
        )
        self.assertIn("--- Memories ---", prompt)
                # Only the non-empty ones should appear as bullets (flat strings get "Unbekannt:" prefix)
        self.assertIn("- Unbekannt: gültig", prompt)
        self.assertIn("- Unbekannt: auch gültig", prompt)
        # Empty entries should not produce dangling bullet points
        lines = [
            line for line in prompt.splitlines()
            if line.strip() == "-" or line.strip() == "-" + " " * 0
        ]
        self.assertEqual(len(lines), 0, f"Found dangling bullet points: {lines}")

    def test_none_memories_not_rendered(self):
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=None,
        )
        self.assertNotIn("--- Memories ---", prompt)


class BuildContextPromptWithSummaryTests(unittest.TestCase):
    """Tests for build_context_prompt() WITH summary."""

    @classmethod
    def setUpClass(cls):
        cls.minimal_payload = {
            "chiefId": "chief-1",
            "villageId": "v-1",
            "chiefName": "Gerald",
            "chiefRole": "Dorfhaeuptling",
            "chiefPersonality": "grimmig",
            "chiefGreeting": "Nanu?",
            "villageBiome": "PLAINS",
            "villageName": "Weidenfeld",
            "villagePopulationEstimate": 12,
            "villageEventSummary": "kein Ereignis",
            "villageAttributes": "klein, freundlich",
            "villagerProfession": "NONE",
            "villagerType": "PLAINS",
            "currentBiome": "PLAINS",
            "worldName": "world",
            "isDay": True,
            "isRaining": False,
            "isThundering": False,
            "playerMessage": "Hallo!",
            "playerUuid": "uuid-123",
        }
        cls.empty_config = {
            "knowledge_packets": {
                "always": ["Ein Dorfbewohner kennt sein Handwerk."],
            }
        }

    def test_summary_section_added_when_summary_provided(self):
        summary = (
            "Der Spieler hat sich als Händler vorgestellt und über Preise diskutiert."
        )
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            summary_text=summary,
        )
        self.assertIn("--- Summary ---", prompt)
        self.assertIn(summary, prompt)

    def test_summary_section_position_correct(self):
        """Summary should come after Memories (if any) but before Spieler-Nachricht.
        Without memories, it should be between Status and Spieler-Nachricht."""
        summary = "Kurze Zusammenfassung: Der Spieler mag Brot."
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            summary_text=summary,
        )
        pos_status = prompt.find("--- Status ---")
        pos_summary = prompt.find("--- Summary ---")
        pos_spieler = prompt.find("--- Spieler-Nachricht ---")

        self.assertLess(pos_status, pos_summary,
                        "Status must appear before Summary")
        self.assertLess(pos_summary, pos_spieler,
                        "Summary must appear before Spieler-Nachricht")

    def test_summary_whitespace_only_not_rendered(self):
        """A summary consisting of only whitespace should not render."""
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            summary_text="   ",
        )
        self.assertNotIn("--- Summary ---", prompt)

    def test_summary_and_memories_together_position(self):
        """When both are present: Status → Memories → Summary → Spieler-Nachricht."""
        memories = ["Erinnerung: Du warst hier."]
        summary = "Zusammenfassung: Ein Händler aus den Bergen."
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=memories,
            summary_text=summary,
        )
        pos_status = prompt.find("--- Status ---")
        pos_memories = prompt.find("--- Memories ---")
        pos_summary = prompt.find("--- Summary ---")
        pos_spieler = prompt.find("--- Spieler-Nachricht ---")

        self.assertLess(pos_status, pos_memories)
        self.assertLess(pos_memories, pos_summary)
        self.assertLess(pos_summary, pos_spieler)

    def test_no_duplicate_sections(self):
        """Each section heading should appear at most once."""
        prompt = prompt_builder.build_context_prompt(
            self.minimal_payload,
            self.empty_config,
            memories=["test"],
            summary_text="test summary",
        )
        section_markers = [
            "--- Ground-Truth ---",
            "--- Persoenlichkeit ---",
            "--- Dorf-Details ---",
            "--- Ruf ---",
            "--- Status ---",
            "--- Knowledge ---",
            "--- Memories ---",
            "--- Summary ---",
            "--- Regeln ---",
            "--- Spieler-Nachricht ---",
        ]
        for marker in section_markers:
            count = prompt.count(marker)
            self.assertLessEqual(count, 1,
                                 f"Duplicate section found: {marker} (count={count})")


class BuildContextPromptEdgeCaseTests(unittest.TestCase):
    """Edge case tests."""

    def test_empty_payload_does_not_crash(self):
        """Even with empty dict, should not raise."""
        try:
            prompt = prompt_builder.build_context_prompt(
                {},
                {"knowledge_packets": {}},
            )
            self.assertIsInstance(prompt, str)
        except Exception as e:
            self.fail(f"build_context_prompt with empty payload raised: {e}")

    def test_missing_optional_fields_do_not_crash(self):
        payload = {
            "playerMessage": "test",
            "playerUuid": "uuid-1",
        }
        try:
            prompt = prompt_builder.build_context_prompt(
                payload,
                {"knowledge_packets": {}},
            )
            self.assertIsInstance(prompt, str)
            self.assertIn("test", prompt)
        except Exception as e:
            self.fail(f"build_context_prompt with minimal payload raised: {e}")

    def test_config_with_no_knowledge_packets(self):
        prompt = prompt_builder.build_context_prompt(
            {"playerUuid": "u", "playerMessage": "Hi"},
            {},
        )
        self.assertIsInstance(prompt, str)
        # Knowledge section should still exist but may be empty
        # (empty or just "--- Knowledge ---\n")
        # build_knowledge_packet returns "" if config has no knowledge_packets
        # Leere Sektionen werden nicht gerendert → Knowledge-Sektion fehlt
        self.assertNotIn("--- Knowledge ---", prompt)


class BuildOllamaPromptTests(unittest.TestCase):
    """Tests for build_ollama_prompt()."""

    def test_ollama_prompt_contains_system_and_context(self):
        payload = {
            "playerUuid": "u-1",
            "playerMessage": "Hallo",
        }
        config = {
            "knowledge_packets": {},
            "ollama": {
                "system_prompt": "Du bist ein Dorfbewohner.",
            },
        }
        prompt = prompt_builder.build_ollama_prompt(payload, config)
        self.assertIn("Du bist ein Dorfbewohner.", prompt)
        # Should contain sections from build_context_prompt
        self.assertIn("--- Spieler-Nachricht ---", prompt)

    def test_ollama_prompt_fallback_system_prompt(self):
        payload = {"playerUuid": "u", "playerMessage": "Hi"}
        config = {"knowledge_packets": {}}
        prompt = prompt_builder.build_ollama_prompt(payload, config)
        # Fallback system prompt should be in there
        self.assertIn("glaubwuerdiger Sprecher", prompt)


class BuildDeepseekMessagesTests(unittest.TestCase):
    """Tests for build_deepseek_messages()."""

    def test_deepseek_messages_structure(self):
        payload = {
            "playerUuid": "u-1",
            "playerMessage": "Hallo",
        }
        config = {
            "knowledge_packets": {},
            "deepseek": {
                "system_prompt": "Du bist ein Händler.",
            },
        }
        result = prompt_builder.build_deepseek_messages(payload, config)
        self.assertIsInstance(result, list)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["role"], "system")
        self.assertEqual(result[1]["role"], "user")
        self.assertIn("Du bist ein Händler.", result[0]["content"])
        self.assertIn("--- Spieler-Nachricht ---", result[1]["content"])

    def test_deepseek_payload_system_prompt_overrides_config(self):
        payload = {
            "playerUuid": "u",
            "playerMessage": "Hi",
            "systemPrompt": "OVERRIDE_PROMPT",
        }
        config = {
            "knowledge_packets": {},
            "deepseek": {"system_prompt": "config prompt"},
        }
        result = prompt_builder.build_deepseek_messages(payload, config)
        self.assertIn("OVERRIDE_PROMPT", result[0]["content"])
        self.assertNotIn("config prompt", result[0]["content"])


if __name__ == "__main__":
    unittest.main()