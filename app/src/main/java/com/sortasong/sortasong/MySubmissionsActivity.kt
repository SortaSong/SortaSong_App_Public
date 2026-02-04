package com.sortasong.sortasong

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sortasong.sortasong.data.StatusResult
import com.sortasong.sortasong.data.SubmissionCredential
import com.sortasong.sortasong.data.SubmissionService
import com.sortasong.sortasong.databinding.ActivityMySubmissionsBinding
import com.sortasong.sortasong.databinding.ItemSubmissionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MySubmissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMySubmissionsBinding
    private lateinit var adapter: SubmissionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMySubmissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        
        setupUI()
        loadSubmissions()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.refreshButton.setOnClickListener { refreshAllStatuses() }
        
        adapter = SubmissionsAdapter(
            onCheckOnline = { credential ->
                openCheckStatusPage(credential)
            }
        )
        
        binding.submissionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.submissionsRecyclerView.adapter = adapter
    }

    private fun loadSubmissions() {
        val submissions = SubmissionService.getStoredCredentials(this)
        adapter.updateSubmissions(submissions)
        
        if (submissions.isEmpty()) {
            binding.submissionsRecyclerView.visibility = View.GONE
            binding.emptyStateText.visibility = View.VISIBLE
        } else {
            binding.submissionsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        }
    }

    private fun refreshAllStatuses() {
        Toast.makeText(this, getString(R.string.my_submissions_refreshing), Toast.LENGTH_SHORT).show()
        
        val submissions = SubmissionService.getStoredCredentials(this)
        
        lifecycleScope.launch {
            var updated = 0
            for (credential in submissions) {
                val result = withContext(Dispatchers.IO) {
                    SubmissionService.checkStatus(credential.submissionId, credential.passwordHash)
                }
                
                when (result) {
                    is StatusResult.Success -> {
                        SubmissionService.updateCachedStatus(
                            this@MySubmissionsActivity,
                            credential.folderName,
                            result.response.status ?: "pending",
                            result.response.voteCount ?: 0,
                            result.response.downloadCount ?: 0,
                            result.response.rejectionReason
                        )
                        updated++
                    }
                    is StatusResult.Error -> {
                        // Ignore individual errors
                    }
                }
            }
            
            loadSubmissions()
            Toast.makeText(this@MySubmissionsActivity, 
                getString(R.string.my_submissions_refreshed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCheckStatusPage(credential: SubmissionCredential) {
        val url = "https://sortasong.github.io/SortaSong_App_Public/check-status/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    // Adapter
    class SubmissionsAdapter(
        private val onCheckOnline: (SubmissionCredential) -> Unit
    ) : RecyclerView.Adapter<SubmissionsAdapter.ViewHolder>() {

        private val submissions = mutableListOf<SubmissionCredential>()

        fun updateSubmissions(newSubmissions: List<SubmissionCredential>) {
            submissions.clear()
            submissions.addAll(newSubmissions.sortedByDescending { it.submittedAt })
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSubmissionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(submissions[position])
        }

        override fun getItemCount() = submissions.size

        inner class ViewHolder(private val binding: ItemSubmissionBinding) : 
            RecyclerView.ViewHolder(binding.root) {

            fun bind(credential: SubmissionCredential) {
                val context = binding.root.context
                
                binding.gameNameText.text = credential.gameName
                binding.votesText.text = "👍 ${credential.cachedVoteCount}"
                binding.downloadsText.text = "⬇️ ${credential.cachedDownloadCount}"
                
                // Status badge
                val (statusText, statusColor) = when (credential.cachedStatus) {
                    "pending" -> context.getString(R.string.my_submissions_status_pending) to "#f59e0b"
                    "in_progress" -> context.getString(R.string.my_submissions_status_in_progress) to "#3b82f6"
                    "approved" -> context.getString(R.string.my_submissions_status_approved) to "#22c55e"
                    "published" -> context.getString(R.string.my_submissions_status_published) to "#8b5cf6"
                    "rejected" -> context.getString(R.string.my_submissions_status_rejected) to "#ef4444"
                    else -> credential.cachedStatus to "#6b7280"
                }
                
                binding.statusBadge.text = statusText
                binding.statusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(statusColor)
                )
                
                // Rejection reason
                if (credential.cachedStatus == "rejected" && !credential.rejectionReason.isNullOrBlank()) {
                    binding.rejectionReasonText.text = context.getString(
                        R.string.my_submissions_rejection_reason, credential.rejectionReason
                    )
                    binding.rejectionReasonText.visibility = View.VISIBLE
                } else {
                    binding.rejectionReasonText.visibility = View.GONE
                }
                
                binding.checkOnlineButton.setOnClickListener { onCheckOnline(credential) }
            }
        }
    }
}
