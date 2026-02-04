-- Community Submissions Table
-- Used for users to submit their custom games for review

-- Create the table
CREATE TABLE IF NOT EXISTS community_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Game content (contains gameName, folderName, linkIdentifier, linkUrl, tracks[])
    game_data JSONB NOT NULL,
    
    -- Submission metadata
    description TEXT,
    submitted_by_name TEXT,
    submission_password_hash TEXT NOT NULL,
    
    -- Status tracking: pending -> in_progress -> approved/rejected
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'in_progress', 'approved', 'rejected')),
    rejection_reason TEXT,
    
    -- Admin fields
    review_notes TEXT,
    admin_notes TEXT,
    reviewed_by TEXT,
    reviewed_at TIMESTAMPTZ,
    
    -- After approval, link to official game
    official_game_id INTEGER,
    
    -- Community voting/downloads (for approved games)
    vote_count INTEGER DEFAULT 0,
    download_count INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_community_submissions_status ON community_submissions(status);
CREATE INDEX IF NOT EXISTS idx_community_submissions_created ON community_submissions(created_at DESC);

-- Enable Row Level Security
ALTER TABLE community_submissions ENABLE ROW LEVEL SECURITY;

-- Policy: Anyone can insert (submit)
CREATE POLICY "Anyone can submit games"
ON community_submissions
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- Policy: Anyone can view approved games
CREATE POLICY "Anyone can view approved games"
ON community_submissions
FOR SELECT
TO anon, authenticated
USING (status = 'approved');

-- Policy: Authenticated users (admin) have full access
CREATE POLICY "Authenticated full access"
ON community_submissions
FOR ALL
TO authenticated
USING (true)
WITH CHECK (true);

-- Submit function: accepts game_data, description, submitted_by_name, password_hash
-- Returns the submission UUID
-- game_data format: { game, folderName, linkIdentifier, cardPurchaseUrl, hasPhysicalCards, isCustom, tracks[] }
CREATE OR REPLACE FUNCTION submit_community_game(
    p_game_data JSONB,
    p_description TEXT DEFAULT NULL,
    p_submitted_by_name TEXT DEFAULT NULL,
    p_submission_password_hash TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_submission_id UUID;
BEGIN
    -- Validate game_data has required fields (uses "game" not "gameName")
    IF p_game_data IS NULL OR p_game_data->>'game' IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'game_data must contain game');
    END IF;
    
    IF p_submission_password_hash IS NULL OR p_submission_password_hash = '' THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'password_hash is required');
    END IF;
    
    -- Insert submission
    INSERT INTO community_submissions (
        game_data,
        description,
        submitted_by_name,
        submission_password_hash,
        status
    ) VALUES (
        p_game_data,
        p_description,
        p_submitted_by_name,
        p_submission_password_hash,
        'pending'
    )
    RETURNING id INTO v_submission_id;
    
    RETURN jsonb_build_object(
        'success', TRUE,
        'submission_id', v_submission_id
    );
    
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', FALSE,
        'error', SQLERRM
    );
END;
$$;

-- Function to check submission status (by id + password)
CREATE OR REPLACE FUNCTION get_submission_status(
    p_submission_id UUID,
    p_password_hash TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_submission RECORD;
BEGIN
    SELECT id, game_data->>'game' as game_name, status, rejection_reason, 
           created_at, updated_at, vote_count, download_count, official_game_id
    INTO v_submission
    FROM community_submissions
    WHERE id = p_submission_id
      AND submission_password_hash = p_password_hash;
    
    IF v_submission IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Invalid ID or password');
    END IF;
    
    RETURN jsonb_build_object(
        'success', TRUE,
        'id', v_submission.id,
        'game_name', v_submission.game_name,
        'status', v_submission.status,
        'rejection_reason', v_submission.rejection_reason,
        'created_at', v_submission.created_at,
        'updated_at', v_submission.updated_at,
        'vote_count', v_submission.vote_count,
        'download_count', v_submission.download_count,
        'official_game_id', v_submission.official_game_id
    );
END;
$$;

-- Function to edit submission (only if pending or in_progress, resets to pending)
CREATE OR REPLACE FUNCTION edit_submission(
    p_submission_id UUID,
    p_password_hash TEXT,
    p_game_data JSONB DEFAULT NULL,
    p_description TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_current_status TEXT;
BEGIN
    -- Verify ownership and get current status
    SELECT status INTO v_current_status
    FROM community_submissions
    WHERE id = p_submission_id
      AND submission_password_hash = p_password_hash;
    
    IF v_current_status IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Invalid ID or password');
    END IF;
    
    IF v_current_status NOT IN ('pending', 'in_progress') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Cannot edit submission with status: ' || v_current_status);
    END IF;
    
    -- Update submission and reset to pending
    UPDATE community_submissions
    SET game_data = COALESCE(p_game_data, game_data),
        description = COALESCE(p_description, description),
        status = 'pending',
        updated_at = NOW()
    WHERE id = p_submission_id;
    
    RETURN jsonb_build_object('success', TRUE, 'message', 'Submission updated');
END;
$$;

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION submit_community_game TO anon, authenticated;
GRANT EXECUTE ON FUNCTION get_submission_status TO anon, authenticated;
GRANT EXECUTE ON FUNCTION edit_submission TO anon, authenticated;

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_community_submissions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_community_submissions_updated_at
BEFORE UPDATE ON community_submissions
FOR EACH ROW
EXECUTE FUNCTION update_community_submissions_updated_at();
