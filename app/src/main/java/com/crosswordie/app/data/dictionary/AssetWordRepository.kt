package com.crosswordie.app.data.dictionary

import android.content.Context
import com.crosswordie.app.domain.generator.WordIndex

/**
 * Loads the bundled offline English dictionaries (`words_target.txt` and `words_valid.txt`)
 * from Android assets and constructs the in-memory [WordIndex].
 */
class AssetWordRepository(private val context: Context) {

    @Volatile
    private var cachedIndex: WordIndex? = null

    fun getWordIndex(): WordIndex {
        cachedIndex?.let { return it }
        synchronized(this) {
            cachedIndex?.let { return it }
            val targetWords = readAssetLines("words_target.txt")
            val validWords = readAssetLines("words_valid.txt")
            return WordIndex(targetWords, validWords).also {
                cachedIndex = it
            }
        }
    }

    private fun readAssetLines(fileName: String): List<String> {
        return context.assets.open(fileName).bufferedReader().useLines { lines ->
            lines.map { it.trim().uppercase() }
                .filter { it.length in WordIndex.MIN_WORD_LEN..WordIndex.MAX_WORD_LEN }
                .toList()
        }
    }
}
