/**
 * SortaSong Admin - Supabase Operations
 * Uses official Supabase JS client for authentication
 */

// Supabase configuration
const SUPABASE_URL = 'https://hjzhojjnjnawwnwhzgwq.supabase.co';
const SUPABASE_ANON_KEY = 'sb_publishable_LATVvS_FmS-sLW_i1T7u1A_Bi9bWah7';

// Will be initialized after library loads
let supabaseClient = null;

class SupabaseAdmin {
    constructor() {
        this.client = null;
        this.user = null;
    }

    /**
     * Initialize the Supabase client
     */
    async init() {
        if (typeof supabase === 'undefined') {
            throw new Error('Supabase library not loaded');
        }
        
        this.client = supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
        supabaseClient = this.client;
        
        // Check for existing session
        const { data: { session } } = await this.client.auth.getSession();
        if (session) {
            this.user = session.user;
            return true;
        }
        return false;
    }

    /**
     * Login with email and password
     */
    async login(email, password) {
        const { data, error } = await this.client.auth.signInWithPassword({
            email,
            password
        });

        if (error) {
            throw new Error(error.message);
        }

        this.user = data.user;
        return this.user;
    }

    /**
     * Logout
     */
    async logout() {
        await this.client.auth.signOut();
        this.user = null;
    }

    /**
     * Check if logged in
     */
    isLoggedIn() {
        return !!this.user;
    }

    /**
     * Get current user
     */
    getUser() {
        return this.user;
    }

    /**
     * Make a query to Supabase
     */
    from(table) {
        return this.client.from(table);
    }

    /**
     * Get all track IDs that have pending reports
     */
    async getTracksWithReports(pendingOnly = true) {
        let query = this.client
            .from('track_reports')
            .select('track_id, reviewed, approved');
        
        if (pendingOnly) {
            // pending = not reviewed and not approved
            query = query.eq('reviewed', false).neq('approved', true);
        }
        
        const { data, error } = await query;
        
        if (error) throw new Error(error.message);
        
        // Group by track_id and count
        const trackMap = new Map();
        for (const report of data) {
            const existing = trackMap.get(report.track_id) || { total: 0, pending: 0 };
            existing.total++;
            if (!report.reviewed && !report.approved) {
                existing.pending++;
            }
            trackMap.set(report.track_id, existing);
        }
        
        return trackMap;
    }

    /**
     * Get track details by IDs
     */
    async getTracksByIds(trackIds) {
        if (trackIds.length === 0) return [];
        
        const { data, error } = await this.client
            .from('tracks')
            .select('*')
            .in('id', trackIds)
            .order('id');
        
        if (error) throw new Error(error.message);
        return data;
    }

    /**
     * Get a single track by ID
     */
    async getTrack(trackId) {
        const { data, error } = await this.client
            .from('tracks')
            .select('*')
            .eq('id', trackId)
            .single();
        
        if (error) throw new Error(error.message);
        return data;
    }

    /**
     * Get all reports for a track
     */
    async getReportsForTrack(trackId) {
        const { data, error } = await this.client
            .from('track_reports')
            .select('*')
            .eq('track_id', trackId)
            .order('submitted_at', { ascending: false });
        
        if (error) throw new Error(error.message);
        return data;
    }

    /**
     * Update a track
     */
    async updateTrack(trackId, trackData) {
        const { error } = await this.client
            .from('tracks')
            .update({
                artist: trackData.artist,
                song: trackData.title,
                release_date: trackData.releaseDate,
                release_year: parseInt(trackData.year) || null
            })
            .eq('id', trackId);

        if (error) throw new Error(error.message);
    }

    /**
     * Mark reports as done
     */
    async markReportsAsDone(trackId) {
        // Mark reports as reviewed and approved=false
        const { error } = await this.client
            .from('track_reports')
            .update({ reviewed: true })
            .eq('track_id', trackId)
            .eq('reviewed', false);

        if (error) throw new Error(error.message);
    }
}

// Global instance
const supabaseAdmin = new SupabaseAdmin();
