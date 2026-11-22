'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  ShieldCheck,
  Server,
  Lock,
  ArrowRight,
  RefreshCw,
  FileCheck,
  Radio,
  Layers,
  UploadCloud,
  CheckCircle2,
} from 'lucide-react';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';

export default function HomePage() {
  const [backendData, setBackendData] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchBackendHealth = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/health', { cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        setBackendData(data);
      } else {
        setBackendData(null);
      }
    } catch (err) {
      setBackendData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBackendHealth();
    const interval = setInterval(fetchBackendHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const isUp = backendData?.status === 'UP';

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900 font-sans">
      <Navbar backendStatus={backendData} />

      <main className="flex-1 max-w-6xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {/* Hero Section */}
        <div className="text-center max-w-2xl mx-auto py-8">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-blue-50 border border-blue-200 text-blue-700 text-xs font-semibold mb-5">
            <Layers className="w-3.5 h-3.5" />
            <span>DATA COMMUNICATION &amp; NETWORKS</span>
          </div>

          <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-slate-900 leading-tight">
            Secure File Transfer Using <span className="text-blue-600">Encryption</span> &amp; <span className="text-emerald-600">Integrity Verification</span>
          </h1>

          <p className="mt-3 text-slate-600 text-sm leading-relaxed">
            Peer-to-peer file transfer over Wi-Fi with TLS 1.3 sockets, AES-256-GCM encryption, and SHA-256 integrity verification.
          </p>

          <div className="mt-6 flex items-center justify-center gap-3 flex-wrap">
            <Link
              href="/dashboard"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold transition-colors shadow-sm"
            >
              <span>Dashboard</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>

            <Link
              href="/send"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 text-xs font-semibold transition-colors shadow-sm"
            >
              <UploadCloud className="w-3.5 h-3.5 text-blue-600" />
              <span>Send Files</span>
            </Link>
          </div>
        </div>

        {/* Real Backend Service Status Card */}
        <div className="mt-4 p-4 rounded-lg bg-white border border-slate-200 shadow-sm max-w-xl mx-auto">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className={`w-2.5 h-2.5 rounded-full ${isUp ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'}`} />
              <div>
                <div className="text-xs font-semibold text-slate-900">
                  Backend Server Status
                </div>
                <div className="text-[11px] text-slate-500 font-mono">
                  {backendData?.localIp ? `Node IP: ${backendData.localIp} · Port 8085` : 'http://localhost:8085'}
                </div>
              </div>
            </div>

            <div className="flex items-center space-x-2">
              <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-md border ${isUp ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-red-50 text-red-700 border-red-200'}`}>
                {isUp ? 'ONLINE' : 'OFFLINE'}
              </span>
              <button
                onClick={fetchBackendHealth}
                disabled={loading}
                className="p-1 rounded-md border border-slate-200 text-slate-500 hover:bg-slate-50 transition-colors"
                title="Refresh Status"
              >
                <RefreshCw className={`w-3 h-3 ${loading ? 'animate-spin' : ''}`} />
              </button>
            </div>
          </div>
        </div>

        {/* 4 Core Features */}
        <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-4 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="w-8 h-8 rounded-md bg-blue-50 text-blue-600 flex items-center justify-center mb-2.5">
              <Lock className="w-4 h-4" />
            </div>
            <h3 className="text-xs font-bold text-slate-900">TLS 1.3 Sockets</h3>
            <p className="mt-1 text-[11px] text-slate-500 leading-normal">
              Direct encrypted TCP socket connection on port 8443.
            </p>
          </div>

          <div className="p-4 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="w-8 h-8 rounded-md bg-emerald-50 text-emerald-600 flex items-center justify-center mb-2.5">
              <FileCheck className="w-4 h-4" />
            </div>
            <h3 className="text-xs font-bold text-slate-900">SHA-256 Integrity</h3>
            <p className="mt-1 text-[11px] text-slate-500 leading-normal">
              Cryptographic digest verification to detect tampering.
            </p>
          </div>

          <div className="p-4 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="w-8 h-8 rounded-md bg-indigo-50 text-indigo-600 flex items-center justify-center mb-2.5">
              <Radio className="w-4 h-4" />
            </div>
            <h3 className="text-xs font-bold text-slate-900">Binary Framing</h3>
            <p className="mt-1 text-[11px] text-slate-500 leading-normal">
              Custom header protocol preventing packet fragmentation.
            </p>
          </div>

          <div className="p-4 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="w-8 h-8 rounded-md bg-amber-50 text-amber-600 flex items-center justify-center mb-2.5">
              <ShieldCheck className="w-4 h-4" />
            </div>
            <h3 className="text-xs font-bold text-slate-900">Tamper Defense</h3>
            <p className="mt-1 text-[11px] text-slate-500 leading-normal">
              Detects any byte modification and purges corrupt files.
            </p>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
