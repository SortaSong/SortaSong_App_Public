package com.sortasong.sortasong

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sortasong.sortasong.databinding.ItemGameStatusBinding

class GameStatusAdapter(
    private var games: List<GameVerificationStatus>,
    private val onGameClick: (GameVerificationStatus) -> Unit
) : RecyclerView.Adapter<GameStatusAdapter.GameStatusViewHolder>() {

    inner class GameStatusViewHolder(
        private val binding: ItemGameStatusBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gameStatus: GameVerificationStatus) {
            binding.gameNameText.text = gameStatus.game.game
            binding.verificationStatusText.text = gameStatus.statusText

            // Make clickable only if there are unavailable tracks
            binding.root.isClickable = gameStatus.isClickable
            binding.root.isFocusable = gameStatus.isClickable

            if (gameStatus.isClickable) {
                binding.root.setOnClickListener {
                    onGameClick(gameStatus)
                }
                // Highlight clickable items
                binding.verificationStatusText.setTextColor(
                    binding.root.context.getColor(android.R.color.holo_orange_light)
                )
            } else {
                binding.root.setOnClickListener(null)
                binding.verificationStatusText.setTextColor(
                    binding.root.context.getColor(android.R.color.darker_gray)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameStatusViewHolder {
        val binding = ItemGameStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GameStatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameStatusViewHolder, position: Int) {
        holder.bind(games[position])
    }

    override fun getItemCount(): Int = games.size

    fun updateGames(newGames: List<GameVerificationStatus>) {
        games = newGames
        notifyDataSetChanged()
    }
}
