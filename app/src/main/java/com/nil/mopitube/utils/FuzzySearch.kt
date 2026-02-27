package com.nil.mopitube.utils

/**
 * Fuzzy string matching utility using Damerau-Levenshtein distance.
 * Handles transpositions (e.g. "Pnuk" → "Punk") unlike standard Levenshtein.
 */
object FuzzySearch {

    /**
     * Damerau-Levenshtein distance: counts insertions, deletions, substitutions,
     * and adjacent transpositions each as 1 operation.
     */
    fun damerauLevenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
                // transposition (swap of two adjacent chars)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 1)
                }
            }
        }
        return dp[m][n]
    }

    /**
     * Returns a score from 0.0 to 1.0 indicating how well [query] matches [target].
     * - 1.0 = perfect / exact substring match
     * - 0.0 = no meaningful match
     *
     * Works word-by-word so "Bohemain Rhap" still matches "Bohemian Rhapsody".
     * Edit-distance tolerance: ≤35% of the longer word length (via Damerau-Levenshtein).
     * Words shorter than 3 chars are only matched exactly.
     */
    fun score(query: String, target: String): Float {
        if (query.isBlank() || target.isBlank()) return 0f

        val q = query.lowercase().trim()
        val t = target.lowercase().trim()

        // Exact substring match → highest priority
        if (t.contains(q)) return 1f

        val qWords = q.split("\\s+".toRegex()).filter { it.length >= 3 }
        val tWords = t.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (qWords.isEmpty()) return 0f

        var matchedWords = 0
        for (qw in qWords) {
            val found = tWords.any { tw ->
                when {
                    // Prefix match (e.g. "Rhap" matches "Rhapsody")
                    tw.startsWith(qw) || qw.startsWith(tw) -> true
                    // Fuzzy match for words ≥ 4 chars: allow ~35% edit distance
                    qw.length >= 4 && tw.length >= 4 -> {
                        val dist = damerauLevenshtein(qw, tw).toFloat()
                        dist / maxOf(qw.length, tw.length) <= 0.35f
                    }
                    else -> false
                }
            }
            if (found) matchedWords++
        }

        return matchedWords.toFloat() / qWords.size
    }

    /**
     * Returns the best fuzzy score for [query] against multiple [fields].
     * Useful for matching a query against name, artist name, and album name at once.
     */
    fun bestScore(query: String, vararg fields: String?): Float {
        return fields.filterNotNull().maxOfOrNull { score(query, it) } ?: 0f
    }
}
