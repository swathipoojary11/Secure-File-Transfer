'use client';

import { useState, useEffect } from 'react';
import {
  CheckCircle2,
  AlertTriangle,
  FileCheck,
  RefreshCw,
  Binary,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet, apiPost } from '../../lib/api';

export default function IntegrityPage() {
  const [auditLogs, setAuditLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  // Manual Verification Tool State
  const [originalHash, setOriginalHash] = useState('2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae');
  const [receivedHash, setReceivedHash] = useState('2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae');
  const [testFilename, setTestFilename] = useState('project_report.pdf');
  const [verifyResult, setVerifyResult] = useState(null);
  const [verifying, setVerifying] = useState(false);

  const fetchIntegrityAudits = async () => {
    try {
      setLoading(true);
      const data = await apiGet('/api/integrity');
      setAuditLogs(data || []);
    } catch (err) {
      console.error('Failed to load integrity audits:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIntegrityAudits();
  }, []);

  const handleManualVerify = async (e) => {
    e.preventDefault();
    setVerifying(true);
    setVerifyResult(null);

    try {
      const res = await apiPost('/api/integrity/verify', {
        originalHash: originalHash.trim(),
        receivedHash: receivedHash.trim(),
        filename: testFilename.trim(),
      });
      setVerifyResult(res);
      fetchIntegrityAudits();
    } catch (err) {
      console.error('Manual verification error:', err);
    } finally {
      setVerifying(false);
    }
  };

  const simulateTamper = () => {
    const tampered = originalHash.substring(0, originalHash.length - 2) + 'ff';
    setReceivedHash(tampered);
    setVerifyResult(null);
  };

  const resetHashes = () => {
    setReceivedHash(originalHash);
    setVerifyResult(null);
  };

  return (
    <AppShell pageTitle="Integrity Audit">
      <div className="space-y-6 max-w-6xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Integrity Audit
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Cryptographic hash verification and audit trail.
            </p>
          </div>
          <button
            onClick={fetchIntegrityAudits}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Reload</span>
          </button>
        </div>

        {/* INTERACTIVE HASH COMPARATOR */}
        <div className="rounded-lg bg-white border border-slate-200 p-5 shadow-sm space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2 border-b border-slate-100">
            <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
              <Binary className="w-4 h-4 text-blue-600" />
              <span>Interactive SHA-256 Hash Comparator</span>
            </div>
            <div className="flex items-center space-x-2 text-xs">
              <button
                type="button"
                onClick={simulateTamper}
                className="px-2.5 py-1 rounded-md bg-red-50 hover:bg-red-100 text-red-700 border border-red-200 transition-colors font-medium text-xs"
              >
                Inject 1-Bit Mismatch
              </button>
              <button
                type="button"
                onClick={resetHashes}
                className="px-2.5 py-1 rounded-md bg-slate-50 hover:bg-slate-100 text-slate-700 border border-slate-300 transition-colors font-medium text-xs"
              >
                Reset Matching
              </button>
            </div>
          </div>

          <form onSubmit={handleManualVerify} className="space-y-4 text-xs font-mono">
            <div>
              <label className="block text-slate-700 text-xs mb-1 font-sans font-medium">
                Filename
              </label>
              <input
                type="text"
                value={testFilename}
                onChange={(e) => setTestFilename(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-slate-900 focus:outline-none focus:ring-1 focus:ring-blue-500 font-sans"
              />
            </div>

            <div>
              <label className="block text-slate-700 text-xs mb-1 font-sans font-medium">
                Expected Original SHA-256 Digest (64 hex characters)
              </label>
              <input
                type="text"
                value={originalHash}
                onChange={(e) => setOriginalHash(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-slate-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
                required
              />
            </div>

            <div>
              <label className="block text-slate-700 text-xs mb-1 font-sans font-medium">
                Actual Received SHA-256 Digest
              </label>
              <input
                type="text"
                value={receivedHash}
                onChange={(e) => setReceivedHash(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-slate-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
                required
              />
            </div>

            <button
              type="submit"
              disabled={verifying}
              className="w-full py-2.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white font-sans font-semibold text-xs transition-colors flex items-center justify-center space-x-2 shadow-sm disabled:opacity-50"
            >
              <FileCheck className="w-4 h-4" />
              <span>{verifying ? 'Executing Constant-Time Verification...' : 'Execute Constant-Time Check'}</span>
            </button>
          </form>

          {/* Verification Result Output */}
          {verifyResult && (
            <div
              className={`p-4 rounded-lg border text-xs space-y-2 ${
                verifyResult.match
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                  : 'bg-red-50 border-red-200 text-red-800'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2 font-bold text-sm">
                  {verifyResult.match ? (
                    <>
                      <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                      <span>CRYPTOGRAPHIC INTEGRITY CONFIRMED (100% MATCH)</span>
                    </>
                  ) : (
                    <>
                      <AlertTriangle className="w-5 h-5 text-red-600" />
                      <span>HASH MISMATCH DETECTED (INTEGRITY FAILED)</span>
                    </>
                  )}
                </div>
                <span className="px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-700 text-[10px] font-mono">
                  {verifyResult.algorithm}
                </span>
              </div>

              <p className="text-slate-600 text-xs">
                {verifyResult.details}
              </p>

              <div className="pt-2 text-[11px] text-slate-500 border-t border-slate-200 flex items-center justify-between font-mono">
                <span>Verified: {new Date(verifyResult.verifiedAt).toLocaleTimeString()}</span>
                <span>Audit ID: {verifyResult.checkId?.substring(0, 8)}...</span>
              </div>
            </div>
          )}
        </div>

        {/* Verification Records Table */}
        <div className="rounded-lg bg-white border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200 flex items-center justify-between">
            <div>
              <h3 className="text-sm font-bold text-slate-900">Cryptographic Verification Records</h3>
              <p className="text-xs text-slate-500">
                Audit trail of SHA-256 checks performed on transfers.
              </p>
            </div>
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200">
              {auditLogs.length} audit entries
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4">Filename</th>
                  <th className="py-3 px-4">Expected Hash</th>
                  <th className="py-3 px-4">Received Hash</th>
                  <th className="py-3 px-4">Outcome</th>
                  <th className="py-3 px-4">Algorithm</th>
                  <th className="py-3 px-4 text-right">Audit Time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700 font-mono">
                {loading ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-slate-400 font-sans">
                      Loading verification log...
                    </td>
                  </tr>
                ) : auditLogs.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-slate-400 font-sans">
                      No integrity check records logged yet.
                    </td>
                  </tr>
                ) : (
                  auditLogs.map((log) => {
                    const isVerified = log.verificationResult === 'VERIFIED';
                    return (
                      <tr key={log.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 px-4 font-sans text-slate-900 font-semibold">
                          {log.filename}
                        </td>
                        <td className="py-3 px-4 text-slate-500 text-[11px]" title={log.expectedHash}>
                          {log.expectedHash ? `${log.expectedHash.substring(0, 16)}...` : '--'}
                        </td>
                        <td className="py-3 px-4 text-slate-500 text-[11px]" title={log.actualHash}>
                          {log.actualHash ? `${log.actualHash.substring(0, 16)}...` : '--'}
                        </td>
                        <td className="py-3 px-4">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold border ${
                              isVerified
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                : 'bg-red-50 text-red-700 border-red-200'
                            }`}
                          >
                            {log.verificationResult}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-slate-600">
                          {log.algorithm || 'SHA-256'}
                        </td>
                        <td className="py-3 px-4 text-right text-slate-500 text-[11px]">
                          {new Date(log.verifiedAt).toLocaleString()}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
