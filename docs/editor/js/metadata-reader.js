/**
 * SortaSong Custom Game Editor - Audio Metadata Reader
 * Uses jsmediatags library for reading ID3 tags from audio files.
 * Falls back to filename parsing if metadata not available.
 */

// Load jsmediatags from CDN
const JSMEDIATAGS_URL = 'https://cdnjs.cloudflare.com/ajax/libs/jsmediatags/3.9.5/jsmediatags.min.js';

let jsmediatagsLoaded = false;

async function loadJsmediatags() {
    if (jsmediatagsLoaded) return;
    
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = JSMEDIATAGS_URL;
        script.onload = () => {
            jsmediatagsLoaded = true;
            resolve();
        };
        script.onerror = () => reject(new Error('Failed to load jsmediatags'));
        document.head.appendChild(script);
    });
}

/**
 * Supported audio file extensions
 */
const AUDIO_EXTENSIONS = ['.mp3', '.m4a', '.flac', '.ogg', '.opus', '.wav', '.aac'];

/**
 * Check if a file is an audio file
 */
function isAudioFile(filename) {
    const ext = filename.toLowerCase().substring(filename.lastIndexOf('.'));
    return AUDIO_EXTENSIONS.includes(ext);
}

/**
 * Parse filename to extract artist and title.
 * Supports common patterns:
 * - "Artist - Title.mp3"
 * - "Artist - Album - Title.mp3"  
 * - "01 - Artist - Title.mp3"
 * - "01. Artist - Title.mp3"
 */
function parseFilename(filename) {
    // Remove extension
    let name = filename.substring(0, filename.lastIndexOf('.'));
    
    // Remove track numbers like "01 - ", "01. ", "1 "
    name = name.replace(/^\d+[\s.\-_]+/, '');
    
    // Split by common separators
    const separators = [' - ', ' – ', ' — ', '_-_'];
    let parts = null;
    
    for (const sep of separators) {
        if (name.includes(sep)) {
            parts = name.split(sep);
            break;
        }
    }
    
    if (parts && parts.length >= 2) {
        // If 3+ parts, likely "Artist - Album - Title" or "Track - Artist - Title"
        if (parts.length >= 3) {
            // Check if first part looks like a track number
            if (/^\d+$/.test(parts[0].trim())) {
                return {
                    artist: parts[1].trim(),
                    title: parts.slice(2).join(' - ').trim()
                };
            }
            // Assume "Artist - Album - Title"
            return {
                artist: parts[0].trim(),
                title: parts[parts.length - 1].trim()
            };
        }
        return {
            artist: parts[0].trim(),
            title: parts[1].trim()
        };
    }
    
    // No separator found, use filename as title
    return {
        artist: '',
        title: name.trim()
    };
}

/**
 * Parse date string to extract year
 */
function extractYear(dateStr) {
    if (!dateStr) return null;
    
    // Already a year
    if (/^\d{4}$/.test(dateStr)) {
        return parseInt(dateStr);
    }
    
    // ISO date: 2000-01-15
    let match = dateStr.match(/(\d{4})-\d{2}-\d{2}/);
    if (match) return parseInt(match[1]);
    
    // German date: 15.01.2000
    match = dateStr.match(/\d{2}\.\d{2}\.(\d{4})/);
    if (match) return parseInt(match[1]);
    
    // US date: 01/15/2000
    match = dateStr.match(/\d{2}\/\d{2}\/(\d{4})/);
    if (match) return parseInt(match[1]);
    
    // Any 4-digit year
    match = dateStr.match(/(\d{4})/);
    if (match) return parseInt(match[1]);
    
    return null;
}

/**
 * Format date for display (DD.MM.YYYY)
 */
function formatDate(year, month, day) {
    if (!year) return '';
    if (!month && !day) return year.toString();
    
    const d = day ? day.toString().padStart(2, '0') : '01';
    const m = month ? month.toString().padStart(2, '0') : '01';
    return `${d}.${m}.${year}`;
}

/**
 * Read metadata from an audio file using jsmediatags
 */
async function readMetadata(file) {
    await loadJsmediatags();
    
    return new Promise((resolve) => {
        const fallback = parseFilename(file.name);
        
        jsmediatags.read(file, {
            onSuccess: (tag) => {
                const tags = tag.tags;
                
                // Extract basic info
                const artist = tags.artist || fallback.artist;
                const title = tags.title || fallback.title;
                
                // Extract year/date
                let releaseYear = null;
                let releaseDate = '';
                
                // Try TDRC (recording date) first, then TYER (year)
                if (tags.TDRC?.data) {
                    releaseDate = tags.TDRC.data;
                    releaseYear = extractYear(releaseDate);
                } else if (tags.year) {
                    releaseYear = parseInt(tags.year);
                    releaseDate = tags.year;
                } else if (tags.TYER?.data) {
                    releaseYear = parseInt(tags.TYER.data);
                    releaseDate = tags.TYER.data;
                }
                
                resolve({
                    artist: artist || '',
                    title: title || '',
                    releaseDate: releaseDate || '',
                    releaseYear: releaseYear,
                    originalFileName: file.name
                });
            },
            onError: (error) => {
                console.warn('Failed to read metadata:', file.name, error);
                resolve({
                    artist: fallback.artist,
                    title: fallback.title,
                    releaseDate: '',
                    releaseYear: null,
                    originalFileName: file.name
                });
            }
        });
    });
}

