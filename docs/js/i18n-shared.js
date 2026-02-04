/**
 * SortaSong Shared Internationalization
 * For landing page, community, and check-status pages
 */

const sharedTranslations = {
    de: {
        // Navigation
        nav_editor: 'Editor',
        nav_community: 'Community',
        nav_status: 'Status',
        nav_reports: 'Reports',
        nav_submissions: 'Submissions',
        get_app_link: '📱 App holen',
        
        // Landing Page
        landing_title: 'SortaSong Custom Playlists',
        landing_subtitle: 'Erstelle eigene Musik-Quiz Playlists, teile sie mit der Community oder spiele Playlists von anderen.',
        
        card_editor_title: 'Playlist Editor',
        card_editor_desc: 'Erstelle und bearbeite eigene Playlists auf deinem PC. Lade Musik-Dateien, bearbeite Metadaten und exportiere für die App.',
        card_editor_link: '→ Editor öffnen',
        
        card_community_title: 'Community Playlists',
        card_community_desc: 'Entdecke Playlists von anderen Spielern. Vote für deine Favoriten und lade sie herunter.',
        card_community_link: '→ Community durchstöbern',
        
        card_status_title: 'Submission Status',
        card_status_desc: 'Prüfe den Status deiner eingereichten Playlist oder bearbeite sie.',
        card_status_link: '→ Status prüfen',
        
        footer_text: 'SortaSong - Ein Musik-Sortier-Spiel',
        
        // Community Page
        community_title: 'Community Playlists',
        community_subtitle: 'Entdecke von der Community erstellte Spiele für SortaSong',
        search_placeholder: 'Suche nach Spielen...',
        sort_label: 'Sortieren nach:',
        sort_newest: 'Neueste zuerst',
        sort_votes: 'Meiste Stimmen',
        sort_downloads: 'Meiste Downloads',
        loading_games: 'Lade Community-Spiele...',
        error_loading: 'Fehler beim Laden der Daten.',
        retry: 'Erneut versuchen',
        no_games: 'Noch keine Community-Spiele verfügbar.',
        create_first: 'Erstelle das erste!',
        tracks_count: 'Tracks',
        votes_count: 'Stimmen',
        downloads_count: 'Downloads',
        btn_tracklist: 'Trackliste',
        btn_download: 'game_info.json',
        tracks_title: 'Enthaltene Songs:',
        
        // Check Status Page
        status_title: 'Submission Status prüfen',
        status_subtitle: 'Geben Sie Ihre Submission-ID und Ihr Passwort ein, um den Status Ihrer eingereichten Playlist zu überprüfen.',
        login_title: 'Anmelden',
        submission_id: 'Submission-ID',
        submission_id_placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        submission_id_hint: 'UUID die Sie nach der Einreichung erhalten haben',
        password: 'Passwort',
        password_placeholder: '************',
        password_hint: '12 Zeichen Passwort',
        check_status: 'Status prüfen',
        
        help_title: 'Hilfe',
        help_q1: 'Wo finde ich meinen Token und mein Passwort?',
        help_a1: 'Diese wurden Ihnen direkt nach dem Einreichen Ihrer Playlist angezeigt. Sie sollten diese notiert oder heruntergeladen haben.',
        help_q2: 'Ich habe meine Zugangsdaten verloren',
        help_a2: 'Leider können wir Token und Passwörter aus Sicherheitsgründen nicht wiederherstellen. Bitte reichen Sie Ihre Playlist erneut ein.',
        help_q3: 'Wie lange dauert die Überprüfung?',
        help_a3: 'Die Überprüfung dauert normalerweise 1-7 Tage, abhängig von der Anzahl der Submissions.',
        help_q4: 'Was sind die Submission-Stati?',
        status_pending: 'Wartet auf Überprüfung',
        status_in_progress: 'Wird gerade überprüft',
        status_approved: 'Genehmigt, verfügbar für Voting',
        status_published: 'Offiziell veröffentlicht in der App',
        status_rejected: 'Abgelehnt mit Begründung'
    },
    en: {
        // Navigation
        nav_editor: 'Editor',
        nav_community: 'Community',
        nav_status: 'Status',
        nav_reports: 'Reports',
        nav_submissions: 'Submissions',
        get_app_link: '📱 Get App',
        
        // Landing Page
        landing_title: 'SortaSong Custom Playlists',
        landing_subtitle: 'Create your own music quiz playlists, share them with the community, or play playlists from others.',
        
        card_editor_title: 'Playlist Editor',
        card_editor_desc: 'Create and edit your own playlists on your PC. Load music files, edit metadata, and export for the app.',
        card_editor_link: '→ Open Editor',
        
        card_community_title: 'Community Playlists',
        card_community_desc: 'Discover playlists from other players. Vote for your favorites and download them.',
        card_community_link: '→ Browse Community',
        
        card_status_title: 'Submission Status',
        card_status_desc: 'Check the status of your submitted playlist or edit it.',
        card_status_link: '→ Check Status',
        
        footer_text: 'SortaSong - A Music Sorting Game',
        
        // Community Page
        community_title: 'Community Playlists',
        community_subtitle: 'Discover games created by the community for SortaSong',
        search_placeholder: 'Search for games...',
        sort_label: 'Sort by:',
        sort_newest: 'Newest first',
        sort_votes: 'Most votes',
        sort_downloads: 'Most downloads',
        loading_games: 'Loading community games...',
        error_loading: 'Error loading data.',
        retry: 'Try again',
        no_games: 'No community games available yet.',
        create_first: 'Create the first one!',
        tracks_count: 'Tracks',
        votes_count: 'Votes',
        downloads_count: 'Downloads',
        btn_tracklist: 'Track List',
        btn_download: 'game_info.json',
        tracks_title: 'Included Songs:',
        
        // Check Status Page
        status_title: 'Check Submission Status',
        status_subtitle: 'Enter your Submission ID and password to check the status of your submitted playlist.',
        login_title: 'Login',
        submission_id: 'Submission ID',
        submission_id_placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        submission_id_hint: 'UUID you received after submission',
        password: 'Password',
        password_placeholder: '************',
        password_hint: '12 character password',
        check_status: 'Check Status',
        
        help_title: 'Help',
        help_q1: 'Where can I find my token and password?',
        help_a1: 'These were displayed to you immediately after submitting your playlist. You should have noted or downloaded them.',
        help_q2: 'I lost my credentials',
        help_a2: 'Unfortunately, we cannot recover tokens and passwords for security reasons. Please resubmit your playlist.',
        help_q3: 'How long does the review take?',
        help_a3: 'The review usually takes 1-7 days, depending on the number of submissions.',
        help_q4: 'What are the submission statuses?',
        status_pending: 'Waiting for review',
        status_in_progress: 'Currently being reviewed',
        status_approved: 'Approved, available for voting',
        status_published: 'Officially published in the app',
        status_rejected: 'Rejected with reason'
    }
};

