// Community Playlists Logic
// Handles fetching, voting, and downloading community submissions

const SUPABASE_URL = 'https://hjzhojjnjnawwnwhzgwq.supabase.co';
// Anon key for public access
const SUPABASE_ANON_KEY = 'sb_publishable_LATVvS_FmS-sLW_i1T7u1A_Bi9bWah7';

const supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// State
let allGames = [];
let filteredGames = [];
let votedGames = [];
const STORAGE_KEY = 'sortasong_votes';

// Initialization
document.addEventListener('DOMContentLoaded', () => {
    loadVotesFromStorage();
    fetchCommunityGames();
    setupEventListeners();
});

// Load votes from local storage
function loadVotesFromStorage() {
    try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
            votedGames = JSON.parse(stored);
        }
    } catch (e) {
        console.error('Error loading votes from storage:', e);
        votedGames = [];
    }
}

// Save vote to local storage
function saveVote(submissionId) {
    if (!votedGames.includes(submissionId)) {
        votedGames.push(submissionId);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(votedGames));
    }
}

// Check if already voted
function hasVoted(submissionId) {
    return votedGames.includes(submissionId);
}

// Fetch games from Supabase
async function fetchCommunityGames() {
    const loadingState = document.getElementById('loadingState');
    const errorState = document.getElementById('errorState');
    const gamesGrid = document.getElementById('gamesGrid');
    const emptyState = document.getElementById('emptyState');
    
    loadingState.style.display = 'block';
    errorState.style.display = 'none';
    if (emptyState) emptyState.style.display = 'none';
    gamesGrid.innerHTML = '';
    
    try {
        const { data, error } = await supabaseClient
            .from('community_submissions')
            .select('*')
            .in('status', ['approved'])
            .order('created_at', { ascending: false });
            
        if (error) throw error;
        
        allGames = data || [];
        
        if (allGames.length === 0) {
            loadingState.style.display = 'none';
            showEmptyState();
            return;
        }
        
        applyFilters(); // Initial render
        
    } catch (error) {
        console.error('Error fetching games:', error);
        loadingState.style.display = 'none';
        errorState.style.display = 'block';
        document.getElementById('errorMessage').textContent = 'Fehler beim Laden: ' + (error.message || 'Unbekannter Fehler');
    } finally {
        loadingState.style.display = 'none';
    }
}

