package com.sortasong.sortasong

import com.sortasong.sortasong.data.CustomGameRepository

object CardDeckRepository {
    var cards: MutableList<TrackEntry> = mutableListOf()

    fun createDeckFromFolders(selectedFolders: List<String>) {
        cards.clear()
        selectedFolders.forEach { folder ->
            // Check both official and custom game tracks
            val tracks = CustomGameRepository.getTracksForFolder(folder) ?: emptyList()
            cards.addAll(tracks)
        }
        cards.shuffle()
    }

    fun drawNextCard(): TrackEntry? {
        return if (cards.isNotEmpty()) cards.removeAt(0) else null
    }

    fun isEmpty(): Boolean = cards.isEmpty()
}
