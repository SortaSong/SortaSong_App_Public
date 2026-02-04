/**
 * SortaSong Custom Game Editor - Main Editor Logic
 * Supports both File System Access API (Chrome/Edge) and fallback (Firefox/Safari)
 */

// State
let currentStep = 'workflow';
let folderHandle = null;      // For File System Access API
let folderFiles = null;       // For fallback: array of File objects
let folderName = '';          // Folder name from selection
let canSaveToFolder = false;  // True only with File System Access API
let gameInfo = {
    game: '',
    folderName: '',
    linkIdentifier: '',
    cardPurchaseUrl: '',
    hasPhysicalCards: false,
    isCustom: true,
    createdAt: new Date().toISOString(),
    tracks: []
};

// Feature detection - try to detect if File System Access API actually works
// Chrome Android has showDirectoryPicker but it throws errors
const hasFileSystemAccess = 'showDirectoryPicker' in window;
let fileSystemAccessWorks = hasFileSystemAccess; // Will be set to false if it fails

// DOM Elements
const steps = {
    workflow: document.getElementById('step-workflow'),
    metadata: document.getElementById('step-metadata'),
    editor: document.getElementById('step-editor')
};

const elements = {
    metadataForm: document.getElementById('metadata-form'),
    gameName: document.getElementById('game-name'),
    folderName: document.getElementById('folder-name'),
    linkIdentifier: document.getElementById('link-identifier'),
    cardUrl: document.getElementById('card-url'),
    tracksBody: document.getElementById('tracks-body'),
    emptyState: document.getElementById('empty-state'),
    guideModal: document.getElementById('guide-modal'),
    folderInput: document.getElementById('folder-input'),
    jsonInput: document.getElementById('json-input')
};

// Step navigation
function showStep(stepName) {
    Object.keys(steps).forEach(key => {
        steps[key].classList.toggle('active', key === stepName);
    });
    currentStep = stepName;
}

// Workflow handlers
async function handleWorkflow(workflow) {
    switch (workflow) {
        case 'scratch':
            showStep('metadata');
            break;
            
        case 'folder':
            if (await selectFolder()) {
                // Try to read existing game_info.json
                const existing = await loadGameInfoFromSelection();
                if (existing) {
                    gameInfo = { ...gameInfo, ...existing };
                    populateMetadataForm();
                    showStep('metadata');
                } else {
                    // Scan folder for tracks
                    await scanFolderForTracks();
                    // Pre-fill folder name from selected folder
                    elements.folderName.value = folderName;
                    gameInfo.folderName = folderName;
                    showStep('metadata');
                }
            }
            break;
            
        case 'existing':
            if (hasFileSystemAccess) {
                if (await selectFolder()) {
                    const existing = await loadGameInfoFromSelection();
                    if (existing) {
                        gameInfo = { ...gameInfo, ...existing };
                        populateMetadataForm();
                        renderTracks();
                        showStep('metadata');
                    } else {
                        alert('game_info.json not found in folder');
                    }
                }
            } else {
                // Firefox fallback: use file picker for JSON
                elements.jsonInput.click();
            }
            break;
    }
}

// Load game_info.json from current selection (works for both APIs)
async function loadGameInfoFromSelection() {
    if (folderHandle) {
        return await MetadataReader.loadGameInfo(folderHandle);
    } else if (folderFiles) {
        const jsonFile = folderFiles.find(f => f.name === 'game_info.json');
        if (jsonFile) {
            const content = await jsonFile.text();
            return JSON.parse(content);
        }
    }
    return null;
}