// Current language
let currentLang = 'de';

// Detect browser language and set default
function detectLanguage() {
    const browserLang = navigator.language || navigator.userLanguage;
    if (browserLang && browserLang.startsWith('de')) {
        return 'de';
    }
    return 'en'; // Default to English for non-German browsers
}

// Get translation
function t(key) {
    return sharedTranslations[currentLang][key] || sharedTranslations['en'][key] || key;
}

// Apply translations to elements with data-i18n attribute
function applyTranslations() {
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        const translation = t(key);
        if (translation) {
            el.textContent = translation;
        }
    });
    
    // Handle placeholders
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
        const key = el.getAttribute('data-i18n-placeholder');
        const translation = t(key);
        if (translation) {
            el.placeholder = translation;
        }
    });
    
    // Handle titles
    document.querySelectorAll('[data-i18n-title]').forEach(el => {
        const key = el.getAttribute('data-i18n-title');
        const translation = t(key);
        if (translation) {
            el.title = translation;
        }
    });
}

// Set language and apply
function setLanguage(lang) {
    if (sharedTranslations[lang]) {
        currentLang = lang;
        localStorage.setItem('sortasong-lang', lang);
        applyTranslations();
        
        // Update lang buttons if they exist
        document.querySelectorAll('.lang-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.lang === lang);
        });
        
        // Update html lang attribute
        document.documentElement.lang = lang;
    }
}

// Initialize i18n
function initI18n() {
    // Load saved language or detect from browser
    const savedLang = localStorage.getItem('sortasong-lang');
    currentLang = savedLang || detectLanguage();
    
    // Apply translations
    applyTranslations();
    
    // Set up language switcher buttons
    document.querySelectorAll('.lang-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.lang === currentLang);
        btn.addEventListener('click', () => setLanguage(btn.dataset.lang));
    });
    
    // Update html lang attribute
    document.documentElement.lang = currentLang;
}

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initI18n);
} else {
    initI18n();
}
