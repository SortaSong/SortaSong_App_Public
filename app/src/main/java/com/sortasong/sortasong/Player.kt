package com.sortasong.sortasong

data class Player(
    val name: String,
    val tracks: MutableList<TrackEntry> = mutableListOf()
)