// Folder selection - uses File System Access API or fallback
async function selectFolder() {
    // Try File System Access API first (if it previously worked or hasn't been tried)
    if (fileSystemAccessWorks) {
        try {
            folderHandle = await window.showDirectoryPicker({ mode: 'readwrite' });
            folderName = folderHandle.name;
            canSaveToFolder = true;
            return true;
        } catch (e) {
            if (e.name === 'AbortError') {
                // User cancelled - don't fall back
                return false;
            }
            // API exists but doesn't work (e.g., Chrome Android) - use fallback
            console.log('File System Access API failed, using fallback:', e.message);
            fileSystemAccessWorks = false;
            folderHandle = null;
        }
    }
    
    // Fallback: use hidden file input
    return new Promise((resolve) => {
        elements.folderInput.onchange = (e) => {
            const files = Array.from(e.target.files);
            if (files.length > 0) {
                folderFiles = files;
                // Extract folder name from path
                const path = files[0].webkitRelativePath || files[0].name;
                folderName = path.split('/')[0];
                canSaveToFolder = false;
                resolve(true);
            } else {
                resolve(false);
            }
            elements.folderInput.value = ''; // Reset for next use
        };
        elements.folderInput.click();
    });
}

// Scan folder for audio files
async function scanFolderForTracks() {
    // If we don't have a selection yet, prompt for one
    if (!folderHandle && !folderFiles) {
        if (!await selectFolder()) return;
    }
    
    const statusEl = document.createElement('div');
    statusEl.className = 'loading';
    statusEl.textContent = t('msg_scanning');
    steps[currentStep].appendChild(statusEl);
    
    try {
        let tracks;
        if (folderHandle) {
            // File System Access API
            tracks = await MetadataReader.scanFolder(folderHandle, (current, total) => {
                statusEl.textContent = `${t('msg_reading_metadata')} ${current}/${total}`;
            });
        } else if (folderFiles) {
            // Fallback: scan from file list
            tracks = await MetadataReader.scanFiles(folderFiles, (current, total) => {
                statusEl.textContent = `${t('msg_reading_metadata')} ${current}/${total}`;
            });
        }
        
        if (!tracks || tracks.length === 0) {
            alert(t('msg_no_audio_files'));
        } else {
            gameInfo.tracks = tracks;
            renderTracks();
        }
    } catch (e) {
        console.error('Error scanning folder:', e);
        alert('Error: ' + e.message);
    } finally {
        statusEl.remove();
    }
}

// Populate metadata form from gameInfo
function populateMetadataForm() {
    elements.gameName.value = gameInfo.game || '';
    elements.folderName.value = gameInfo.folderName || '';
    elements.linkIdentifier.value = gameInfo.linkIdentifier || '';
    elements.cardUrl.value = gameInfo.cardPurchaseUrl || '';
    
    // Set physical cards toggle
    const hasCards = gameInfo.hasPhysicalCards || false;
    document.querySelectorAll('.toggle-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.value === String(hasCards));
    });
    document.querySelectorAll('.card-fields').forEach(el => {
        el.style.display = hasCards ? 'block' : 'none';
    });
}

// Read metadata form into gameInfo
function readMetadataForm() {
    gameInfo.game = elements.gameName.value.trim();
    gameInfo.folderName = elements.folderName.value.trim();
    gameInfo.linkIdentifier = elements.linkIdentifier.value.trim();
    gameInfo.cardPurchaseUrl = elements.cardUrl.value.trim();
}

// Render tracks table
function renderTracks() {
    elements.tracksBody.innerHTML = '';
    
    if (gameInfo.tracks.length === 0) {
        elements.emptyState.classList.remove('hidden');
        return;
    }
    
    elements.emptyState.classList.add('hidden');
    
    gameInfo.tracks.forEach((track, index) => {
        const row = createTrackRow(track, index);
        elements.tracksBody.appendChild(row);
    });
}

