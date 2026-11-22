'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  Shield,
  LayoutDashboard,
  UploadCloud,
  DownloadCloud,
  Laptop,
  History,
  CheckCircle2,
  FlaskConical,
  ShieldAlert,
  BarChart3,
  Settings,
  LogOut,
  Radio,
  Menu,
  X,
  Server,
  Activity,
  User,
} from 'lucide-react';
import { supabase } from '../lib/supabase';
import { apiGet } from '../lib/api';

const NAV_ITEMS = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
  { name: 'Send Files', href: '/send', icon: UploadCloud },
  { name: 'Receive Files', href: '/receive', icon: DownloadCloud },
  { name: 'Connected Devices', href: '/devices', icon: Laptop },
  { name: 'Transfer History', href: '/history', icon: History },
  { name: 'Integrity Audit', href: '/integrity', icon: CheckCircle2 },
  { name: 'Tampering Lab', href: '/tampering-lab', icon: FlaskConical },
  { name: 'Security Center', href: '/security', icon: ShieldAlert },
  { name: 'Analytics', href: '/analytics', icon: BarChart3 },
  { name: 'Node Settings', href: '/settings', icon: Settings },
];

export default function AppShell({ children, pageTitle = 'Dashboard' }) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userEmail, setUserEmail] = useState('operator@dcn.local');
  const [backendHealth, setBackendHealth] = useState(null);

  useEffect(() => {
    const resolveUser = async () => {
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
    resolveUser();

    const checkHealth = async () => {
      try {
        const data = await apiGet('/api/health');
        setBackendHealth(data);
      } catch (err) {
        setBackendHealth({ status: 'DOWN', error: err.message });
      }
    };
    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleSignOut = async () => {
    try {
      await supabase.auth.signOut();
    } catch (ignored) {}
    if (typeof window !== 'undefined') {
      localStorage.removeItem('dcn_jwt_token');
      localStorage.removeItem('dcn_user_email');
    }
    router.push('/login');
  };

  const isUp = backendHealth?.status === 'UP';

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col md:flex-row font-sans">
      {/* Mobile Top Header */}
      <div className="md:hidden flex items-center justify-between px-4 py-3 bg-white border-b border-slate-200 shadow-sm">
        <Link href="/" className="flex items-center space-x-2">
          <div className="w-8 h-8 rounded-md bg-blue-600 flex items-center justify-center text-white">
            <Shield className="w-5 h-5" />
          </div>
          <span className="font-bold text-sm tracking-tight text-slate-900">
            Secure<span className="text-blue-600">Transfer</span>
          </span>
        </Link>
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="p-2 rounded-md border border-slate-200 text-slate-600 hover:bg-slate-100"
          aria-label="Toggle Navigation"
        >
          {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Left Sidebar */}
      <aside
        className={`fixed md:static inset-y-0 left-0 z-40 w-64 bg-white border-r border-slate-200 flex flex-col justify-between transform transition-transform duration-200 ease-in-out md:translate-x-0 shadow-sm ${
          mobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div>
          {/* Brand Header */}
          <div className="p-5 border-b border-slate-200 flex items-center space-x-3">
            <div className="w-9 h-9 rounded-md bg-blue-600 flex items-center justify-center text-white shadow-sm">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <div className="font-bold text-sm text-slate-900 tracking-tight">
                Secure File Transfer
              </div>
              <div className="text-[11px] text-slate-500 font-medium">
                DCN Mini-Project
              </div>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="p-3 space-y-1">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center space-x-3 px-3 py-2.5 rounded-md text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-blue-50 text-blue-700 font-semibold'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-blue-600' : 'text-slate-400'}`} />
                  <span>{item.name}</span>
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Sidebar Footer: Real Node & Auth Status */}
        <div className="p-3 border-t border-slate-200 bg-slate-50 space-y-2">
          {/* Service Status Pill */}
          <div className="px-3 py-2 rounded-md bg-white border border-slate-200 flex items-center justify-between text-xs">
            <div className="flex items-center space-x-2">
              <span className={`w-2 h-2 rounded-full ${isUp ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'}`} />
              <span className="text-slate-600 font-medium">Backend (8085)</span>
            </div>
            <span className={`text-[11px] font-semibold ${isUp ? 'text-emerald-700' : 'text-red-700'}`}>
              {isUp ? 'ONLINE' : 'OFFLINE'}
            </span>
          </div>

          {/* User Session Info */}
          <div className="px-3 py-2 rounded-md bg-white border border-slate-200">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2 overflow-hidden">
                <User className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="text-xs text-slate-700 font-mono truncate">
                  {userEmail}
                </span>
              </div>
              <button
                onClick={handleSignOut}
                className="text-slate-400 hover:text-red-600 transition-colors p-1"
                title="Sign Out"
              >
                <LogOut className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top Header Bar */}
        <header className="hidden md:flex h-16 bg-white border-b border-slate-200 px-6 items-center justify-between shadow-sm">
          <div className="flex items-center space-x-3">
            <h1 className="text-lg font-bold text-slate-900 tracking-tight">{pageTitle}</h1>
          </div>

          <div className="flex items-center space-x-4">
            {/* TLS 1.3 / Port Info */}
            <div className="flex items-center space-x-1.5 px-2.5 py-1 rounded-md bg-slate-100 border border-slate-200 text-xs font-mono text-slate-700">
              <Radio className="w-3 h-3 text-blue-600" />
              <span>TLS 1.3 Port 8443</span>
            </div>

            {/* Health Badge */}
            <div
              className={`flex items-center space-x-1.5 px-2.5 py-1 rounded-md border text-xs font-medium ${
                isUp
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                  : 'bg-red-50 text-red-700 border-red-200'
              }`}
            >
              <span className={`w-1.5 h-1.5 rounded-full ${isUp ? 'bg-emerald-500' : 'bg-red-500'}`} />
              <span>{isUp ? 'System Operational' : 'Backend Disconnected'}</span>
            </div>
          </div>
        </header>

        {/* Dynamic Page Content */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8 overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  );
}
