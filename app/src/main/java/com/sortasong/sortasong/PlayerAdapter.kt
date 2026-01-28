package com.sortasong.sortasong

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlayerAdapter(private val players: List<Player>) :
    RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerInitials: TextView = view.findViewById(R.id.playerInitials)
        val playerName: TextView = view.findViewById(R.id.playerName)
        val playerTrackCount: TextView = view.findViewById(R.id.playerTrackCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = players[position]
        holder.playerInitials.text = player.name.firstOrNull()?.toString() ?: ""
        holder.playerName.text = player.name
        holder.playerTrackCount.text = holder.playerTrackCount.context.getString(R.string.list_item_player_track_count_format, player.tracks.size)
    }

    override fun getItemCount() = players.size
}