// Create a track row
function createTrackRow(track, index) {
    const row = document.createElement('tr');
    row.dataset.index = index;
    
    row.innerHTML = `
        <td class="track-number">${index + 1}</td>
        <td><input type="text" class="track-artist" value="${escapeHtml(track.artist || '')}" placeholder="${t('col_artist')}"></td>
        <td><input type="text" class="track-song" value="${escapeHtml(track.song || '')}" placeholder="${t('col_title')}"></td>
        <td><input type="text" class="track-date" value="${escapeHtml(track.releaseDate || '')}" placeholder="DD.MM.YYYY"></td>
        <td><input type="number" class="track-year" value="${track.releaseYear || ''}" placeholder="YYYY" min="1900" max="2100"></td>
        <td><input type="text" class="track-filename" value="${escapeHtml(track.originalFileName || '')}" readonly></td>
        <td class="actions-cell">
            <button type="button" class="btn-swap" title="${t('swap')}">${t('swap')}</button>
            <button type="button" class="btn-delete" title="${t('delete')}">${t('delete')}</button>
        </td>
    `;
    
    // Event listeners
    const artistInput = row.querySelector('.track-artist');
    const songInput = row.querySelector('.track-song');
    const dateInput = row.querySelector('.track-date');
    const yearInput = row.querySelector('.track-year');
    const filenameInput = row.querySelector('.track-filename');
    
    // Update track data on input
    artistInput.addEventListener('change', () => {
        gameInfo.tracks[index].artist = artistInput.value;
        checkRenameSuggestion(row, index);
    });
    
    songInput.addEventListener('change', () => {
        gameInfo.tracks[index].song = songInput.value;
        checkRenameSuggestion(row, index);
    });
    
    dateInput.addEventListener('change', () => {
        gameInfo.tracks[index].releaseDate = dateInput.value;
        // Auto-fill year if empty
        if (!yearInput.value && dateInput.value) {
            const year = MetadataReader.extractYear(dateInput.value);
            if (year) {
                yearInput.value = year;
                gameInfo.tracks[index].releaseYear = year;
            }
        }
    });
    
    yearInput.addEventListener('change', () => {
        gameInfo.tracks[index].releaseYear = yearInput.value ? parseInt(yearInput.value) : null;
    });
    
    // Swap artist/song
    row.querySelector('.btn-swap').addEventListener('click', () => {
        const tempArtist = artistInput.value;
        artistInput.value = songInput.value;
        songInput.value = tempArtist;
        gameInfo.tracks[index].artist = artistInput.value;
        gameInfo.tracks[index].song = songInput.value;
        checkRenameSuggestion(row, index);
    });
    
    // Delete track
    row.querySelector('.btn-delete').addEventListener('click', () => {
        if (confirm(t('msg_confirm_delete'))) {
            gameInfo.tracks.splice(index, 1);
            renderTracks();
        }
    });
    
    return row;
}

// Check if we should suggest renaming the file
function checkRenameSuggestion(row, index) {
    const track = gameInfo.tracks[index];
    const existingSuggestion = row.querySelector('.rename-suggestion');
    if (existingSuggestion) {
        existingSuggestion.remove();
    }
    
    // Only show rename suggestion if we have File System Access API
    if (!track.originalFileName || !folderHandle || !canSaveToFolder) return;
    
    const suggested = MetadataReader.suggestFilename(track.artist, track.song, track.originalFileName);
    if (!suggested) return;
    
    const suggestionEl = document.createElement('div');
    suggestionEl.className = 'rename-suggestion';
    suggestionEl.innerHTML = `
        <span>${t('rename_suggestion')} <strong>${escapeHtml(suggested)}</strong></span>
        <button type="button">${t('rename_apply')}</button>
    `;
    
    suggestionEl.querySelector('button').addEventListener('click', async () => {
        const success = await MetadataReader.renameFile(folderHandle, track.originalFileName, suggested);
        if (success) {
            track.originalFileName = suggested;
            row.querySelector('.track-filename').value = suggested;
            suggestionEl.remove();
        }
    });
    
    row.querySelector('.actions-cell').after(suggestionEl);
}

