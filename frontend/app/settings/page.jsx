'use client';

import { useState, useEffect } from 'react';
import {
  Settings,
  Server,
  Lock,
  HardDrive,
  RefreshCw,
  Copy,
  Radio,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet } from '../../lib/api';

export default function SettingsPage() {
  const [healthData, setHealthData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pingLatency, setPingLatency] = useState(null);
  const [pinging, setPinging] = useState(false);

  const loadHealth = async () => {
    try {
      setLoading(true);
      const start = performance.now();
      const data = await apiGet('/api/health');
      const latency = Math.round(performance.now() - start);
      setHealthData(data);
      setPingLatency(latency);
    } catch (err) {
      console.error('Health load error:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHealth();
  }, []);

  const triggerPing = async () => {
    setPinging(true);
    try {
      const start = performance.now();
      const res = await apiGet('/api/health');
      const end = performance.now();
      setHealthData(res);
      setPingLatency(Math.round(end - start));
    } catch (err) {
      setPingLatency(-1);
    } finally {
      setPinging(false);
    }
  };

  const copyToClipboard = (text) => {
    if (typeof navigator !== 'undefined') {
      navigator.clipboard.writeText(text);
    }
  };

  return (
    <AppShell pageTitle="Settings">
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Node Settings
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Network port bindings and system configuration.
            </p>
          </div>
          <button
            onClick={triggerPing}
            disabled={pinging}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${pinging ? 'animate-spin' : ''}`} />
            <span>Ping Backend {pingLatency !== null ? `(${pingLatency} ms)` : ''}</span>
          </button>
        </div>

        {/* 1. Network & Socket Configuration */}
        <div className="rounded-lg bg-white border border-slate-200 p-5 shadow-sm space-y-4">
          <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
            <Radio className="w-4 h-4 text-blue-600" />
            <span>Network Layer 4 &amp; Layer 7 Bindings</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div className="p-3 rounded-md bg-slate-50 border border-slate-200 flex items-center justify-between">
              <div>
                <span className="text-slate-500 block text-[11px] font-medium">Local Host IPv4 Address</span>
                <span className="text-slate-900 font-mono font-bold text-sm mt-0.5 block">
                  {healthData?.localIp || '127.0.0.1'}
                </span>
              </div>
              <button
                onClick={() => copyToClipboard(healthData?.localIp || '127.0.0.1')}
                className="p-1.5 rounded bg-white hover:bg-slate-100 text-slate-500 border border-slate-200"
                title="Copy IP"
              >
                <Copy className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200 flex items-center justify-between">
              <div>
                <span className="text-slate-500 block text-[11px] font-medium">TLS Socket Transfer Server</span>
                <span className="text-blue-700 font-mono font-bold text-sm mt-0.5 block">
                  TCP Port 8443
                </span>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                ACTIVE
              </span>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
              <span className="text-slate-500 block text-[11px] font-medium">Spring Boot REST API</span>
              <span className="text-slate-900 font-mono font-bold text-sm mt-0.5 block">
                HTTP Port 8085
              </span>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
              <span className="text-slate-500 block text-[11px] font-medium">Next.js Web Frontend</span>
              <span className="text-slate-900 font-mono font-bold text-sm mt-0.5 block">
                Web Port 3000
              </span>
            </div>
          </div>
        </div>

        {/* 2. Cryptographic Configuration */}
        <div className="rounded-lg bg-white border border-slate-200 p-5 shadow-sm space-y-4">
          <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
            <Lock className="w-4 h-4 text-emerald-600" />
            <span>Cryptographic &amp; Security Settings</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
            <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
              <span className="text-slate-500 block text-[11px] font-sans font-medium">Enforced Protocol</span>
              <span className="text-emerald-700 font-bold mt-0.5 block">
                TLSv1.3 (RFC 8446)
              </span>
              <span className="text-[10px] text-slate-400 font-sans mt-0.5 block">
                Legacy SSL 3.0 / TLS 1.0 disabled
              </span>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
              <span className="text-slate-500 block text-[11px] font-sans font-medium">AEAD Cipher Suite</span>
              <span className="text-slate-900 font-bold mt-0.5 block">
                TLS_AES_256_GCM_SHA384
              </span>
              <span className="text-[10px] text-slate-400 font-sans mt-0.5 block">
                Galois/Counter Mode with 128-bit MAC
              </span>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
              <span className="text-slate-500 block text-[11px] font-sans font-medium">Integrity Verification</span>
              <span className="text-slate-900 font-bold mt-0.5 block">
                SHA-256 (FIPS 180-4)
              </span>
              <span className="text-[10px] text-slate-400 font-sans mt-0.5 block">
                64 KB streaming buffers, O(1) RAM
              </span>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
              <span className="text-slate-500 block text-[11px] font-sans font-medium">KeyStore / TrustStore</span>
              <span className="text-slate-900 font-bold mt-0.5 block">
                PKCS12 Keystore
              </span>
              <span className="text-[10px] text-slate-400 font-sans mt-0.5 block">
                server-keystore.p12 / client-truststore.p12
              </span>
            </div>
          </div>
        </div>

        {/* 3. Filesystem Directories */}
        <div className="rounded-lg bg-white border border-slate-200 p-5 shadow-sm space-y-4">
          <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
            <HardDrive className="w-4 h-4 text-indigo-600" />
            <span>Filesystem Storage Locations</span>
          </div>

          <div className="space-y-2 text-xs font-mono">
            <div className="p-3 rounded-md bg-slate-50 border border-slate-200 flex items-center justify-between">
              <div>
                <span className="text-slate-500 block text-[11px] font-sans font-medium">Temporary Quarantine &amp; Staging Directory</span>
                <span className="text-slate-800 font-bold">./storage/temp/</span>
              </div>
              <span className="text-[11px] text-slate-400 font-sans">Purged on failure</span>
            </div>

            <div className="p-3 rounded-md bg-slate-50 border border-slate-200 flex items-center justify-between">
              <div>
                <span className="text-slate-500 block text-[11px] font-sans font-medium">Verified Permanent Storage Directory</span>
                <span className="text-emerald-700 font-bold">./storage/received/</span>
              </div>
              <span className="text-[11px] text-slate-400 font-sans">Atomic move on match</span>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
