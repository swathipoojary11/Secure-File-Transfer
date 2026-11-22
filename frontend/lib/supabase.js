import { createClient } from '@supabase/supabase-js';

/**
 * Supabase Client Initialization.
 * 
 * Networking & Cloud Integration:
 * - Supabase manages Layer 7 Authentication (JWT issuance and validation).
 * - PostgreSQL handles metadata storage and Row Level Security (RLS).
 * - The client below uses the public anonymous key (safe for browser environments).
 */
const rawUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://placeholder.supabase.co';
// Normalize URL by stripping any accidental /rest/v1 or trailing slashes
const supabaseUrl = rawUrl.replace(/\/rest\/v1\/?$/, '').replace(/\/$/, '');
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'placeholder-anon-key';

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
  },
});
