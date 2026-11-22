'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  Search,
  CheckCircle2,
  AlertTriangle,
  Download,
  Copy,
  ArrowUpDown,
  RefreshCw,
  FileText,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet, BASE_URL } from '../../lib/api';

export default function HistoryPage() {
  const [transfers, setTransfers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortOrder, setSortOrder] = useState('DESC');
  const [copiedId, setCopiedId] = useState(null);

  const fetchHistory = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams();
      if (search) params.append('search', search);
      if (statusFilter !== 'ALL') params.append('status', statusFilter.toLowerCase());
      params.append('sort', sortOrder);

      const data = await apiGet(`/api/transfers?${params.toString()}`);
      setTransfers(data || []);
    } catch (err) {
      console.error('Failed to load history:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, [statusFilter, sortOrder]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    fetchHistory();
  };

  const copyToClipboard = (text, id) => {
    if (typeof navigator !== 'undefined') {
      navigator.clipboard.writeText(text);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    }
  };

  const handleDownload = (transferId, filename) => {
    const downloadUrl = `${BASE_URL}/api/transfers/${transferId}/download`;
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.download = filename || 'downloaded-file';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
  };

  return (
    <AppShell pageTitle="Transfer History">
      <div className="space-y-6 max-w-6xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Transfer History
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Logs of sent and received files.
            </p>
          </div>
          <button
            onClick={fetchHistory}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Reload</span>
          </button>
        </div>

        {/* Filter & Search Bar */}
        <div className="rounded-lg bg-white border border-slate-200 p-4 shadow-sm flex flex-col md:flex-row gap-3 items-center justify-between">
          <form onSubmit={handleSearchSubmit} className="relative w-full md:w-80">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search filename or hash..."
              className="w-full pl-9 pr-3 py-1.5 rounded-md bg-slate-50 border border-slate-300 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            />
          </form>

          <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
            {/* Status Tabs */}
            <div className="flex items-center space-x-1 bg-slate-100 p-1 rounded-md border border-slate-200 text-xs">
              {['ALL', 'COMPLETED', 'FAILED', 'CANCELLED'].map((st) => (
                <button
                  key={st}
                  onClick={() => setStatusFilter(st)}
                  className={`px-2.5 py-1 rounded text-xs font-semibold transition-colors ${
                    statusFilter === st
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>

            {/* Sort Toggle */}
            <button
              onClick={() => setSortOrder(sortOrder === 'DESC' ? 'ASC' : 'DESC')}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
            >
              <ArrowUpDown className="w-3.5 h-3.5" />
              <span>{sortOrder === 'DESC' ? 'Newest' : 'Oldest'}</span>
            </button>
          </div>
        </div>

        {/* Audit Table */}
        <div className="rounded-lg bg-white border border-slate-200 shadow-sm overflow-hidden">
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
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700 font-mono">
                {loading ? (
                  <tr>
                    <td colSpan="7" className="py-8 text-center text-slate-400 font-sans">
                      Loading transfer records...
                    </td>
                  </tr>
                ) : transfers.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="py-8 text-center text-slate-400 font-sans">
                      No transfer history found. Transmit a file from the Send page to see records.
                    </td>
                  </tr>
                ) : (
                  transfers.map((t) => {
                    const isSuccess = t.transferStatus === 'completed';
                    const isFailed = t.transferStatus === 'failed';

                    return (
                      <tr key={t.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 px-4 font-sans text-slate-900 font-semibold flex items-center space-x-2">
                          <FileText className="w-4 h-4 text-blue-600 flex-shrink-0" />
                          <span className="truncate max-w-[160px]">{t.filename}</span>
                        </td>

                        <td className="py-3 px-4 text-slate-600">
                          {formatFileSize(t.fileSize)}
                        </td>

                        <td className="py-3 px-4 text-slate-600 font-sans">
                          {t.receiverName || t.receiverIp || '127.0.0.1:8443'}
                        </td>

                        <td className="py-3 px-4">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold border ${
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
                          {t.transferSpeed > 0 ? `${t.transferSpeed.toFixed(1)} KB/s` : '--'}
                        </td>

                        <td className="py-3 px-4 text-slate-500 text-[11px]" title={t.originalHash}>
                          {t.originalHash ? `${t.originalHash.substring(0, 16)}...` : '--'}
                        </td>

                        <td className="py-3 px-4 text-right font-sans">
                          {isSuccess && (
                            <button
                              onClick={() => handleDownload(t.id, t.filename)}
                              className="inline-flex items-center space-x-1 px-2.5 py-1 rounded bg-blue-50 hover:bg-blue-100 text-blue-700 font-medium text-xs border border-blue-200 transition-colors"
                            >
                              <Download className="w-3.5 h-3.5" />
                              <span>Download</span>
                            </button>
                          )}
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