// Add new empty track
function addTrack() {
    gameInfo.tracks.push({
        trackNr: (gameInfo.tracks.length + 1).toString(),
        artist: '',
        song: '',
        releaseDate: '',
        releaseYear: null,
        originalFileName: ''
    });
    renderTracks();
    
    // Focus the new row's artist input
    const lastRow = elements.tracksBody.lastElementChild;
    if (lastRow) {
        lastRow.querySelector('.track-artist').focus();
    }
}

// Add track from selection (or empty)
function addTrackFromSelection(track) {
    if (track === null) {
        // Create empty track
        addTrack();
    } else {
        // Add selected track
        gameInfo.tracks.push({
            trackNr: (gameInfo.tracks.length + 1).toString(),
            artist: track.artist || '',
            song: track.song || '',
            releaseDate: track.releaseDate || '',
            releaseYear: track.releaseYear,
            originalFileName: track.originalFileName || ''
        });
        renderTracks();
    }
}

// Track picker instance
let showTrackPicker = null;

// Save game_info.json
async function saveGame() {
    readMetadataForm();
    
    // Validate
    if (!gameInfo.game || !gameInfo.folderName) {
        alert(t('msg_invalid_form'));
        return;
    }
    
    if (gameInfo.tracks.length === 0) {
        alert(t('msg_no_tracks_to_save'));
        return;
    }
    
    // Update track numbers
    gameInfo.tracks.forEach((track, index) => {
        track.trackNr = (index + 1).toString();
    });
    
    // Update timestamp
    gameInfo.createdAt = gameInfo.createdAt || new Date().toISOString();
    gameInfo.isCustom = true;
    
    if (folderHandle && canSaveToFolder) {
        // Save directly to folder (Chrome/Edge)
        try {
            await MetadataReader.saveGameInfo(folderHandle, gameInfo);
            alert(t('msg_saved'));
            showGuideModal();
        } catch (e) {
            console.error('Error saving:', e);
            downloadGameInfo();
        }
    } else {
        // Download as file (Firefox/Safari or fallback)
        downloadGameInfo();
    }
}

