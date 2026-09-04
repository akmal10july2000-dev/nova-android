package com.nova.assistant

import java.util.Locale

sealed interface ParsedCommand {
    data class OpenApp(val target: String) : ParsedCommand
    data object NotForNova : ParsedCommand
    data object Unrecognized : ParsedCommand
}

object CommandParser {
    private val wakePhrase = Regex("""\b(?:hey\s+nova|ok\s+nova|okay\s+nova|nova)\b""")
    private val openCommand = Regex(
        """^(?:please\s+)?(?:open|launch|start|run|go\s+to)\s+(.+)$""",
    )

    fun parse(spokenText: String, wakePhraseRequired: Boolean): ParsedCommand {
        val normalized = spokenText
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val wakeMatch = wakePhrase.find(normalized)
        if (wakePhraseRequired && wakeMatch == null) {
            return ParsedCommand.NotForNova
        }

        val commandText = if (wakeMatch != null) {
            normalized.removeRange(wakeMatch.range).trim()
        } else {
            normalized
        }

        val openMatch = openCommand.matchEntire(commandText) ?: return ParsedCommand.Unrecognized
        val target = openMatch.groupValues[1]
            .replace(Regex("\\b(app|application)\\b$"), "")
            .trim()

        return if (target.isBlank()) {
            ParsedCommand.Unrecognized
        } else {
            ParsedCommand.OpenApp(target)
        }
    }
}