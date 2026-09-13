package com.jasonarends.forklore.data.db

/**
 * Reduces a dish name to a matching key: lowercased, accents folded, punctuation dropped,
 * whitespace collapsed. "Mac 'n' Cheese", "mac n cheese" and "MAC N CHEESE" all normalize to the
 * same string, which is what stops one dish becoming three rows.
 *
 * Stored alongside every dish and alias rather than computed in SQL: SQLite's LOWER() is
 * ASCII-only, and wrapping a column in a function makes it unindexable.
 */
fun normalizeDishName(raw: String): String =
  java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase()
    .replace("[^a-z0-9 ]".toRegex(), " ")
    .trim()
    .replace("\\s+".toRegex(), " ")