// Download game_info.json
function downloadGameInfo() {
    const blob = new Blob([JSON.stringify(gameInfo, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'game_info.json';
    a.click();
    URL.revokeObjectURL(url);
    
    alert(t('msg_saved'));
    showGuideModal();
}

// Show transfer guide modal
function showGuideModal() {
    elements.guideModal.classList.add('active');
}

// Utility: escape HTML
function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    // Workflow buttons
    document.querySelectorAll('.workflow-btn').forEach(btn => {
        btn.addEventListener('click', () => handleWorkflow(btn.dataset.workflow));
    });
    
    // Physical cards toggle
    document.querySelectorAll('.toggle-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.toggle-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            
            const hasCards = btn.dataset.value === 'true';
            gameInfo.hasPhysicalCards = hasCards;
            document.querySelectorAll('.card-fields').forEach(el => {
                el.style.display = hasCards ? 'block' : 'none';
            });
        });
    });
    
    // Metadata form
    elements.metadataForm.addEventListener('submit', (e) => {
        e.preventDefault();
        readMetadataForm();
        
        if (!gameInfo.game || !gameInfo.folderName) {
            alert(t('msg_invalid_form'));
            return;
        }
        
        renderTracks();
        showStep('editor');
    });
    
    // Back buttons
    document.getElementById('btn-back-workflow').addEventListener('click', () => showStep('workflow'));
    document.getElementById('btn-back-metadata').addEventListener('click', () => showStep('metadata'));
    
    // Editor buttons
    document.getElementById('btn-scan-folder').addEventListener('click', scanFolderForTracks);
    document.getElementById('btn-add-track').addEventListener('click', () => {
        if (showTrackPicker) {
            showTrackPicker();
        } else {
            addTrack();
        }
    });
    document.getElementById('btn-save').addEventListener('click', saveGame);
    
    // Initialize track picker
    showTrackPicker = SupabaseTracks.initTrackPicker(addTrackFromSelection);
    
    // Modal close
    document.querySelector('.modal .close').addEventListener('click', () => {
        elements.guideModal.classList.remove('active');
    });
    
    elements.guideModal.addEventListener('click', (e) => {
        if (e.target === elements.guideModal) {
            elements.guideModal.classList.remove('active');
        }
    });
    
    // Auto-generate folder name from game name (only for "start from scratch")
    let folderSelectedFromPicker = false;
    
    elements.gameName.addEventListener('input', () => {
        // Only auto-fill folder name if user hasn't selected a folder
        if (!folderSelectedFromPicker && !folderHandle && !folderFiles) {
            const sanitized = elements.gameName.value
                .replace(/[^a-zA-Z0-9äöüÄÖÜß\s\-]/g, '')
                .replace(/\s+/g, '_')
                .substring(0, 50);
            elements.folderName.value = sanitized;
            gameInfo.folderName = sanitized;
        }
    });
    
    // Track when folder is selected
    const originalSelectFolder = selectFolder;
    selectFolder = async function() {
        const result = await originalSelectFolder();
        if (result) {
            folderSelectedFromPicker = true;
            // When folder is selected, prefill game name from folder name if game name is empty
            if (!elements.gameName.value && folderName) {
                const prettyName = folderName
                    .replace(/_/g, ' ')
                    .replace(/-/g, ' ');
                elements.gameName.value = prettyName;
                gameInfo.game = prettyName;
            }
        }
        return result;
    };
    
    // JSON file input handler (Firefox fallback for "existing game")
    elements.jsonInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        if (file) {
            try {
                const content = await file.text();
                const existing = JSON.parse(content);
                gameInfo = { ...gameInfo, ...existing };
                populateMetadataForm();
                renderTracks();
                showStep('metadata');
            } catch (err) {
                alert('Error reading JSON: ' + err.message);
            }
        }
        elements.jsonInput.value = '';
    });
    
    // Share with Community button
    document.getElementById('btn-share').addEventListener('click', showShareModal);
    
    // Share modal handlers
    document.getElementById('btn-cancel-share').addEventListener('click', hideShareModal);
    document.getElementById('btn-submit-share').addEventListener('click', submitToCommunitiy);
    
    // Share modal close
    const shareModal = document.getElementById('share-modal');
    shareModal.querySelector('.close').addEventListener('click', hideShareModal);
    shareModal.addEventListener('click', (e) => {
        if (e.target === shareModal) hideShareModal();
    });
});

// Share with Community functions
function showShareModal() {
    // Validate game first
    if (!gameInfo.game || gameInfo.tracks.length === 0) {
        alert(t('share_error_empty') || 'Please add a game name and at least one track before sharing.');
        return;
    }
    
    // Populate modal
    document.getElementById('share-game-name').textContent = gameInfo.game;
    document.getElementById('share-track-count').textContent = gameInfo.tracks.length;
    document.getElementById('share-description').value = '';
    if (document.getElementById('submitterName')) {
        document.getElementById('submitterName').value = '';
    }
    
    // Reset status - use inline styles
    document.getElementById('share-status').style.display = 'none';
    document.getElementById('share-success').style.display = 'none';
    document.getElementById('share-success').innerHTML = '';
    document.getElementById('share-error').style.display = 'none';
    document.getElementById('share-error').innerHTML = '';
    document.getElementById('btn-submit-share').disabled = false;
    document.getElementById('btn-submit-share').textContent = '📤 Einreichen';
    
    document.getElementById('share-modal').classList.add('active');
}

function hideShareModal() {
    document.getElementById('share-modal').classList.remove('active');
}

