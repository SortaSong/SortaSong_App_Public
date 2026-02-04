/**
 * SortaSong Admin - Main Application Logic
 */

// State
let currentTrackId = null;
let currentTrack = null;
let currentReports = [];
let originalTrackData = null;
let tracksWithReports = new Map();
let allTracks = [];

// DOM Elements
const screens = {
    login: document.getElementById('loginScreen'),
    main: document.getElementById('mainScreen'),
    detail: document.getElementById('detailScreen')
};

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
    setupEventListeners();
    
    // Initialize Supabase and check for existing session
    const hasSession = await supabaseAdmin.init();
    if (hasSession) {
        showScreen('main');
        await loadTracksWithReports();
    }
});

function setupEventListeners() {
    // Login
    document.getElementById('loginBtn').addEventListener('click', handleLogin);
    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
    
    // Main screen
    document.getElementById('refreshBtn').addEventListener('click', loadTracksWithReports);
    document.getElementById('showPendingOnly').addEventListener('change', loadTracksWithReports);
    document.getElementById('searchInput').addEventListener('input', filterTrackList);
    
    // Detail screen
    document.getElementById('backBtn').addEventListener('click', () => showScreen('main'));
    document.getElementById('saveBtn').addEventListener('click', handleSave);
    
    // Allow Enter key to login
    document.getElementById('password').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleLogin();
    });
}

