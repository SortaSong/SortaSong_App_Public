/**
 * SortaSong Custom Game Editor - Internationalization
 */

const translations = {
    de: {
        // Workflow
        workflow_title: 'Wie möchtest du beginnen?',
        workflow_scratch: 'Komplett neu starten',
        workflow_scratch_desc: 'Erstelle ein neues Spiel von Grund auf',
        workflow_folder: 'Ordner mit Songs auswählen',
        workflow_folder_desc: 'Metadaten aus vorhandenen Musikdateien lesen',
        workflow_existing: 'Bestehendes Spiel bearbeiten',
        workflow_existing_desc: 'game_info.json aus einem Ordner laden',
        browser_warning: '⚠️ Dein Browser unterstützt nicht alle Funktionen. Bitte verwende Chrome, Edge oder einen anderen Chromium-basierten Browser für die beste Erfahrung.',
        
        // Metadata form
        metadata_title: 'Spiel-Informationen',
        game_name: 'Spielname *',
        folder_name: 'Ordnername *',
        folder_name_hint: 'Keine Leerzeichen oder Sonderzeichen',
        has_cards: 'Hast du physische Spielkarten?',
        yes: 'Ja',
        no: 'Nein',
        link_identifier: 'Link-Kennung (QR-Code)',
        card_url: 'Link zum Karten-Kauf',
        back: 'Zurück',
        continue: 'Weiter',
        
        // Editor
        editor_title: 'Tracks bearbeiten',
        scan_folder: '📁 Ordner scannen',
        add_track: '➕ Track hinzufügen',
        col_artist: 'Künstler',
        col_title: 'Titel',
        col_date: 'Erscheinungsdatum',
        col_year: 'Jahr',
        col_filename: 'Dateiname',
        col_actions: 'Aktionen',
        no_tracks: 'Noch keine Tracks. Füge Tracks hinzu oder scanne einen Ordner.',
        save: '💾 Speichern',
        swap: '⇄',
        delete: '🗑️',
        rename_suggestion: 'Vorgeschlagener Dateiname:',
        rename_apply: 'Umbenennen',
        
        // Guide
        guide_title: 'Anleitung: Spiel auf Handy übertragen',
        guide_step1_title: 'Schritt 1: Ordner vorbereiten',
        guide_step1: 'Stelle sicher, dass dein Spielordner die game_info.json und alle Musikdateien enthält.',
        guide_step2_title: 'Schritt 2: Auf Handy kopieren',
        guide_step2: 'Verbinde dein Handy per USB-Kabel und kopiere den gesamten Spielordner in deinen SortaSong-Musikordner.',
        guide_step3_title: 'Schritt 3: App aktualisieren',
        guide_step3: 'Öffne SortaSong und gehe zu Einstellungen → "Ordner neu scannen". Dein Spiel erscheint mit einem ⭐.',
        
        // Messages
        msg_folder_not_supported: 'Dein Browser unterstützt den Ordnerzugriff nicht. Bitte verwende Chrome oder Edge.',
        msg_no_audio_files: 'Keine Audio-Dateien im Ordner gefunden.',
        msg_scanning: 'Scanne Ordner...',
        msg_reading_metadata: 'Lese Metadaten...',
        msg_saved: 'game_info.json wurde gespeichert!',
        msg_confirm_delete: 'Diesen Track wirklich löschen?',
        msg_invalid_form: 'Bitte fülle alle Pflichtfelder aus.',
        msg_no_tracks_to_save: 'Bitte füge mindestens einen Track hinzu.',
        
        // Track picker
        add_track_title: 'Track hinzufügen',
        add_empty_track: 'Neuen Track erstellen',
        or: 'oder',
        search_tracks: 'Suche nach Künstler oder Titel...',
        all_games: 'Alle Spiele',
        loading_tracks: 'Lade Tracks...',
        load_more: 'Mehr laden',
        no_tracks_found: 'Keine Tracks gefunden.',
        search_hint: 'Bitte mindestens {n} Zeichen eingeben.',
        search_prompt: 'Gib einen Suchbegriff ein, um Tracks zu finden.'
    },
    en: {
        // Workflow
        workflow_title: 'How would you like to start?',
        workflow_scratch: 'Start from scratch',
        workflow_scratch_desc: 'Create a new game from the ground up',
        workflow_folder: 'Select folder with songs',
        workflow_folder_desc: 'Read metadata from existing music files',
        workflow_existing: 'Edit existing game',
        workflow_existing_desc: 'Load game_info.json from a folder',
        browser_warning: '⚠️ Your browser does not support all features. Please use Chrome, Edge, or another Chromium-based browser for the best experience.',
        
        // Metadata form
        metadata_title: 'Game Information',
        game_name: 'Game Name *',
        folder_name: 'Folder Name *',
        folder_name_hint: 'No spaces or special characters',
        has_cards: 'Do you have physical playing cards?',
        yes: 'Yes',
        no: 'No',
        link_identifier: 'Link Identifier (QR Code)',
        card_url: 'Link to purchase cards',
        back: 'Back',
        continue: 'Continue',
        
        // Editor
        editor_title: 'Edit Tracks',
        scan_folder: '📁 Scan Folder',
        add_track: '➕ Add Track',
        col_artist: 'Artist',
        col_title: 'Title',
        col_date: 'Release Date',
        col_year: 'Year',
        col_filename: 'Filename',
        col_actions: 'Actions',
        no_tracks: 'No tracks yet. Add tracks or scan a folder.',
        save: '💾 Save',
        swap: '⇄',
        delete: '🗑️',
        rename_suggestion: 'Suggested filename:',
        rename_apply: 'Rename',
        
        // Guide
        guide_title: 'Guide: Transfer Game to Phone',
        guide_step1_title: 'Step 1: Prepare folder',
        guide_step1: 'Make sure your game folder contains the game_info.json and all music files.',
        guide_step2_title: 'Step 2: Copy to phone',
        guide_step2: 'Connect your phone via USB cable and copy the entire game folder to your SortaSong music folder.',
        guide_step3_title: 'Step 3: Update app',
        guide_step3: 'Open SortaSong and go to Settings → "Rescan folder". Your game will appear with a ⭐.',
        
        // Messages
        msg_folder_not_supported: 'Your browser does not support folder access. Please use Chrome or Edge.',
        msg_no_audio_files: 'No audio files found in folder.',
        msg_scanning: 'Scanning folder...',
        msg_reading_metadata: 'Reading metadata...',
        msg_saved: 'game_info.json has been saved!',
        msg_confirm_delete: 'Really delete this track?',
        msg_invalid_form: 'Please fill in all required fields.',
        msg_no_tracks_to_save: 'Please add at least one track.',
        
        // Track picker
        add_track_title: 'Add Track',
        add_empty_track: 'Create new track',
        or: 'or',
        search_tracks: 'Search by artist or title...',
        all_games: 'All games',
        loading_tracks: 'Loading tracks...',
        load_more: 'Load more',
        no_tracks_found: 'No tracks found.',
        search_hint: 'Please enter at least {n} characters.',
        search_prompt: 'Enter a search term to find tracks.'
    }
};

let currentLang = 'de';

function setLanguage(lang) {
    currentLang = lang;
    localStorage.setItem('sortasong-editor-lang', lang);
    
    // Update all elements with data-i18n
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        if (translations[lang][key]) {
            el.textContent = translations[lang][key];
        }
    });
    
    // Update placeholders
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
        const key = el.getAttribute('data-i18n-placeholder');
        if (translations[lang][key]) {
            el.placeholder = translations[lang][key];
        }
    });
    
    // Update active button
    document.querySelectorAll('.lang-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.lang === lang);
    });
}

function t(key) {
    return translations[currentLang][key] || key;
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    // Load saved language or detect from browser
    const savedLang = localStorage.getItem('sortasong-editor-lang');
    const browserLang = navigator.language.startsWith('de') ? 'de' : 'en';
    setLanguage(savedLang || browserLang);
    
    // Language switcher
    document.querySelectorAll('.lang-btn').forEach(btn => {
        btn.addEventListener('click', () => setLanguage(btn.dataset.lang));
    });
});
