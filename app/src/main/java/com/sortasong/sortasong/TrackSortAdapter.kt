package com.sortasong.sortasong

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.text.takeLast
import androidx.core.graphics.toColorInt

class TrackSortAdapter(
    private val tracks: List<TrackEntry>,
    private val insertIndex: Int

) : RecyclerView.Adapter<TrackSortAdapter.TrackViewHolder>() {

    class TrackViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun getItemCount(): Int = tracks.size + 1 // +1 für Platzhalter „???"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val tv = LayoutInflater.from(parent.context).inflate(
            android.R.layout.simple_list_item_1, parent, false
        ) as TextView
        tv.textSize = 18f
        tv.setTextColor(Color.WHITE)
        tv.setPadding(32, 32, 32, 32)
        val background = GradientDrawable().apply {
            setColor("#222222".toColorInt()) // dunkler Hintergrund
            cornerRadius = 24f
            setStroke(3, Color.WHITE)             // weißer Rand
        }

        tv.background = background
        return TrackViewHolder(tv)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val ctx = holder.textView.context
        if (position == insertIndex) {
            holder.textView.text = ctx.getString(R.string.track_sort_placeholder)
        } else {
            val actualIndex = if (position < insertIndex) position else position - 1
            val track = tracks[actualIndex]
            holder.textView.text = ctx.getString(
                R.string.track_sort_item_format,
                track.releaseDate.takeLast(4),
                track.artist,
                track.song
            )
        }
    }
}
