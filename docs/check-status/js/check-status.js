// Check Submission Status App
// Handles authentication and status display for submissions

const SUPABASE_URL = 'https://hjzhojjnjnawwnwhzgwq.supabase.co';
const SUPABASE_ANON_KEY = 'sb_publishable_LATVvS_FmS-sLW_i1T7u1A_Bi9bWah7';

const supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// Store credentials in memory for session
let currentSubmissionId = null;
let currentPasswordHash = null;

// Hash password using SHA-256
async function hashPassword(password) {
    const encoder = new TextEncoder();
    const data = encoder.encode(password);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// Check for URL parameters (id and password can be pre-filled)
window.addEventListener('DOMContentLoaded', () => {
    const urlParams = new URLSearchParams(window.location.search);
    const idParam = urlParams.get('id');
    const passwordParam = urlParams.get('password');

    if (idParam) {
        document.getElementById('submissionId').value = idParam;
    }
    if (passwordParam) {
        document.getElementById('password').value = passwordParam;
    }

    // Auto-submit if both provided
    if (idParam && passwordParam) {
        setTimeout(() => {
            document.getElementById('loginForm').dispatchEvent(new Event('submit'));
        }, 500);
    }
});

// Login form handler
document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const submissionId = document.getElementById('submissionId').value.trim();
    const password = document.getElementById('password').value.trim();
    
    if (!submissionId || !password) {
        showError('Bitte Submission-ID und Passwort eingeben.');
        return;
    }
    
    await checkStatus(submissionId, password);
});

// Check submission status
async function checkStatus(submissionId, password) {
    try {
        // Show loading state
        const submitBtn = document.querySelector('#loginForm button[type="submit"]');
        const originalText = submitBtn.textContent;
        submitBtn.textContent = '⏳ Prüfe...';
        submitBtn.disabled = true;
        
        hideError();
        
        // Hash the password
        const passwordHash = await hashPassword(password);
        
        // Call RPC function to check status
        const { data, error } = await supabaseClient.rpc('get_submission_status', {
            p_submission_id: submissionId,
            p_password_hash: passwordHash
        });
        
        if (error) throw error;
        
        if (!data || !data.success) {
            throw new Error(data?.error || 'Ungültige Submission-ID oder Passwort');
        }
        
        // Store credentials for session
        currentSubmissionId = submissionId;
        currentPasswordHash = passwordHash;
        
        // Display status
        displayStatus(data);
        
    } catch (error) {
        console.error('Status check error:', error);
        showError(error.message || 'Fehler beim Prüfen des Status. Bitte überprüfen Sie Ihre Eingaben.');
        
        // Reset button
        const submitBtn = document.querySelector('#loginForm button[type="submit"]');
        submitBtn.textContent = 'Status prüfen';
        submitBtn.disabled = false;
    }
}

// Display submission status
function displayStatus(submission) {
    // Hide login, show status
    document.getElementById('loginSection').style.display = 'none';
    document.getElementById('statusSection').style.display = 'block';
    
    // Set game name
    document.getElementById('gameName').textContent = submission.game_name || 'Unknown';
    
    // Set status badge
    const statusBadge = document.getElementById('statusBadge');
    statusBadge.textContent = getStatusText(submission.status);
    statusBadge.className = `status-badge ${submission.status}`;
    
    // Set detail fields
    document.getElementById('statusText').textContent = getStatusText(submission.status);
    document.getElementById('submittedAt').textContent = new Date(submission.created_at).toLocaleString('de-DE');
    document.getElementById('updatedAt').textContent = new Date(submission.updated_at).toLocaleString('de-DE');
    
    // Hide all info boxes first
    document.querySelectorAll('.info-box').forEach(box => box.style.display = 'none');
    
    // Show appropriate info box
    switch (submission.status) {
        case 'pending':
            document.getElementById('pendingInfo').style.display = 'block';
            document.getElementById('editBtn').style.display = 'inline-block';
            break;
        case 'in_progress':
            document.getElementById('inProgressInfo').style.display = 'block';
            document.getElementById('editBtn').style.display = 'inline-block';
            break;
        case 'approved':
            document.getElementById('approvedInfo').style.display = 'block';
            document.getElementById('voteCount').textContent = submission.vote_count || 0;
            document.getElementById('downloadCount').textContent = submission.download_count || 0;
            document.getElementById('editBtn').style.display = 'none';
            break;
        case 'rejected':
            document.getElementById('rejectedInfo').style.display = 'block';
            document.getElementById('rejectionReason').textContent = 
                submission.rejection_reason || 'Kein Grund angegeben.';
            document.getElementById('editBtn').style.display = 'none';
            break;
    }
}

// Get human-readable status text
function getStatusText(status) {
    const statusTexts = {
        pending: 'Warten auf Überprüfung',
        in_progress: 'Wird überprüft',
        approved: 'Genehmigt',
        published: 'Veröffentlicht',
        rejected: 'Abgelehnt'
    };
    return statusTexts[status] || status;
}

// Show error message
function showError(message) {
    const errorDiv = document.getElementById('loginError');
    errorDiv.textContent = message;
    errorDiv.style.display = 'block';
}

// Hide error message
function hideError() {
    const errorDiv = document.getElementById('loginError');
    errorDiv.style.display = 'none';
}

// Button handlers
document.getElementById('editBtn').addEventListener('click', () => {
    if (currentSubmissionId && currentPasswordHash) {
        // Redirect to edit page with credentials (hash, not cleartext)
        window.location.href = `../edit-submission/?id=${currentSubmissionId}&hash=${encodeURIComponent(currentPasswordHash)}`;
    }
});

document.getElementById('checkAgainBtn').addEventListener('click', async () => {
    if (currentSubmissionId && currentPasswordHash) {
        // Re-check using stored hash
        try {
            const submitBtn = document.getElementById('checkAgainBtn');
            submitBtn.textContent = '⏳ Prüfe...';
            submitBtn.disabled = true;
            
            const { data, error } = await supabaseClient.rpc('get_submission_status', {
                p_submission_id: currentSubmissionId,
                p_password_hash: currentPasswordHash
            });
            
            if (error) throw error;
            if (!data || !data.success) throw new Error(data?.error || 'Error');
            
            displayStatus(data);
            submitBtn.textContent = '🔄 Erneut prüfen';
            submitBtn.disabled = false;
        } catch (e) {
            console.error('Refresh error:', e);
            alert('Fehler beim Aktualisieren: ' + e.message);
        }
    }
});

document.getElementById('logoutBtn').addEventListener('click', () => {
    // Clear session and show login
    currentSubmissionId = null;
    currentPasswordHash = null;
    document.getElementById('loginSection').style.display = 'block';
    document.getElementById('statusSection').style.display = 'none';
    document.getElementById('loginForm').reset();
    hideError();
    
    // Reset button
    const submitBtn = document.querySelector('#loginForm button[type="submit"]');
    submitBtn.textContent = 'Status prüfen';
    submitBtn.disabled = false;
});
