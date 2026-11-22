'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  FlaskConical,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Play,
  FileText,
  Binary,
  Layers,
  ArrowRight,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiFetch } from '../../lib/api';

export default function TamperingLabPage() {
  const [tamperType, setTamperType] = useState('SINGLE_BIT_FLIP');
  const [sampleText, setSampleText] = useState(
    'DATA COMMUNICATION & NETWORKS: Comprehensive Final Project Specification. Cryptographic integrity must be preserved.'
  );
  const [selectedFile, setSelectedFile] = useState(null);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleSimulate = async (e) => {
    e.preventDefault();
    setRunning(true);
    setResult(null);
    setError(null);

    try {
      const formData = new FormData();
      if (selectedFile) {
        formData.append('file', selectedFile);
      } else {
        formData.append('sampleText', sampleText);
      }
      formData.append('tamperType', tamperType);

      const res = await apiFetch('/api/tampering/simulate', {
        method: 'POST',
        body: formData,
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || `Simulation failed with HTTP ${res.status}`);
      }

      const data = await res.json();
      setResult(data);
    } catch (err) {
      setError(err.message || 'Simulation error');
    } finally {
      setRunning(false);
    }
  };

  return (
    <AppShell pageTitle="Tampering Lab">
      <div className="space-y-6 max-w-4xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Tampering Lab
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Simulate bit alterations and verify SHA-256 detection.
            </p>
          </div>
          <Link
            href="/security"
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
          >
            <span>Security Center</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        {/* Experiment Setup Form */}
        <form onSubmit={handleSimulate} className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm space-y-5">
          <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
            <FlaskConical className="w-4 h-4 text-blue-600" />
            <span>Select Simulation Test Case</span>
          </div>

          {/* Test Case Selection */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
            <button
              type="button"
              onClick={() => setTamperType('NONE')}
              className={`p-3 rounded-lg text-left border transition-all ${
                tamperType === 'NONE'
                  ? 'bg-blue-50 border-blue-600 text-blue-950 font-semibold'
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              <div className="flex items-center space-x-1.5 mb-1 text-xs">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                <span className="font-bold">Case A: Legitimate</span>
              </div>
              <p className="text-[11px] text-slate-500 font-normal">Control test. Hashes match 100%; file accepted.</p>
            </button>

            <button
              type="button"
              onClick={() => setTamperType('SINGLE_BIT_FLIP')}
              className={`p-3 rounded-lg text-left border transition-all ${
                tamperType === 'SINGLE_BIT_FLIP'
                  ? 'bg-red-50 border-red-600 text-red-950 font-semibold'
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              <div className="flex items-center space-x-1.5 mb-1 text-xs">
                <AlertTriangle className="w-4 h-4 text-red-600" />
                <span className="font-bold">Case B: 1-Bit Tamper</span>
              </div>
              <p className="text-[11px] text-slate-500 font-normal">Flips exactly 1 bit. Shows ~50% Avalanche Effect.</p>
            </button>

            <button
              type="button"
              onClick={() => setTamperType('BYTE_INJECTION')}
              className={`p-3 rounded-lg text-left border transition-all ${
                tamperType === 'BYTE_INJECTION'
                  ? 'bg-amber-50 border-amber-600 text-amber-950 font-semibold'
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              <div className="flex items-center space-x-1.5 mb-1 text-xs">
                <Binary className="w-4 h-4 text-amber-600" />
                <span className="font-bold">Case C: Injection</span>
              </div>
              <p className="text-[11px] text-slate-500 font-normal">Appends unauthorized rogue bytes to payload.</p>
            </button>

            <button
              type="button"
              onClick={() => setTamperType('BYTE_TRUNCATION')}
              className={`p-3 rounded-lg text-left border transition-all ${
                tamperType === 'BYTE_TRUNCATION'
                  ? 'bg-purple-50 border-purple-600 text-purple-950 font-semibold'
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              <div className="flex items-center space-x-1.5 mb-1 text-xs">
                <Layers className="w-4 h-4 text-purple-600" />
                <span className="font-bold">Case D: Truncation</span>
              </div>
              <p className="text-[11px] text-slate-500 font-normal">Simulates packet drop by dropping trailing bytes.</p>
            </button>
          </div>

          {/* Payload Content */}
          <div className="space-y-1.5">
            <label className="block text-slate-700 text-xs font-medium">
              Payload Text to Test:
            </label>
            <textarea
              rows={3}
              value={sampleText}
              onChange={(e) => setSampleText(e.target.value)}
              className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-xs text-slate-900 font-mono focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          {/* Trigger Button */}
          <button
            type="submit"
            disabled={running}
            className="w-full py-2.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs transition-colors flex items-center justify-center space-x-2 shadow-sm disabled:opacity-50"
          >
            <Play className={`w-3.5 h-3.5 ${running ? 'animate-spin' : ''}`} />
            <span>{running ? 'Running Simulation...' : 'Simulate Transfer & Integrity Check'}</span>
          </button>
        </form>

        {/* SIMULATION RESULTS PRESENTATION */}
        {result && (
          <div className="p-6 rounded-lg bg-white border border-slate-200 shadow-sm space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-900">Simulation Outcome</h3>
                <p className="text-xs text-slate-500 mt-0.5">{result.modificationSummary}</p>
              </div>
              <span
                className={`px-3 py-1 rounded-md text-xs font-bold border ${
                  result.match
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                    : 'bg-red-50 text-red-700 border-red-200'
                }`}
              >
                {result.match ? 'VERIFIED (100% MATCH)' : 'INTEGRITY FAILED (TAMPER DETECTED)'}
              </span>
            </div>

            {/* Cryptographic Hashes Comparison */}
            <div className="space-y-3 font-mono text-xs">
              <div className="p-3 rounded-md bg-slate-50 border border-slate-200 space-y-1">
                <div className="text-slate-500 text-[11px] font-sans font-medium">
                  Original Sender SHA-256 Digest:
                </div>
                <div className="text-slate-900 break-all select-all font-bold">
                  {result.originalHash}
                </div>
              </div>

              <div
                className={`p-3 rounded-md border space-y-1 ${
                  result.match ? 'bg-slate-50 border-slate-200' : 'bg-red-50/60 border-red-200'
                }`}
              >
                <div className="text-slate-500 text-[11px] font-sans font-medium">
                  Receiver SHA-256 Digest (After In-Transit State):
                </div>
                <div
                  className={`break-all select-all font-bold ${
                    result.match ? 'text-emerald-700' : 'text-red-700'
                  }`}
                >
                  {result.modifiedHash}
                </div>
              </div>
            </div>

            {/* Metrics */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
              <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
                <span className="text-slate-500 text-[11px] block">Bytes Altered</span>
                <span className="text-slate-900 font-bold text-base block font-mono mt-0.5">{result.bytesModified} Byte</span>
              </div>

              <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
                <span className="text-slate-500 text-[11px] block">Avalanche Effect Divergence</span>
                <span className={`font-bold text-base block font-mono mt-0.5 ${result.avalancheEffectPercentage > 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                  {result.avalancheEffectPercentage}%
                </span>
                <span className="text-[10px] text-slate-400">~50% expected in SHA-256</span>
              </div>

              <div className="p-3 rounded-md bg-slate-50 border border-slate-200">
                <span className="text-slate-500 text-[11px] block">Receiver Action</span>
                <span
                  className={`font-bold text-sm block font-mono mt-0.5 ${
                    result.match ? 'text-emerald-700' : 'text-red-700'
                  }`}
                >
                  {result.match ? 'COMMITTED TO DISK' : 'PURGED & QUARANTINED'}
                </span>
              </div>
            </div>

            {!result.match && (
              <div className="p-3 rounded-md bg-red-50 border border-red-200 text-red-800 text-xs flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <ShieldAlert className="w-4 h-4 text-red-600 flex-shrink-0" />
                  <span>CRITICAL security incident logged to audit table.</span>
                </div>
                <Link
                  href="/security"
                  className="font-semibold underline text-red-900 hover:text-red-700"
                >
                  View Security Center
                </Link>
              </div>
            )}
          </div>
        )}
      </div>
    </AppShell>
  );
}
