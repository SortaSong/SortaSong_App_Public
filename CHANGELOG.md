# Changelog

All notable changes to SortaSong will be documented in this file.

## [1.2.1] - 2026-02-12

### Fixed
- Fixed naming inconsistencies in drawable resources (SortaSound → SortaSong)
- Corrected resource file naming convention across the project

## [1.2.0] - 2026-02-04

### Added

#### 🌐 Community Sharing
- **Share your custom games with the community!** Submit your game packs for others to download
- **Browse Community Games** - Discover and download games created by other players
- **Vote for games** you enjoy to help others find the best content
- **My Submissions** - Track the status of your submitted games (Pending → Approved)
- Games are reviewed before being published to ensure quality

#### 🔍 Track Search
- **Search online tracks** when adding songs to custom games
- Access the full SortaSong track database from within the app
- Search by artist or song title

#### 🖥️ Web Features
- **Community Playlists page** - Browse and vote for community games online
- **Check submission status** - Track your submissions from any device
- **Download App button** - Easy access to get the app from the website

### Improved
- Streamlined custom game editor workflow
- Auto-save before sharing games
- Better error handling and user feedback

### Technical
- New activities: BrowseCommunityGamesActivity, MySubmissionsActivity
- CommunityGameService and SubmissionService for Supabase integration
- Password-based submission authentication (no account required)

---

## [1.1.0] - 2026-01-31

### Added

#### 🎮 Custom Games
- **Create your own game packs!** You can now create custom games with your own music files
- **Manage Custom Games** screen accessible from main menu
- **In-App Editor** for creating and editing custom games on your phone
- **Web Editor** for more comfortable editing on PC: [https://sortasong.github.io/SortaSong_App_Public/editor/](https://sortasong.github.io/SortaSong_App_Public/editor/)
- Custom games are marked with a ⭐ star in the game selection
- Auto-scan audio files for metadata (Artist, Title, Year)
- Supports MP3, M4A, FLAC, OGG, WAV, AAC files

#### 🐛 Issue Reporting
- **Report incorrect track data** directly from the result popup
- Submit corrections for Artist, Title, Release Date, or Year
- Duplicate detection prevents spam submissions
- Reports are reviewed before being applied

### Technical
- Custom games stored locally in `game_info.json` per folder
- Easy transfer between phone and PC - just copy the folder
- Custom games use negative track IDs internally to distinguish from official tracks

---

## [1.0.0] - Initial Release

### Features
- Play Hitster-style music quiz with your own files
- Two play modes: With Cards (QR scanning) and Without Cards (digital deck)
- Support for multiple official game packs
- Multi-player support
- Track verification system
- Supabase backend for game data
