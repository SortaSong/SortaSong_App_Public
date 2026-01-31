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

// Feature detection
const hasFileSystemAccess = 'showDirectoryPicker' in window;

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
    if (hasFileSystemAccess) {
        try {
            folderHandle = await window.showDirectoryPicker({ mode: 'readwrite' });
            folderName = folderHandle.name;
            canSaveToFolder = true;
            return true;
        } catch (e) {
            if (e.name !== 'AbortError') {
                console.error('Error selecting folder:', e);
            }
            return false;
        }
    } else {
        // Firefox fallback: use hidden input
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
        <td><input type="text" class="track-title" value="${escapeHtml(track.title || '')}" placeholder="${t('col_title')}"></td>
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
    const titleInput = row.querySelector('.track-title');
    const dateInput = row.querySelector('.track-date');
    const yearInput = row.querySelector('.track-year');
    const filenameInput = row.querySelector('.track-filename');
    
    // Update track data on input
    artistInput.addEventListener('change', () => {
        gameInfo.tracks[index].artist = artistInput.value;
        checkRenameSuggestion(row, index);
    });
    
    titleInput.addEventListener('change', () => {
        gameInfo.tracks[index].title = titleInput.value;
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
    
    // Swap artist/title
    row.querySelector('.btn-swap').addEventListener('click', () => {
        const tempArtist = artistInput.value;
        artistInput.value = titleInput.value;
        titleInput.value = tempArtist;
        gameInfo.tracks[index].artist = artistInput.value;
        gameInfo.tracks[index].title = titleInput.value;
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
    
    const suggested = MetadataReader.suggestFilename(track.artist, track.title, track.originalFileName);
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
        title: '',
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
            title: track.song || '',
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
});
