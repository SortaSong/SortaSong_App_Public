/**
 * SortaSong Custom Game Editor - Supabase Track Browser
 * Fetches and displays tracks from the official SortaSong database
 */

const SUPABASE_URL = 'https://hjzhojjnjnawwnwhzgwq.supabase.co';
const SUPABASE_ANON_KEY = 'sb_publishable_LATVvS_FmS-sLW_i1T7u1A_Bi9bWah7';

// State
let allGames = [];
let currentTracks = [];
let currentOffset = 0;
let currentSearch = '';
let currentGameFilter = '';
let hasMore = true;
const PAGE_SIZE = 50;
const MIN_SEARCH_LENGTH = 3;

/**
 * Fetch games list from Supabase
 */
async function fetchGames() {
    try {
        const response = await fetch(
            `${SUPABASE_URL}/rest/v1/games?select=id,game,folder_name&order=game.asc&apikey=${SUPABASE_ANON_KEY}`,
            {
                headers: {
                    'Authorization': `Bearer ${SUPABASE_ANON_KEY}`
                }
            }
        );
        
        if (!response.ok) throw new Error('Failed to fetch games');
        
        allGames = await response.json();
        return allGames;
    } catch (e) {
        console.error('Error fetching games:', e);
        return [];
    }
}

/**
 * Fetch tracks from Supabase with optional filtering
 * Note: tracks table has id, song, artist, release_date, release_year
 */
async function fetchTracks(search = '', gameFilter = '', offset = 0) {
    // Don't fetch if search is too short (but empty is OK for initial load when filtered by game)
    if (search && search.length < MIN_SEARCH_LENGTH) {
        return { tracks: [], hasMore: false, needsMoreChars: true };
    }
    
    try {
        let url = `${SUPABASE_URL}/rest/v1/tracks?select=id,song,artist,release_date&order=artist.asc,song.asc&limit=${PAGE_SIZE}&offset=${offset}&apikey=${SUPABASE_ANON_KEY}`;
        
        // Add search filter (OR across artist and song)
        if (search) {
            const searchPattern = `*${search}*`;
            const orFilter = `(artist.ilike.${searchPattern},song.ilike.${searchPattern})`;
            url += `&or=${encodeURIComponent(orFilter)}`;
        }
        
        // Note: Can't filter by game/folder directly - tracks table doesn't have folder_name
        // Game filter would require a join with game_tracks table which isn't straightforward via REST
        
        const response = await fetch(url, {
            headers: {
                'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
                'Prefer': 'count=exact'
            }
        });
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Supabase error:', response.status, errorText);
            throw new Error('Failed to fetch tracks');
        }
        
        const tracks = await response.json();
        const contentRange = response.headers.get('content-range');
        const totalCount = contentRange ? parseInt(contentRange.split('/')[1] || '0') : tracks.length;
        
        return {
            tracks,
            hasMore: offset + tracks.length < totalCount
        };
    } catch (e) {
        console.error('Error fetching tracks:', e);
        return { tracks: [], hasMore: false };
    }
}

/**
 * Extract year from release date string
 */
function extractYearFromDate(dateStr) {
    if (!dateStr) return null;
    const match = dateStr.match(/(\d{4})/);
    return match ? parseInt(match[1]) : null;
}

/**
 * Convert Supabase track to game_info track format
 */
function convertToGameTrack(track) {
    return {
        trackNr: '',
        song: track.song || '',
        artist: track.artist || '',
        releaseDate: track.release_date || '',
        releaseYear: extractYearFromDate(track.release_date),
        originalFileName: `${track.artist} - ${track.song}.mp3`,
        sourceTrackId: track.id
    };
}

/**
 * Initialize the track picker UI
 */
function initTrackPicker(onTrackSelected) {
    const modal = document.getElementById('add-track-modal');
    const searchInput = document.getElementById('track-search-input');
    const gameFilterEl = document.getElementById('game-filter');
    const trackList = document.getElementById('track-list');
    const loadingState = document.getElementById('tracks-loading');
    const loadMoreContainer = document.getElementById('load-more');
    const btnLoadMore = document.getElementById('btn-load-more');
    const btnAddEmpty = document.getElementById('btn-add-empty-track');
    
    let searchTimeout = null;
    
    // Close modal handlers
    modal.querySelector('.close').addEventListener('click', () => {
        modal.classList.remove('active');
    });
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('active');
        }
    });
    
    // Add empty track button
    btnAddEmpty.addEventListener('click', () => {
        modal.classList.remove('active');
        onTrackSelected(null);
    });
    
    // Search input with debounce (500ms)
    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentSearch = searchInput.value.trim();
            currentOffset = 0;
            loadTracks(true);
        }, 500);
    });
    
    // Load more button
    btnLoadMore.addEventListener('click', () => {
        currentOffset += PAGE_SIZE;
        loadTracks(false);
    });
    
    // Load tracks function
    async function loadTracks(reset = false) {
        if (reset) {
            trackList.innerHTML = '';
            currentOffset = 0;
        }
        
        // Show hint if search is too short
        if (currentSearch && currentSearch.length < MIN_SEARCH_LENGTH) {
            loadingState.style.display = 'none';
            loadMoreContainer.style.display = 'none';
            trackList.innerHTML = `<div class="search-hint">${t('search_hint').replace('{n}', MIN_SEARCH_LENGTH)}</div>`;
            return;
        }
        
        // Don't auto-load all tracks - require a search
        if (!currentSearch) {
            loadingState.style.display = 'none';
            loadMoreContainer.style.display = 'none';
            trackList.innerHTML = `<div class="search-hint">${t('search_prompt')}</div>`;
            return;
        }
        
        loadingState.style.display = 'block';
        loadMoreContainer.style.display = 'none';
        
        const result = await fetchTracks(currentSearch, '', currentOffset);
        
        loadingState.style.display = 'none';
        hasMore = result.hasMore;
        
        if (result.tracks.length === 0 && reset) {
            trackList.innerHTML = `<div class="no-results">${t('no_tracks_found')}</div>`;
            return;
        }
        
        result.tracks.forEach(track => {
            const item = document.createElement('div');
            item.className = 'track-item';
            item.innerHTML = `
                <div class="track-info">
                    <span class="track-artist">${escapeHtml(track.artist || '')}</span>
                    <span class="track-separator">–</span>
                    <span class="track-song">${escapeHtml(track.song || '')}</span>
                </div>
                <div class="track-meta">
                    <span class="track-year">${extractYearFromDate(track.release_date) || ''}</span>
                </div>
            `;
            
            item.addEventListener('click', () => {
                modal.classList.remove('active');
                onTrackSelected(convertToGameTrack(track));
            });
            
            trackList.appendChild(item);
        });
        
        loadMoreContainer.style.display = hasMore ? 'block' : 'none';
    }
    
    // Show modal function
    return async function showTrackPicker() {
        modal.classList.add('active');
        searchInput.value = '';
        currentSearch = '';
        currentOffset = 0;
        trackList.innerHTML = `<div class="search-hint">${t('search_prompt')}</div>`;
        loadingState.style.display = 'none';
        loadMoreContainer.style.display = 'none';
        searchInput.focus();
    };
}

// Helper
function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str || '';
    return div.innerHTML;
}

// Export
window.SupabaseTracks = {
    fetchGames,
    fetchTracks,
    convertToGameTrack,
    initTrackPicker,
    getApiKey: () => SUPABASE_ANON_KEY
};
