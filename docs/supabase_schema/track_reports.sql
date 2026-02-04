-- Track Reports Table for SortaSong
-- Run this in your Supabase SQL Editor

BEGIN;

CREATE TABLE IF NOT EXISTS public.track_reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    track_id INTEGER NOT NULL REFERENCES public.tracks(id),
    -- User suggested corrections
    suggested_song TEXT NOT NULL,
    suggested_artist TEXT NOT NULL,
    suggested_release_date TEXT,
    suggested_release_year INTEGER,
    -- Metadata
    user_comment TEXT,
    reporter_id TEXT NOT NULL,  -- anonymous device identifier
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    approved BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_track_reports_reviewed ON public.track_reports(reviewed);
CREATE INDEX IF NOT EXISTS idx_track_reports_track ON public.track_reports(track_id);
CREATE INDEX IF NOT EXISTS idx_track_reports_reporter ON public.track_reports(reporter_id);

-- Enable Row Level Security
ALTER TABLE public.track_reports ENABLE ROW LEVEL SECURITY;

-- No anonymous insert policy - inserts only via RPC function (SECURITY DEFINER)

-- Only authenticated users can read reports (for admin review)
CREATE POLICY "Allow authenticated select" ON public.track_reports
    FOR SELECT
    TO authenticated
    USING (true);

-- Only authenticated users can update reports (mark as reviewed/approved)
CREATE POLICY "Allow authenticated updates" ON public.track_reports
    FOR UPDATE
    TO authenticated
    USING (true);

COMMIT;

-- Function to check for duplicate reports and insert if not exists
CREATE OR REPLACE FUNCTION public.submit_track_report(
    p_track_id INTEGER,
    p_suggested_song TEXT,
    p_suggested_artist TEXT,
    p_suggested_release_date TEXT,
    p_suggested_release_year INTEGER,
    p_user_comment TEXT,
    p_reporter_id TEXT
) RETURNS JSON AS $$
DECLARE
    existing_id BIGINT;
    new_id BIGINT;
BEGIN
    -- Check if same suggestion already exists (from any user)
    SELECT id INTO existing_id
    FROM public.track_reports
    WHERE track_id = p_track_id
      AND suggested_song = p_suggested_song
      AND suggested_artist = p_suggested_artist
      AND COALESCE(suggested_release_year, 0) = COALESCE(p_suggested_release_year, 0)
    LIMIT 1;
    
    IF existing_id IS NOT NULL THEN
        RETURN json_build_object('success', false, 'reason', 'duplicate', 'existing_id', existing_id);
    END IF;
    
    -- Insert new report
    INSERT INTO public.track_reports (
        track_id,
        suggested_song, suggested_artist, suggested_release_date, suggested_release_year,
        user_comment, reporter_id
    ) VALUES (
        p_track_id,
        p_suggested_song, p_suggested_artist, p_suggested_release_date, p_suggested_release_year,
        p_user_comment, p_reporter_id
    ) RETURNING id INTO new_id;
    
    RETURN json_build_object('success', true, 'id', new_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
