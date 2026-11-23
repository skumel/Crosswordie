package com.crosswordie.app.domain.generator

/**
 * Fast, in-memory dictionary index for 3-, 4-, 5-, and 6-letter words.
 * Supports O(1) lookup by word length and positional character constraints,
 * plus plural and regular verb/noun inflection recognition so plural guesses
 * (e.g., MAKE -> MAKES, CAT -> CATS, BABY -> BABIES, BOX -> BOXES) are always valid.
 */
class WordIndex(
    targetWords: Collection<String>,
    validGuessWords: Collection<String> = emptyList()
) {
    val wordsByLength: Map<Int, List<String>>
    val validWordsByLength: Map<Int, Set<String>>
    private val allKnownWords: Set<String>

    // Key: (length, positionIndex, uppercaseChar) -> Set of matching target words
    private val positionalIndex: Map<Triple<Int, Int, Char>, Set<String>>

    init {
        val cleanedTargets = targetWords
            .asSequence()
            .map { it.trim().uppercase() }
            .filter { it.length in MIN_WORD_LEN..MAX_WORD_LEN && it.all { ch -> ch in 'A'..'Z' } }
            .distinct()
            .sorted()
            .toList()

        wordsByLength = cleanedTargets.groupBy { it.length }

        val posMap = HashMap<Triple<Int, Int, Char>, MutableSet<String>>()
        for (word in cleanedTargets) {
            val len = word.length
            for (i in 0 until len) {
                val key = Triple(len, i, word[i])
                posMap.getOrPut(key) { LinkedHashSet() }.add(word)
            }
        }
        positionalIndex = posMap

        val baseValid = (cleanedTargets.asSequence() + validGuessWords.asSequence()
            .map { it.trim().uppercase() }
            .filter { it.length in MIN_WORD_LEN..MAX_WORD_LEN && it.all { ch -> ch in 'A'..'Z' } })
            .toMutableSet()

        // Expand plurals & regular inflections from all known 3..5 letter stems into the valid dictionary
        val expandedForms = HashSet<String>(baseValid.size * 2)
        for (w in baseValid) {
            expandedForms.add(w)
            for (inflected in generatePluralAndInflectedForms(w)) {
                if (inflected.length in MIN_WORD_LEN..MAX_WORD_LEN) {
                    expandedForms.add(inflected)
                }
            }
        }

        allKnownWords = expandedForms
        validWordsByLength = expandedForms.groupBy { it.length }.mapValues { it.value.toSet() }
    }

    /**
     * Checks if a player's submitted guess is in the valid dictionary for its length,
     * including plural forms (-S, -ES, -IES) and regular inflections (-ED, -ER, -ING)
     * of any known dictionary word.
     */
    fun isValidGuess(word: String): Boolean {
        val upper = word.trim().uppercase()
        if (upper.length !in MIN_WORD_LEN..MAX_WORD_LEN) return false
        if (validWordsByLength[upper.length]?.contains(upper) == true) return true
        return isPluralOrInflectedFormOfKnownWord(upper)
    }

    /**
     * Fallback stemmer check: verifies if [word] is a valid plural or inflection of a known stem.
     */
    private fun isPluralOrInflectedFormOfKnownWord(word: String): Boolean {
        val len = word.length
        // 1. Consonant + Y -> IES / IED (e.g., BABIES -> BABY, CITIES -> CITY, TRIED -> TRY)
        if (len >= 4 && (word.endsWith("IES") || word.endsWith("IED"))) {
            val stemY = word.substring(0, len - 3) + "Y"
            if (stemY in allKnownWords) return true
        }

        // 2. Words ending in S (e.g., MAKES -> MAKE, CATS -> CAT, BOXES -> BOX, WISHES -> WISH)
        if (len >= 4 && word.endsWith("S")) {
            val withoutS = word.substring(0, len - 1)
            if (withoutS in allKnownWords) return true

            if (word.endsWith("ES") && len >= 5) {
                val withoutEs = word.substring(0, len - 2)
                if (withoutEs in allKnownWords) return true
            }
        }

        // 3. Regular past/agent suffixes (-ED, -ER, -D, -R)
        if (len >= 4 && (word.endsWith("ED") || word.endsWith("ER"))) {
            val withoutLast1 = word.substring(0, len - 1) // e.g., BAKED -> BAKE, MAKER -> MAKE
            if (withoutLast1 in allKnownWords) return true
            val withoutLast2 = word.substring(0, len - 2) // e.g., PLAYED -> PLAY
            if (withoutLast2 in allKnownWords) return true
            // Doubled consonant (e.g., STOPPED -> STOP, BIGGER -> BIG)
            if (withoutLast2.length >= 3 &&
                withoutLast2.last() == withoutLast2[withoutLast2.length - 2]
            ) {
                val undoubled = withoutLast2.substring(0, withoutLast2.length - 1)
                if (undoubled in allKnownWords) return true
            }
        }

        return false
    }

    private fun generatePluralAndInflectedForms(stem: String): List<String> {
        val len = stem.length
        if (len >= MAX_WORD_LEN) return emptyList()
        val results = ArrayList<String>(6)

        val endsWithSibilant = stem.endsWith("S") ||
            stem.endsWith("Z") ||
            stem.endsWith("X") ||
            stem.endsWith("CH") ||
            stem.endsWith("SH")

        val endsWithConsonantY = len >= 2 &&
            stem.last() == 'Y' &&
            stem[len - 2] !in VOWELS

        when {
            endsWithConsonantY -> {
                val prefix = stem.substring(0, len - 1)
                results.add("${prefix}IES")
                results.add("${prefix}IED")
                results.add("${prefix}IER")
            }
            endsWithSibilant -> {
                results.add("${stem}ES")
                results.add("${stem}ED")
                results.add("${stem}ER")
            }
            stem.endsWith("E") -> {
                results.add("${stem}S")
                results.add("${stem}D")
                results.add("${stem}R")
            }
            else -> {
                results.add("${stem}S")
                results.add("${stem}ED")
                results.add("${stem}ER")
                results.add("${stem}ING")
            }
        }

        return results
    }

    /**
     * Returns all candidate target words of [length] that satisfy [constraints] (index -> Char)
     * and are not in [excludedWords].
     */
    fun getCandidates(
        length: Int,
        constraints: Map<Int, Char>,
        excludedWords: Set<String> = emptySet()
    ): List<String> {
        val allOfLength = wordsByLength[length] ?: return emptyList()
        if (constraints.isEmpty()) {
            return if (excludedWords.isEmpty()) allOfLength else allOfLength.filter { it !in excludedWords }
        }

        // Start with the smallest set among the positional constraints for fast set intersection
        var smallestSet: Set<String>? = null
        val otherSets = mutableListOf<Set<String>>()

        for ((pos, ch) in constraints) {
            val matching = positionalIndex[Triple(length, pos, ch.uppercaseChar())] ?: return emptyList()
            if (smallestSet == null || matching.size < smallestSet.size) {
                if (smallestSet != null) otherSets.add(smallestSet)
                smallestSet = matching
            } else {
                otherSets.add(matching)
            }
        }

        val base = smallestSet ?: return emptyList()
        val result = ArrayList<String>(base.size)
        for (candidate in base) {
            if (candidate in excludedWords) continue
            var matchesAll = true
            for (set in otherSets) {
                if (candidate !in set) {
                    matchesAll = false
                    break
                }
            }
            if (matchesAll) {
                result.add(candidate)
            }
        }
        return result
    }

    /**
     * Fast boolean check for forward checking: is there at least one valid candidate
     * of [length] satisfying [constraints] and not in [excludedWords]?
     */
    fun hasAnyCandidate(
        length: Int,
        constraints: Map<Int, Char>,
        excludedWords: Set<String>
    ): Boolean {
        val allOfLength = wordsByLength[length] ?: return false
        if (constraints.isEmpty()) {
            return allOfLength.any { it !in excludedWords }
        }

        var smallestSet: Set<String>? = null
        val otherSets = mutableListOf<Set<String>>()

        for ((pos, ch) in constraints) {
            val matching = positionalIndex[Triple(length, pos, ch.uppercaseChar())] ?: return false
            if (smallestSet == null || matching.size < smallestSet.size) {
                if (smallestSet != null) otherSets.add(smallestSet)
                smallestSet = matching
            } else {
                otherSets.add(matching)
            }
        }

        val base = smallestSet ?: return false
        for (candidate in base) {
            if (candidate in excludedWords) continue
            var matchesAll = true
            for (set in otherSets) {
                if (candidate !in set) {
                    matchesAll = false
                    break
                }
            }
            if (matchesAll) return true
        }
        return false
    }

    companion object {
        const val MIN_WORD_LEN = 3
        const val MAX_WORD_LEN = 6
        private val VOWELS = setOf('A', 'E', 'I', 'O', 'U')
    }
}
