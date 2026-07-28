package com.protoprojects.agrix.ai

import org.json.JSONObject

/**
 * Pulls a JSON object out of a small on-device model's raw text response.
 *
 * Small models reliably follow "respond with only JSON" instructions maybe
 * 80-90% of the time; the rest wrap it in a sentence, markdown fences, or
 * trailing commentary. A naive `indexOf('{')` / `lastIndexOf('}')` approach
 * (what this used to be) breaks the moment there's a second brace-looking
 * fragment anywhere in the response — including inside a string value like
 * "notes": "if unsure, treat as {unknown}". This does a proper balanced-brace
 * scan instead, respecting string literals and escapes, so it finds the
 * actual first complete top-level object rather than just the first-to-last
 * brace span.
 */
object JsonExtractor {

    fun extractJson(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escapeNext = false

        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (inString) {
                when {
                    escapeNext -> escapeNext = false
                    c == '\\' -> escapeNext = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val candidate = cleaned.substring(start, i + 1)
                        return try {
                            JSONObject(candidate)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }
}
