-- Add email verification columns to users table
-- Migration V2: Add Email Verification Support

-- Add email_verified column (default false for existing users)
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Add verification_token column (nullable)
ALTER TABLE users ADD COLUMN verification_token VARCHAR(255);

-- Add verification_token_expires_at column (nullable)
ALTER TABLE users ADD COLUMN verification_token_expires_at TIMESTAMP;

-- Set email_verified to true for existing OAuth users (they have pre-verified emails)
UPDATE users SET email_verified = TRUE WHERE oauth_provider != 'local' OR oauth_provider IS NULL;

-- Add index on verification_token for faster lookups
CREATE INDEX idx_users_verification_token ON users(verification_token);

-- Add comment for documentation
COMMENT ON COLUMN users.email_verified IS 'Whether the user has verified their email address';
COMMENT ON COLUMN users.verification_token IS 'Token used for email verification';
COMMENT ON COLUMN users.verification_token_expires_at IS 'Expiration time for verification token';