async function handleLogin() {
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value.trim();
    
    if (!email || !password) {
        showToast('Please enter email and password', 'error');
        return;
    }
    
    const btn = document.getElementById('loginBtn');
    btn.disabled = true;
    btn.textContent = 'Logging in...';
    
    try {
        await supabaseAdmin.login(email, password);
        
        showToast('Logged in successfully!', 'success');
        showScreen('main');
        await loadTracksWithReports();
        
    } catch (e) {
        console.error('Login failed:', e);
        showToast(e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = '🔑 Login';
    }
}

async function handleLogout() {
    await supabaseAdmin.logout();
    showScreen('login');
}

async function loadTracksWithReports() {
    const trackList = document.getElementById('trackList');
    trackList.innerHTML = '<div class="loading">Loading...</div>';
    
    try {
        const pendingOnly = document.getElementById('showPendingOnly').checked;
        tracksWithReports = await supabaseAdmin.getTracksWithReports(pendingOnly);
        
        if (tracksWithReports.size === 0) {
            trackList.innerHTML = '<div class="loading">No tracks with reports found.</div>';
            document.getElementById('reportCount').textContent = '0 tracks with reports';
            return;
        }
        
        // Fetch track details
        const trackIds = Array.from(tracksWithReports.keys());
        allTracks = await supabaseAdmin.getTracksByIds(trackIds);
        
        document.getElementById('reportCount').textContent = `${allTracks.length} tracks with reports`;
        
        renderTrackList();
        
    } catch (e) {
        console.error('Failed to load tracks:', e);
        trackList.innerHTML = `<div class="loading">Error: ${e.message}</div>`;
    }
}

function renderTrackList() {
    const trackList = document.getElementById('trackList');
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();
    
    let filteredTracks = allTracks;
    if (searchTerm) {
        filteredTracks = allTracks.filter(track => 
            track.id.toString().includes(searchTerm) ||
            (track.artist && track.artist.toLowerCase().includes(searchTerm)) ||
            (track.song && track.song.toLowerCase().includes(searchTerm))
        );
    }
    
    if (filteredTracks.length === 0) {
        trackList.innerHTML = '<div class="loading">No matching tracks found.</div>';
        return;
    }
    
    trackList.innerHTML = filteredTracks.map(track => {
        const reportInfo = tracksWithReports.get(track.id) || { total: 0, pending: 0, reviewed: 0 };
        const allDone = reportInfo.pending === 0;
        
        return `
            <div class="track-item ${allDone ? 'all-done' : ''}" data-track-id="${track.id}">
                <span class="track-id">#${track.id}</span>
                <div class="track-info">
                    <div class="track-artist">${escapeHtml(track.artist || '-')}</div>
                    <div class="track-title">${escapeHtml(track.song || '-')}</div>
                </div>
                <span class="report-count">${reportInfo.pending} pending / ${reportInfo.total} total</span>
            </div>
        `;
    }).join('');
    
    // Add click handlers
    trackList.querySelectorAll('.track-item').forEach(item => {
        item.addEventListener('click', () => {
            const trackId = parseInt(item.dataset.trackId);
            openTrackDetail(trackId);
        });
    });
}

function filterTrackList() {
    renderTrackList();
}

async function openTrackDetail(trackId) {
    currentTrackId = trackId;
    document.getElementById('trackIdDisplay').textContent = trackId;
    
    showScreen('detail');
    
    // Show loading state
    document.getElementById('reportsList').innerHTML = '<div class="loading">Loading...</div>';
    
    try {
        // Load track and reports
        currentTrack = await supabaseAdmin.getTrack(trackId);
        currentReports = await supabaseAdmin.getReportsForTrack(trackId);
        
        // Store original for comparison
        originalTrackData = { ...currentTrack };
        
        // Populate current data (editable)
        document.getElementById('currentArtist').value = currentTrack.artist || '';
        document.getElementById('currentTitle').value = currentTrack.song || '';
        document.getElementById('currentDate').value = currentTrack.release_date || '';
        document.getElementById('currentYear').value = currentTrack.release_year || '';
        
        // Populate original data (read-only reference)
        document.getElementById('originalArtist').textContent = currentTrack.artist || '-';
        document.getElementById('originalTitle').textContent = currentTrack.song || '-';
        document.getElementById('originalDate').textContent = currentTrack.release_date || '-';
        document.getElementById('originalYear').textContent = currentTrack.release_year || '-';
        
        // Render reports
        renderReports();
        
    } catch (e) {
        console.error('Failed to load track detail:', e);
        showToast('Failed to load track: ' + e.message, 'error');
    }
}

function renderReports() {
    const reportsList = document.getElementById('reportsList');
    document.getElementById('submissionCount').textContent = currentReports.length;
    
    if (currentReports.length === 0) {
        reportsList.innerHTML = '<div class="loading">No reports for this track.</div>';
        return;
    }
    
    reportsList.innerHTML = currentReports.map((report, index) => {
        const isDone = report.status ? report.status === 'done' : (report.approved || report.reviewed);
        
        // Check for differences
        const artistDiff = report.suggested_artist && report.suggested_artist !== originalTrackData.artist;
        const titleDiff = report.suggested_title && report.suggested_title !== originalTrackData.song;
        const dateDiff = report.suggested_release_date && report.suggested_release_date !== originalTrackData.release_date;
        const yearDiff = report.suggested_year && report.suggested_year !== originalTrackData.release_year;
        
        const submittedDate = new Date(report.submitted_at || report.submittedAt || report.created_at || report.createdAt).toLocaleDateString('de-DE', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
        
        return `
            <div class="data-row report-row ${isDone ? 'done' : ''}" data-report-index="${index}">
                <span class="col-artist ${artistDiff ? 'diff' : ''}" data-field="artist" data-value="${escapeHtml(report.suggested_artist || '')}">
                    ${escapeHtml(report.suggested_artist || '-')}
                </span>
                <span class="col-title ${titleDiff ? 'diff' : ''}" data-field="title" data-value="${escapeHtml(report.suggested_title || '')}">
                    ${escapeHtml(report.suggested_title || '-')}
                </span>
                <span class="col-date ${dateDiff ? 'diff' : ''}" data-field="date" data-value="${escapeHtml(report.suggested_release_date || '')}">
                    ${escapeHtml(report.suggested_release_date || '-')}
                </span>
                <span class="col-year ${yearDiff ? 'diff' : ''}" data-field="year" data-value="${report.suggested_year || ''}">
                    ${report.suggested_year || '-'}
                </span>
                <span class="col-meta">${submittedDate}</span>
                <span class="col-status">
                    <span class="${isDone ? 'status-done' : 'status-pending'}">${isDone ? 'DONE' : 'PENDING'}</span>
                </span>
            </div>
        `;
    }).join('');
    
    // Add click-to-copy handlers
    reportsList.querySelectorAll('.report-row span[data-field]').forEach(cell => {
        cell.addEventListener('click', (e) => {
            e.stopPropagation();
            const field = cell.dataset.field;
            const value = cell.dataset.value;
            
            if (value && value !== '-') {
                copyValueToCurrentRow(field, value);
                showToast(`Copied ${field} value`, 'success');
            }
        });
        
        // Add visual hint on hover
        cell.style.cursor = 'pointer';
        cell.title = 'Click to copy to current data';
    });
    
    // Add hint text
    if (!document.querySelector('.click-hint')) {
        const hint = document.createElement('p');
        hint.className = 'click-hint';
        hint.textContent = '💡 Click on any highlighted value to copy it to the current track data above.';
        reportsList.parentNode.appendChild(hint);
    }
}

function copyValueToCurrentRow(field, value) {
    switch (field) {
        case 'artist':
            document.getElementById('currentArtist').value = value;
            break;
        case 'title':
            document.getElementById('currentTitle').value = value;
            break;
        case 'date':
            document.getElementById('currentDate').value = value;
            break;
        case 'year':
            document.getElementById('currentYear').value = value;
            break;
    }
}

async function handleSave() {
    const btn = document.getElementById('saveBtn');
    btn.disabled = true;
    btn.textContent = 'Saving...';
    
    try {
        const updatedData = {
            artist: document.getElementById('currentArtist').value.trim(),
            title: document.getElementById('currentTitle').value.trim(),
            releaseDate: document.getElementById('currentDate').value.trim(),
            year: document.getElementById('currentYear').value.trim()
        };
        
        // Update track
        await supabaseAdmin.updateTrack(currentTrackId, updatedData);
        
        // Mark all pending reports as done
        await supabaseAdmin.markReportsAsDone(currentTrackId);
        
        showToast('Track updated and reports marked as done!', 'success');
        
        // Go back to list and refresh
        showScreen('main');
        await loadTracksWithReports();
        
    } catch (e) {
        console.error('Save failed:', e);
        showToast('Save failed: ' + e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = '💾 Save & Mark Done';
    }
}

function showScreen(screenName) {
    Object.values(screens).forEach(s => s.classList.add('hidden'));
    screens[screenName].classList.remove('hidden');
}

function showToast(message, type = 'success') {
    const existing = document.querySelector('.toast');
    if (existing) existing.remove();
    
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    
    setTimeout(() => toast.remove(), 3000);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
