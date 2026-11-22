'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { Shield, Radio, ArrowUpRight, Lock, Activity, LogOut, Laptop } from 'lucide-react';
import { supabase } from '../lib/supabase';

export default function Navbar({ backendStatus: initialStatus }) {
  const pathname = usePathname();
  const router = useRouter();
  const [userEmail, setUserEmail] = useState(null);
  const [backendStatus, setBackendStatus] = useState(initialStatus || null);

  useEffect(() => {
    if (initialStatus) {
      setBackendStatus(initialStatus);
      return;
    }
    const check = async () => {
      try {
        const res = await fetch('/api/health');
        if (res.ok) {
          const data = await res.json();
          setBackendStatus(data);
        } else {
          setBackendStatus(null);
        }
      } catch (e) {
        setBackendStatus(null);
      }
    };
    check();
    const interval = setInterval(check, 10000);
    return () => clearInterval(interval);
  }, [initialStatus]);

  const isConnected = backendStatus?.status === 'UP';

  useEffect(() => {
    const checkUser = async () => {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session?.user?.email) {
          setUserEmail(session.user.email);
        } else if (typeof window !== 'undefined') {
          const cached = localStorage.getItem('dcn_user_email');
          if (cached) setUserEmail(cached);
        }
      } catch (ignored) {}
    };
    checkUser();

    const { data: authListener } = supabase.auth.onAuthStateChange((_event, session) => {
      if (session?.user?.email) {
        setUserEmail(session.user.email);
        if (typeof window !== 'undefined') {
          localStorage.setItem('dcn_jwt_token', session.access_token);
          localStorage.setItem('dcn_user_email', session.user.email);
        }
      } else {
        setUserEmail(null);
      }
    });

    return () => {
      authListener?.subscription?.unsubscribe();
    };
  }, []);

  const handleSignOut = async () => {
    try {
      await supabase.auth.signOut();
    } catch (ignored) {}
    if (typeof window !== 'undefined') {
      localStorage.removeItem('dcn_jwt_token');
      localStorage.removeItem('dcn_user_email');
    }
    setUserEmail(null);
    router.push('/login');
  };

  return (
    <header className="sticky top-0 z-50 w-full bg-white border-b border-slate-200 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand Logo */}
        <Link href="/" className="flex items-center space-x-2.5">
          <div className="w-9 h-9 rounded-md bg-blue-600 flex items-center justify-center text-white shadow-sm">
            <Shield className="w-5 h-5" />
          </div>
          <div>
            <div className="font-bold text-base text-slate-900 tracking-tight flex items-center space-x-1">
              <span>Secure</span>
              <span className="text-blue-600">Transfer</span>
            </div>
            <div className="text-[10px] text-slate-500 font-medium">
              DCN Mini-Project
            </div>
          </div>
        </Link>

        {/* Navigation Links */}
        <nav className="hidden md:flex items-center space-x-1">
          <Link
            href="/dashboard"
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              pathname === '/dashboard' ? 'bg-blue-50 text-blue-700 font-semibold' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            Dashboard
          </Link>
          <Link
            href="/send"
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              pathname === '/send' ? 'bg-blue-50 text-blue-700 font-semibold' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            Send Files
          </Link>
          <Link
            href="/receive"
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              pathname === '/receive' ? 'bg-blue-50 text-blue-700 font-semibold' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            Receive Files
          </Link>
          <Link
            href="/tampering-lab"
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              pathname === '/tampering-lab' ? 'bg-blue-50 text-blue-700 font-semibold' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            Tampering Lab
          </Link>
        </nav>

        {/* Right Action & Status */}
        <div className="flex items-center space-x-3">
          {/* Status Badge */}
          <div
            className={`hidden sm:flex items-center space-x-1.5 px-2.5 py-1 rounded-md text-xs font-medium border ${
              isConnected
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : 'bg-red-50 text-red-700 border-red-200'
            }`}
          >
            <span className={`w-1.5 h-1.5 rounded-full ${isConnected ? 'bg-emerald-500' : 'bg-red-500'}`} />
            <span>{isConnected ? 'Port 8085 Online' : 'Offline'}</span>
          </div>

          {/* User Auth Info or Login Button */}
          {userEmail ? (
            <div className="flex items-center space-x-2">
              <span className="text-xs text-slate-600 font-mono hidden lg:inline max-w-[160px] truncate">
                {userEmail}
              </span>
              <button
                onClick={handleSignOut}
                className="p-1.5 rounded-md border border-slate-200 hover:bg-slate-100 text-slate-600 hover:text-red-600 transition-colors"
                title="Sign Out"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <Link
              href="/login"
              className="px-3.5 py-1.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-xs font-medium transition-colors shadow-sm"
            >
              Sign In
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