/**
 * Scan an array of File objects for audio files and read their metadata
 * (Fallback for browsers without File System Access API)
 * @param {File[]} files - Array of File objects from input[webkitdirectory]
 * @param {Function} onProgress - Progress callback (current, total)
 * @returns {Promise<Array>} Array of track objects
 */
async function scanFiles(files, onProgress) {
    const audioFiles = files.filter(f => isAudioFile(f.name));
    
    if (audioFiles.length === 0) {
        return [];
    }
    
    // Sort by filename
    audioFiles.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
    
    // Read metadata for each file
    const tracks = [];
    for (let i = 0; i < audioFiles.length; i++) {
        if (onProgress) {
            onProgress(i + 1, audioFiles.length);
        }
        
        const file = audioFiles[i];
        const metadata = await readMetadata(file);
        
        tracks.push({
            trackNr: (i + 1).toString(),
            artist: metadata.artist,
            song: metadata.title,  // Convert "title" to "song"
            releaseDate: metadata.releaseDate,
            releaseYear: metadata.releaseYear,
            originalFileName: metadata.originalFileName
        });
    }
    
    return tracks;
}

/**
 * Scan a folder/directory for audio files and read their metadata
 * @param {FileSystemDirectoryHandle} dirHandle - Directory handle from File System Access API
 * @param {Function} onProgress - Progress callback (current, total)
 * @returns {Promise<Array>} Array of track objects
 */
async function scanFolder(dirHandle, onProgress) {
    const audioFiles = [];
    
    // Collect all audio files
    for await (const entry of dirHandle.values()) {
        if (entry.kind === 'file' && isAudioFile(entry.name)) {
            audioFiles.push(entry);
        }
    }
    
    if (audioFiles.length === 0) {
        return [];
    }
    
    // Sort by filename
    audioFiles.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
    
    // Read metadata for each file
    const tracks = [];
    for (let i = 0; i < audioFiles.length; i++) {
        if (onProgress) {
            onProgress(i + 1, audioFiles.length);
        }
        
        const fileHandle = audioFiles[i];
        const file = await fileHandle.getFile();
        const metadata = await readMetadata(file);
        
        tracks.push({
            trackNr: (i + 1).toString(),
            artist: metadata.artist,
            song: metadata.title,  // Convert "title" to "song"
            releaseDate: metadata.releaseDate,
            releaseYear: metadata.releaseYear,
            originalFileName: metadata.originalFileName
        });
    }
    
    return tracks;
}

/**
 * Read game_info.json from a folder
 * @param {FileSystemDirectoryHandle} dirHandle
 * @returns {Promise<Object|null>} Game info object or null if not found
 */
async function loadGameInfo(dirHandle) {
    try {
        const fileHandle = await dirHandle.getFileHandle('game_info.json');
        const file = await fileHandle.getFile();
        const content = await file.text();
        return JSON.parse(content);
    } catch (e) {
        return null;
    }
}

/**
 * Save game_info.json to a folder
 * @param {FileSystemDirectoryHandle} dirHandle
 * @param {Object} gameInfo
 */
async function saveGameInfo(dirHandle, gameInfo) {
    const fileHandle = await dirHandle.getFileHandle('game_info.json', { create: true });
    const writable = await fileHandle.createWritable();
    await writable.write(JSON.stringify(gameInfo, null, 2));
    await writable.close();
}

/**
 * Suggest a filename based on artist and title
 */
function suggestFilename(artist, title, originalFileName) {
    if (!artist || !title) return null;
    
    // Get extension from original filename
    const ext = originalFileName.substring(originalFileName.lastIndexOf('.'));
    const suggested = `${artist} - ${title}${ext}`;
    
    // Only suggest if different from current
    if (suggested === originalFileName) return null;
    
    return suggested;
}

/**
 * Rename a file in a folder
 * @param {FileSystemDirectoryHandle} dirHandle
 * @param {string} oldName
 * @param {string} newName
 */
async function renameFile(dirHandle, oldName, newName) {
    try {
        const fileHandle = await dirHandle.getFileHandle(oldName);
        const file = await fileHandle.getFile();
        
        // Create new file
        const newFileHandle = await dirHandle.getFileHandle(newName, { create: true });
        const writable = await newFileHandle.createWritable();
        await writable.write(file);
        await writable.close();
        
        // Delete old file
        await dirHandle.removeEntry(oldName);
        
        return true;
    } catch (e) {
        console.error('Failed to rename file:', e);
        return false;
    }
}

// Export for use in editor.js
window.MetadataReader = {
    isAudioFile,
    parseFilename,
    extractYear,
    formatDate,
    readMetadata,
    scanFiles,
    scanFolder,
    loadGameInfo,
    saveGameInfo,
    suggestFilename,
    renameFile
};
