package com.sortasong.sortasong

object CardDeckRepository {
    var cards: MutableList<TrackEntry> = mutableListOf()

    fun createDeckFromFolders(selectedFolders: List<String>) {
        cards.clear()
        selectedFolders.forEach { folder ->
            val tracks = GameRepository.tracksByFolder[folder] ?: emptyList()
            cards.addAll(tracks)
        }
        cards.shuffle()
    }

    fun drawNextCard(): TrackEntry? {
        return if (cards.isNotEmpty()) cards.removeAt(0) else null
    }

    fun isEmpty(): Boolean = cards.isEmpty()
}
