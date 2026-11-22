-- ============================================================================
-- DATA COMMUNICATION AND NETWORKS (DCN) ACADEMIC MINI-PROJECT
-- Title: Secure File Transfer Using Encryption and Integrity Verification
-- Database: Supabase PostgreSQL Schema with RLS and Security Triggers
-- ============================================================================

-- Enable UUID extension if not already present
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 1. PROFILES TABLE
-- Maps 1:1 with Supabase auth.users to store application user metadata.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    email TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Profiles RLS Policies:
-- Any authenticated user can view profiles (needed to list transfer destinations)
CREATE POLICY "Allow authenticated users to read profiles"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

-- Users can only update their own profile
CREATE POLICY "Allow users to update their own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id);

-- ----------------------------------------------------------------------------
-- 2. AUTOMATIC PROFILE CREATION TRIGGER
-- Whenever a user registers through Supabase Auth, automatically insert
-- a matching row into public.profiles using their auth metadata.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, username, email)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'username', SPLIT_PART(NEW.email, '@', 1)),
        NEW.email
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger definition for auth.users
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ----------------------------------------------------------------------------
-- 3. DEVICES TABLE
-- Registers computers participating in the peer-to-peer TCP/TLS transfer network.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL,
    device_ip TEXT NOT NULL,
    listening_port INTEGER NOT NULL DEFAULT 8443,
    status TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline', 'busy')),
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL
);

-- Index for fast user device lookups
CREATE INDEX IF NOT EXISTS idx_devices_user_id ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_status ON public.devices(status);

-- Enable RLS on devices
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

-- Devices RLS Policies:
-- Users can read all devices so they can discover available transfer recipients
CREATE POLICY "Allow authenticated users to discover devices"
    ON public.devices FOR SELECT
    TO authenticated
    USING (true);

-- Users can only insert, update, or delete their own registered devices
CREATE POLICY "Allow users to register their own devices"
    ON public.devices FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Allow users to update their own devices"
    ON public.devices FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Allow users to delete their own devices"
    ON public.devices FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 4. TRANSFERS TABLE
-- Logs every secure file transmission between sender and receiver agents.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    transfer_status TEXT NOT NULL DEFAULT 'pending' 
        CHECK (transfer_status IN ('pending', 'in_progress', 'completed', 'failed', 'cancelled')),
    original_hash TEXT NOT NULL,
    received_hash TEXT,
    integrity_status TEXT NOT NULL DEFAULT 'pending' 
        CHECK (integrity_status IN ('pending', 'verified', 'failed')),
    transfer_speed DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_transfers_sender ON public.transfers(sender_id);
CREATE INDEX IF NOT EXISTS idx_transfers_receiver ON public.transfers(receiver_id);
CREATE INDEX IF NOT EXISTS idx_transfers_created_at ON public.transfers(created_at DESC);

-- Enable RLS on transfers
ALTER TABLE public.transfers ENABLE ROW LEVEL SECURITY;

-- Transfers RLS Policies:
-- Users can only view transfers where they are either the sender or receiver
CREATE POLICY "Users can view their own transfers"
    ON public.transfers FOR SELECT
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Only senders can initiate transfer records
CREATE POLICY "Senders can insert transfers"
    ON public.transfers FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = sender_id);

-- Both sender and receiver can update status (e.g. progress, completion, verification)
CREATE POLICY "Participants can update transfers"
    ON public.transfers FOR UPDATE
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- ----------------------------------------------------------------------------
-- 5. INTEGRITY_CHECKS TABLE
-- Audit log of SHA-256 verification results comparing sender and receiver hashes.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.integrity_checks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id UUID NOT NULL REFERENCES public.transfers(id) ON DELETE CASCADE,
    original_hash TEXT NOT NULL,
    received_hash TEXT NOT NULL,
    verification_result TEXT NOT NULL CHECK (verification_result IN ('VERIFIED', 'FAILED')),
    verified_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_integrity_transfer_id ON public.integrity_checks(transfer_id);

-- Enable RLS on integrity_checks
ALTER TABLE public.integrity_checks ENABLE ROW LEVEL SECURITY;

-- Integrity Checks RLS Policies:
-- Users can view integrity checks for transfers they participated in
CREATE POLICY "Users can view integrity checks for their transfers"
    ON public.integrity_checks FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.transfers t
            WHERE t.id = integrity_checks.transfer_id
            AND (t.sender_id = auth.uid() OR t.receiver_id = auth.uid())
        )
    );

CREATE POLICY "Participants can insert integrity checks"
    ON public.integrity_checks FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.transfers t
            WHERE t.id = integrity_checks.transfer_id
            AND (t.sender_id = auth.uid() OR t.receiver_id = auth.uid())
        )
    );

-- ----------------------------------------------------------------------------
-- 6. SECURITY_EVENTS TABLE
-- Logs cybersecurity events such as hash mismatches, unauthorized attempts, etc.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.security_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::TEXT, NOW()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_security_events_user ON public.security_events(user_id);
CREATE INDEX IF NOT EXISTS idx_security_events_severity ON public.security_events(severity);
CREATE INDEX IF NOT EXISTS idx_security_events_created_at ON public.security_events(created_at DESC);

-- Enable RLS on security_events
ALTER TABLE public.security_events ENABLE ROW LEVEL SECURITY;

-- Security Events RLS Policies:
-- Users can view security events related to their account
CREATE POLICY "Users can view their own security events"
    ON public.security_events FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Authenticated users can log security events"
    ON public.security_events FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);
