'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  Shield,
  UploadCloud,
  CheckCircle2,
  AlertTriangle,
  ArrowUpRight,
  Activity,
  HardDrive,
  FileCheck,
  RefreshCw,
  Search,
  Zap,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet } from '../../lib/api';

export default function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [securityStatus, setSecurityStatus] = useState(null);
  const [transfers, setTransfers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  const fetchDashboardData = async () => {
    try {
      setRefreshing(true);
      const [sumData, secData, transData] = await Promise.all([
        apiGet('/api/analytics/summary').catch(() => null),
        apiGet('/api/security/status').catch(() => null),
        apiGet('/api/transfers').catch(() => []),
      ]);

      setSummary(sumData);
      setSecurityStatus(secData);
      setTransfers(transData || []);
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
    const interval = setInterval(fetchDashboardData, 10000);
    return () => clearInterval(interval);
  }, []);

  const filteredTransfers = transfers.filter((t) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      t.filename?.toLowerCase().includes(q) ||
      t.receiverName?.toLowerCase().includes(q) ||
      t.originalHash?.toLowerCase().includes(q)
    );
  });

  const hasCriticalAlerts =
    (securityStatus?.criticalViolations || 0) > 0 ||
    (summary?.integrityVerificationFailures || 0) > 0;

  return (
    <AppShell pageTitle="Dashboard">
      <div className="space-y-6 max-w-7xl mx-auto">
        {/* Header & Actions */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-3 border-b border-slate-200">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Dashboard
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Overview of network transfers and status.
            </p>
          </div>
          <div className="flex items-center space-x-2">
            <button
              onClick={fetchDashboardData}
              disabled={refreshing}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
            <Link
              href="/send"
              className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white font-medium text-xs transition-colors shadow-sm"
            >
              <UploadCloud className="w-3.5 h-3.5" />
              <span>Send File</span>
            </Link>
          </div>
        </div>

        {/* Security Alert if Tampering Occurred */}
        {hasCriticalAlerts && (
          <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-800 flex items-start space-x-3">
            <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5 text-red-600" />
            <div className="flex-1 text-xs">
              <div className="font-bold text-sm text-red-900">Cryptographic Integrity Alert: Tampering Detected</div>
              <div className="mt-1 text-red-700">
                A file transmitted across the network failed SHA-256 hash validation.
                The receiving agent purged the compromised payload to maintain system integrity.
              </div>
              <div className="mt-2">
                <Link
                  href="/security"
                  className="inline-flex items-center space-x-1 underline font-semibold text-red-900 hover:text-red-700"
                >
                  <span>View in Security Center</span>
                  <ArrowUpRight className="w-3.5 h-3.5" />
                </Link>
              </div>
            </div>
          </div>
        )}

        {/* Real KPI Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* 1. Total Transfers */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Total Transfers</span>
              <Activity className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {loading ? '--' : summary?.totalTransfers || 0}
            </div>
            <div className="mt-2 text-xs text-slate-500 flex items-center space-x-2">
              <span className="text-emerald-600 font-semibold">
                {summary?.successfulTransfers || 0} completed
              </span>
              <span>•</span>
              <span className="text-red-600 font-semibold">
                {summary?.failedTransfers || 0} failed
              </span>
            </div>
          </div>

          {/* 2. Total Data Transferred */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Data Transferred</span>
              <HardDrive className="w-4 h-4 text-indigo-600" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {loading ? '--' : summary?.totalDataTransferredFormatted || '0 B'}
            </div>
            <div className="mt-2 text-xs text-slate-500 font-mono">
              {summary?.totalFilesTransferred || 0} files processed
            </div>
          </div>

          {/* 3. Average Speed */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Average Throughput</span>
              <Zap className="w-4 h-4 text-amber-500" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {loading
                ? '--'
                : `${(summary?.averageTransferSpeedKbps || 0).toLocaleString(undefined, {
                    maximumFractionDigits: 1,
                  })} KB/s`}
            </div>
            <div className="mt-2 text-xs text-slate-500">
              64 KB Streaming Buffer
            </div>
          </div>

          {/* 4. Integrity Status */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Integrity Verification</span>
              <FileCheck className="w-4 h-4 text-emerald-600" />
            </div>
            <div className={`text-2xl font-bold font-mono ${(summary?.integrityVerificationFailures || 0) === 0 ? 'text-emerald-600' : 'text-red-600'}`}>
              {loading
                ? '--'
                : (summary?.integrityVerificationFailures || 0) === 0
                ? 'VERIFIED'
                : `${summary?.integrityVerificationFailures} FAILED`}
            </div>
            <div className="mt-2 text-xs text-slate-500 font-mono">
              SHA-256 Constant-Time Check
            </div>
          </div>
        </div>

        {/* Recent Transfer Activity Table */}
        <div className="rounded-lg bg-white border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-bold text-slate-900">Recent File Transfers</h3>
              <p className="text-xs text-slate-500">Live records from the transfer log.</p>
            </div>
            <div className="relative w-full sm:w-64">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search filename or hash..."
                className="w-full pl-9 pr-3 py-1.5 rounded-md bg-slate-50 border border-slate-300 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4">Filename</th>
                  <th className="py-3 px-4">Size</th>
                  <th className="py-3 px-4">Peer Device</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Speed</th>
                  <th className="py-3 px-4">SHA-256 Digest</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700 font-mono">
                {loading ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-slate-400 font-sans">
                      Loading transfer data...
                    </td>
                  </tr>
                ) : filteredTransfers.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-slate-400 font-sans">
                      No transfer activity found. Click &quot;Send File&quot; above to perform your first transfer.
                    </td>
                  </tr>
                ) : (
                  filteredTransfers.map((t) => {
                    const isSuccess = t.transferStatus === 'completed';
                    const isFailed = t.transferStatus === 'failed';
                    return (
                      <tr key={t.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 px-4 font-semibold text-slate-900">
                          {t.filename}
                        </td>
                        <td className="py-3 px-4 text-slate-600">
                          {t.fileSizeFormatted || `${(t.fileSize / 1024).toFixed(1)} KB`}
                        </td>
                        <td className="py-3 px-4 text-slate-600 font-sans">
                          {t.receiverName || t.receiverIp || '127.0.0.1:8443'}
                        </td>
                        <td className="py-3 px-4">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${
                              isSuccess
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                : isFailed
                                ? 'bg-red-50 text-red-700 border-red-200'
                                : 'bg-blue-50 text-blue-700 border-blue-200'
                            }`}
                          >
                            {t.transferStatus?.toUpperCase()}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-slate-600">
                          {t.transferSpeedKbps ? `${Math.round(t.transferSpeedKbps)} KB/s` : '--'}
                        </td>
                        <td className="py-3 px-4 text-slate-500 text-[11px]" title={t.originalHash}>
                          {t.originalHash ? `${t.originalHash.substring(0, 16)}...` : '--'}
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
