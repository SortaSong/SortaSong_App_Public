// Initialize Supabase
const SUPABASE_URL = 'https://hjzhojjnjnawwnwhzgwq.supabase.co';
const SUPABASE_ANON_KEY = 'sb_publishable_LATVvS_FmS-sLW_i1T7u1A_Bi9bWah7';
const supabase = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// State
let currentState = {
    token: null,
    password: null,
    submission: null,
    tracks: []
};

// DOM Elements
const loginSection = document.getElementById('loginSection');
const editorSection = document.getElementById('editorSection');
const loginForm = document.getElementById('loginForm');
const loginError = document.getElementById('loginError');
const tracksBody = document.getElementById('tracks-body');
const saveBtn = document.getElementById('saveBtn');
const cancelBtn = document.getElementById('cancelBtn');
const addTrackBtn = document.getElementById('addTrackBtn');
const saveError = document.getElementById('saveError');

// Init
document.addEventListener('DOMContentLoaded', () => {
    // Check URL params
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const password = urlParams.get('password');

    if (token && password) {
        currentState.token = token;
        currentState.password = password;
        
        // Populate login form just in case
        document.getElementById('token').value = token;
        document.getElementById('password').value = password;
        
        // Auto-login
        checkStatus();
    } else {
        loginSection.style.display = 'block';
    }
});

// Login Handler
loginForm.addEventListener('submit', (e) => {
    e.preventDefault();
    currentState.token = document.getElementById('token').value.trim();
    currentState.password = document.getElementById('password').value.trim();
    checkStatus();
});

// Check Status & Load Data
async function checkStatus() {
    loginError.style.display = 'none';
    
    try {
        const { data, error } = await supabase.rpc('check_submission_status', {
            p_token: currentState.token,
            p_password: currentState.password
        });

        if (error) throw error;

        if (!data.success) {
            throw new Error(data.error || 'Login fehlgeschlagen');
        }

        const submission = data.submission;
        
        if (submission.status === 'published') {
            throw new Error('Veröffentlichte Submissions können nicht mehr bearbeitet werden.');
        }

        // Success - Load Editor
        currentState.submission = submission;
        currentState.tracks = submission.tracks || [];
        
        loginSection.style.display = 'none';
        editorSection.style.display = 'block';
        
        populateEditor();
        
    } catch (err) {
        loginError.textContent = err.message;
        loginError.style.display = 'block';
        loginSection.style.display = 'block';
        editorSection.style.display = 'none';
    }
}

// Populate Editor Fields
function populateEditor() {
    document.getElementById('gameName').value = currentState.submission.game_name || '';
    document.getElementById('description').value = currentState.submission.game_description || '';
    document.getElementById('submitterName').value = currentState.submission.submitted_by_name || '';
    
    renderTracks();
}

// Render Tracks Table
function renderTracks() {
    tracksBody.innerHTML = '';
    
    currentState.tracks.forEach((track, index) => {
        const tr = document.createElement('tr');
        
        tr.innerHTML = `
            <td>${index + 1}</td>
            <td><input type="text" value="${escapeHtml(track.artist || '')}" onchange="updateTrack(${index}, 'artist', this.value)"></td>
            <td><input type="text" value="${escapeHtml(track.song || '')}" onchange="updateTrack(${index}, 'song', this.value)"></td>
            <td><input type="text" value="${escapeHtml(track.releaseDate || '')}" placeholder="DD.MM.YYYY" onchange="updateTrack(${index}, 'releaseDate', this.value)"></td>
            <td><input type="number" value="${track.releaseYear || ''}" style="width: 80px;" onchange="updateTrack(${index}, 'releaseYear', this.value)"></td>
            <td>
                <button class="btn-danger" onclick="removeTrack(${index})">🗑️</button>
            </td>
        `;
        
        tracksBody.appendChild(tr);
    });
}

// Track Operations
window.updateTrack = (index, field, value) => {
    if (field === 'releaseYear') {
        value = parseInt(value) || 0;
    }
    currentState.tracks[index][field] = value;
    
    // Auto-parse year from date if year is empty
    if (field === 'releaseDate' && !currentState.tracks[index].releaseYear) {
        const match = value.match(/\d{4}/);
        if (match) {
            currentState.tracks[index].releaseYear = parseInt(match[0]);
            renderTracks(); // Re-render to show updated year
        }
    }
};

window.removeTrack = (index) => {
    if (confirm('Track wirklich löschen?')) {
        currentState.tracks.splice(index, 1);
        renderTracks();
    }
};

addTrackBtn.addEventListener('click', () => {
    currentState.tracks.push({
        trackNr: (currentState.tracks.length + 1).toString(),
        artist: '',
        song: '',
        releaseDate: '',
        releaseYear: 0
    });
    renderTracks();
});

// Save Changes
saveBtn.addEventListener('click', async () => {
    saveError.style.display = 'none';
    saveBtn.disabled = true;
    saveBtn.textContent = 'Speichert...';
    
    try {
        const gameName = document.getElementById('gameName').value.trim();
        const description = document.getElementById('description').value.trim();
        const submitterName = document.getElementById('submitterName').value.trim();
        
        if (!gameName) throw new Error('Spielname ist erforderlich.');
        if (currentState.tracks.length === 0) throw new Error('Mindestens ein Track ist erforderlich.');

        // Re-number tracks
        const tracks = currentState.tracks.map((t, i) => ({
            ...t,
            trackNr: (i + 1).toString()
        }));

        const { data, error } = await supabase.rpc('update_submission', {
            p_token: currentState.token,
            p_password: currentState.password,
            p_game_name: gameName,
            p_game_description: description,
            p_folder_name: currentState.submission.folder_name, // Keep existing
            p_link_identifier: currentState.submission.link_identifier, // Keep existing
            p_tracks: tracks,
            p_submitted_by_name: submitterName || null
        });

        if (error) throw error;
        
        if (!data.success) {
            throw new Error(data.error || 'Speichern fehlgeschlagen');
        }

        // Success - Redirect to check-status
        window.location.href = `../check-status/index.html?token=${currentState.token}&password=${currentState.password}`;
        
    } catch (err) {
        saveError.textContent = err.message;
        saveError.style.display = 'block';
        saveBtn.disabled = false;
        saveBtn.textContent = '💾 Änderungen speichern';
    }
});

cancelBtn.addEventListener('click', () => {
    window.location.href = `../check-status/index.html?token=${currentState.token}&password=${currentState.password}`;
});

// Helper
function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