async function submitToCommunitiy() {
    const btn = document.getElementById('btn-submit-share');
    btn.disabled = true;
    btn.textContent = t('submitting') || 'Submitting...';
    
    try {
        const description = document.getElementById('share-description').value.trim();
        const submitterName = document.getElementById('submitterName')?.value || null;
        
        // Generate a random password for the user
        const password = generatePassword();
        
        // Hash the password client-side (SHA-256)
        const passwordHash = await hashPassword(password);
        
        // Submit to Supabase
        const result = await submitGameToSupabase({
            description: description,
            submittedByName: submitterName,
            gameData: gameInfo,
            passwordHash: passwordHash
        });
        
        if (result.success) {
            // Show success with submission ID and password
            showSubmissionSuccess(result.submission_id, password);
            btn.textContent = '✓ ' + (t('submitted') || 'Submitted');
        } else {
            throw new Error(result.error || 'Submission failed');
        }
        
    } catch (e) {
        console.error('Share error:', e);
        
        // Show user-friendly error message
        const errorDiv = document.getElementById('share-error');
        let errorMessage = '❌ Fehler beim Einreichen. ';
        
        // Check if it's the missing function error (SQL not run)
        if (e.message.includes('404') || e.message.includes('submit_community_game')) {
            errorMessage += '<br><br><strong>⚠️ Datenbank-Setup erforderlich:</strong><br>';
            errorMessage += 'Der Administrator muss zunächst die SQL-Migration ausführen.<br>';
            errorMessage += '<small>Fehler: RPC-Funktion submit_community_game wurde nicht gefunden.</small>';
        } else {
            errorMessage += e.message || 'Bitte versuche es später erneut.';
        }
        
        errorDiv.innerHTML = errorMessage;
        document.getElementById('share-status').style.display = 'block';
        errorDiv.style.display = 'block';
        document.getElementById('share-success').style.display = 'none';
        btn.disabled = false;
        btn.textContent = t('submit_share') || '📤 Einreichen';
    }
}

