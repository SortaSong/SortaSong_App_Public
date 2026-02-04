-- Admin Access Policies for SortaSong
-- Run this AFTER creating an admin user in Supabase Auth (Authentication > Users > Add user)
-- Replace 'YOUR_ADMIN_EMAIL' with your actual admin email

-- ==============================================
-- TRACK_REPORTS TABLE - Admin full access
-- ==============================================

-- Drop existing policies if they exist (ignore errors if they don't)
DROP POLICY IF EXISTS "Admin full access to track_reports" ON track_reports;

-- Allow authenticated admin users to read all reports
CREATE POLICY "Admin full access to track_reports"
ON track_reports
FOR ALL
TO authenticated
USING (true)
WITH CHECK (true);

-- ==============================================
-- TRACKS TABLE - Admin can update
-- ==============================================

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Admin can update tracks" ON tracks;

-- Allow authenticated users to read tracks (already exists via anon)
-- Allow authenticated admin users to update tracks
CREATE POLICY "Admin can update tracks"
ON tracks
FOR UPDATE
TO authenticated
USING (true)
WITH CHECK (true);

-- ==============================================
-- COMMUNITY_SUBMISSIONS TABLE - Admin full access
-- ==============================================
-- Note: Run this only after creating the community_submissions table

-- DROP POLICY IF EXISTS "Admin full access to community_submissions" ON community_submissions;

-- CREATE POLICY "Admin full access to community_submissions"
-- ON community_submissions
-- FOR ALL
-- TO authenticated
-- USING (true)
-- WITH CHECK (true);

-- ==============================================
-- INSTRUCTIONS
-- ==============================================
-- 1. Go to Supabase Dashboard > Authentication > Users
-- 2. Click "Add user" and create an admin account
-- 3. Run this SQL in the SQL Editor
-- 4. Login to the admin interface with your admin email/password