// Show empty state when no games available
function showEmptyState() {
    const gamesGrid = document.getElementById('gamesGrid');
    gamesGrid.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1; text-align: center; padding: 3rem;">
            <h3 style="color: #94a3b8; margin-bottom: 1rem;">🎵 Noch keine Community-Spiele verfügbar</h3>
            <p style="color: #64748b; margin-bottom: 1.5rem;">
                Sei der Erste! Erstelle eine Playlist und teile sie mit der Community.
            </p>
            <a href="../editor/" class="btn-primary" style="display: inline-block; padding: 0.75rem 1.5rem; background: #2563eb; color: white; text-decoration: none; border-radius: 8px; font-weight: 600;">
                🎨 Playlist erstellen →
            </a>
        </div>
    `;
}

// Setup event listeners
function setupEventListeners() {
    document.getElementById('searchInput').addEventListener('input', debounce(() => {
        applyFilters();
    }, 300));
    
    document.getElementById('sortOrder').addEventListener('change', () => {
        applyFilters();
    });
    
    document.getElementById('retryBtn').addEventListener('click', () => {
        fetchCommunityGames();
    });
}

// Apply filters and sort
function applyFilters() {
    const searchTerm = document.getElementById('searchInput').value.toLowerCase().trim();
    const sortOrder = document.getElementById('sortOrder').value;
    
    // Filter
    filteredGames = allGames.filter(game => {
        const gd = game.game_data || {};
        const gameName = gd.game || '';
        const matchesSearch = !searchTerm || 
            gameName.toLowerCase().includes(searchTerm) || 
            (game.description && game.description.toLowerCase().includes(searchTerm)) ||
            (game.submitted_by_name && game.submitted_by_name.toLowerCase().includes(searchTerm));
            
        return matchesSearch;
    });
    
    // Sort
    filteredGames.sort((a, b) => {
        switch (sortOrder) {
            case 'votes':
                return (b.vote_count || 0) - (a.vote_count || 0);
            case 'downloads':
                return (b.download_count || 0) - (a.download_count || 0);
            case 'newest':
            default:
                return new Date(b.created_at) - new Date(a.created_at);
        }
    });
    
    renderGames();
}

// Render games grid
function renderGames() {
    const gamesGrid = document.getElementById('gamesGrid');
    const noResults = document.getElementById('noResults');
    const template = document.getElementById('gameCardTemplate');
    
    gamesGrid.innerHTML = '';
    
    if (filteredGames.length === 0) {
        if (allGames.length === 0) {
            showEmptyState();
        } else {
            noResults.style.display = 'block';
        }
        return;
    }
    
    noResults.style.display = 'none';
    
    filteredGames.forEach(game => {
        const gd = game.game_data || {};
        const gameName = gd.game || 'Unknown';
        const tracks = gd.tracks || [];
        
        const clone = template.content.cloneNode(true);
        const card = clone.querySelector('.game-card');
        
        // Populate data
        clone.querySelector('.game-title').textContent = gameName;
        clone.querySelector('.track-count').textContent = tracks.length;
        clone.querySelector('.game-description').textContent = game.description || 'Keine Beschreibung verfügbar.';
        
        const submitterName = game.submitted_by_name || 'Anonym';
        clone.querySelector('.submitter-name').textContent = submitterName;
        
        const date = new Date(game.created_at);
        clone.querySelector('.date-value').textContent = date.toLocaleDateString('de-DE');
        
        const voteCountEl = clone.querySelector('.vote-count');
        voteCountEl.textContent = game.vote_count || 0;
        
        clone.querySelector('.download-count').textContent = game.download_count || 0;
        
        // Vote button logic
        const voteBtn = clone.querySelector('.btn-vote');
        const icon = voteBtn.querySelector('i');
        
        if (hasVoted(game.id)) {
            voteBtn.classList.add('voted');
            icon.classList.remove('far');
            icon.classList.add('fas'); // Solid star
            voteBtn.title = "Du hast bereits abgestimmt";
        } else {
            voteBtn.onclick = () => handleVote(game, voteBtn, voteCountEl);
        }
        
        // Track list toggle
        const detailsBtn = clone.querySelector('.btn-details');
        const trackListContainer = clone.querySelector('.track-list-container');
        const trackList = clone.querySelector('.track-list');
        
        detailsBtn.onclick = () => {
            if (trackListContainer.style.display === 'none') {
                // Populate if empty
                if (trackList.children.length === 0 && tracks.length > 0) {
                    tracks.forEach(track => {
                        const li = document.createElement('li');
                        li.innerHTML = `
                            <span class="track-year">${track.releaseYear || track.year || '?'}</span>
                            <span class="track-name">${escapeHtml(track.artist)} - ${escapeHtml(track.song || track.title)}</span>
                        `;
                        trackList.appendChild(li);
                    });
                }
                trackListContainer.style.display = 'block';
                detailsBtn.innerHTML = '<i class="fas fa-chevron-up"></i> Einklappen';
            } else {
                trackListContainer.style.display = 'none';
                detailsBtn.innerHTML = '<i class="fas fa-list"></i> Trackliste';
            }
        };
        
        // Download button
        clone.querySelector('.btn-download').onclick = () => handleDownload(game);
        
        gamesGrid.appendChild(clone);
    });
}

// Handle voting
async function handleVote(game, btn, countEl) {
    if (hasVoted(game.id)) return;
    
    // Optimistic update
    const currentCount = parseInt(countEl.textContent) || 0;
    countEl.textContent = currentCount + 1;
    btn.classList.add('voted');
    btn.querySelector('i').classList.replace('far', 'fas');
    
    // Save locally immediately to prevent double clicks
    saveVote(game.id);
    
    // Remove handler
    btn.onclick = null;
    
    try {
        // Update vote count directly
        const { error } = await supabaseClient
            .from('community_submissions')
            .update({ vote_count: currentCount + 1 })
            .eq('id', game.id);
        
        if (error) throw error;
        
        // Update local data model
        const gameIndex = allGames.findIndex(g => g.id === game.id);
        if (gameIndex >= 0) {
            allGames[gameIndex].vote_count = currentCount + 1;
        }
        
    } catch (error) {
        console.error('Error voting:', error);
        // Revert on error
        countEl.textContent = currentCount;
        btn.classList.remove('voted');
        btn.querySelector('i').classList.replace('fas', 'far');
        
        // Remove from local storage
        const idx = votedGames.indexOf(game.id);
        if (idx > -1) {
            votedGames.splice(idx, 1);
            localStorage.setItem(STORAGE_KEY, JSON.stringify(votedGames));
        }
        
        // Re-attach handler
        btn.onclick = () => handleVote(game, btn, countEl);
        alert('Fehler beim Abstimmen. Bitte versuche es später erneut.');
    }
}

// Handle download
async function handleDownload(game) {
    try {
        const gd = game.game_data || {};
        const tracks = gd.tracks || [];
        
        // 1. Increment download count
        const currentCount = game.download_count || 0;
        supabaseClient
            .from('community_submissions')
            .update({ download_count: currentCount + 1 })
            .eq('id', game.id)
            .then(({ error }) => {
                if (error) console.error('Error incrementing download count:', error);
                else {
                    // Update local data model
                    const gameIndex = allGames.findIndex(g => g.id === game.id);
                    if (gameIndex >= 0) {
                        allGames[gameIndex].download_count = currentCount + 1;
                        applyFilters();
                    }
                }
            });
        
        // 2. Generate JSON structure matching game_info.json format
        const exportData = {
            game: gd.game,
            folderName: gd.folderName || sanitizeFilename(gd.game),
            linkIdentifier: gd.linkIdentifier || '',
            cardPurchaseUrl: gd.cardPurchaseUrl || '',
            hasPhysicalCards: gd.hasPhysicalCards || false,
            isCustom: true,
            tracks: tracks.map((t, index) => ({
                trackNr: t.trackNr || (index + 1).toString(),
                song: t.song,
                artist: t.artist,
                releaseDate: t.releaseDate || '',
                releaseYear: t.releaseYear || t.year || 0
            }))
        };
        
        // 3. Create blob and download
        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'game_info.json';
        document.body.appendChild(a);
        a.click();
        
        // Cleanup
        setTimeout(() => {
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }, 100);
        
    } catch (error) {
        console.error('Error downloading:', error);
        alert('Fehler beim Herunterladen.');
    }
}

// Helper: Sanitize filename
function sanitizeFilename(name) {
    return name.replace(/[^a-z0-9]/gi, '_').toLowerCase();
}

// Helper: Debounce function
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// Helper: Escape HTML
function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}