// Generate a random password (12 chars, alphanumeric)
function generatePassword() {
    const chars = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    let password = '';
    for (let i = 0; i < 12; i++) {
        password += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return password;
}

// Hash password using SHA-256
async function hashPassword(password) {
    const encoder = new TextEncoder();
    const data = encoder.encode(password);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

async function submitGameToSupabase(data) {
    const SUPABASE_URL = 'https://hjzhojjnjnawwnwhzgwq.supabase.co';
    const apiKey = SupabaseTracks.getApiKey();
    
    // Use RPC function for submission
    const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/submit_community_game`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'apikey': apiKey,
            'Authorization': `Bearer ${apiKey}`
        },
        body: JSON.stringify({
            p_game_data: data.gameData,
            p_description: data.description || null,
            p_submitted_by_name: data.submittedByName || null,
            p_submission_password_hash: data.passwordHash
        })
    });
    
    if (!response.ok) {
        const error = await response.text();
        console.error('Supabase error:', error);
        throw new Error('Server error: ' + response.status);
    }
    
    const result = await response.json();
    console.log('Submission result:', result);
    
    // RPC returns the JSONB directly
    if (result && typeof result === 'object') {
        return result;
    }
    return { success: false, message: 'Unknown error' };
}

// Show submission success with credentials
function showSubmissionSuccess(submissionId, password) {
    // Show success section in modal
    document.getElementById('share-status').style.display = 'block';
    document.getElementById('share-success').style.display = 'block';
    document.getElementById('share-error').style.display = 'none';
    
    // Hide the form elements
    document.querySelector('.share-form').style.display = 'none';
    document.querySelector('.share-actions').style.display = 'none';
    
    // Create credentials display
    const successDiv = document.getElementById('share-success');
    successDiv.innerHTML = `
        <h3 style="color: #16a34a; margin-bottom: 1rem;">✅ Erfolgreich eingereicht!</h3>
        
        <div style="background: #fef3c7; border: 2px solid #f59e0b; border-radius: 8px; padding: 1.5rem; margin: 1rem 0;">
            <h4 style="color: #92400e; margin-bottom: 0.5rem;">⚠️ WICHTIG: Speichern Sie diese Zugangsdaten!</h4>
            <p style="color: #78350f; margin-bottom: 1rem;">
                Sie benötigen diese, um den Status Ihrer Submission zu prüfen oder sie zu bearbeiten.
                <strong>Diese Daten werden nur einmal angezeigt!</strong>
            </p>
        </div>
        
        <div style="background: #f9fafb; border: 2px solid #e5e7eb; border-radius: 8px; padding: 1.5rem; margin: 1rem 0;">
            <div style="margin-bottom: 1rem;">
                <label style="display: block; font-weight: 600; margin-bottom: 0.5rem; color: #374151;">
                    📋 Submission-ID:
                </label>
                <div style="display: flex; gap: 0.5rem;">
                    <input 
                        type="text" 
                        id="submissionId" 
                        value="${submissionId}" 
                        readonly
                        style="flex: 1; padding: 0.75rem; font-family: monospace; font-size: 1em; border: 2px solid #d1d5db; border-radius: 6px; background: white;"
                    >
                    <button onclick="copyToClipboard('submissionId', this)" style="padding: 0.75rem 1rem; background: #2563eb; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600;">
                        📋 Kopieren
                    </button>
                </div>
            </div>
            
            <div style="margin-bottom: 1rem;">
                <label style="display: block; font-weight: 600; margin-bottom: 0.5rem; color: #374151;">
                    🔐 Passwort:
                </label>
                <div style="display: flex; gap: 0.5rem;">
                    <input 
                        type="text" 
                        id="submissionPassword" 
                        value="${password}" 
                        readonly
                        style="flex: 1; padding: 0.75rem; font-family: monospace; font-size: 1.1em; border: 2px solid #d1d5db; border-radius: 6px; background: white;"
                    >
                    <button onclick="copyToClipboard('submissionPassword', this)" style="padding: 0.75rem 1rem; background: #2563eb; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600;">
                        📋 Kopieren
                    </button>
                </div>
            </div>
            
            <button 
                onclick="downloadCredentials('${submissionId}', '${password}')"
                style="width: 100%; padding: 0.75rem; background: #16a34a; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; margin-top: 0.5rem;"
            >
                💾 Als Textdatei herunterladen
            </button>
        </div>
        
        <div style="margin-top: 1.5rem;">
            <a 
                href="../check-status/?id=${submissionId}&password=${encodeURIComponent(password)}" 
                style="display: inline-block; padding: 0.75rem 1.5rem; background: #2563eb; color: white; text-decoration: none; border-radius: 8px; font-weight: 600; margin-right: 0.5rem;"
            >
                📊 Status jetzt prüfen →
            </a>
            <button 
                onclick="location.reload()"
                style="padding: 0.75rem 1.5rem; background: #6b7280; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;"
            >
                Neue Submission erstellen
            </button>
        </div>
    `;
}

// Copy to clipboard helper
function copyToClipboard(inputId, button) {
    const input = document.getElementById(inputId);
    input.select();
    input.setSelectionRange(0, 99999); // For mobile
    
    try {
        document.execCommand('copy');
        const originalText = button.textContent;
        button.textContent = '✓ Kopiert!';
        button.style.background = '#16a34a';
        
        setTimeout(() => {
            button.textContent = originalText;
            button.style.background = '#2563eb';
        }, 2000);
    } catch (err) {
        console.error('Copy failed:', err);
        alert('Kopieren fehlgeschlagen. Bitte manuell kopieren.');
    }
}

// Download credentials as text file
function downloadCredentials(token, password) {
    const content = `SortaSong Submission Zugangsdaten
=====================================

Token: ${token}
Passwort: ${password}

WICHTIG: Bewahren Sie diese Daten sicher auf!

Sie benötigen diese, um:
- Den Status Ihrer Submission zu prüfen
- Ihre Submission zu bearbeiten

Status prüfen:
https://sortasong.github.io/SortaSong_App_Public/check-status/?token=${token}&password=${encodeURIComponent(password)}

Erstellt: ${new Date().toLocaleString('de-DE')}
`;
    
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `sortasong-submission-${token}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
