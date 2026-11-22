'use client';

import { useState, useEffect } from 'react';
import {
  CheckCircle2,
  RefreshCw,
  Download,
  FileText,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet, BASE_URL } from '../../lib/api';

export default function ReceivePage() {
  const [transfers, setTransfers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchReceivedFiles = async () => {
    setRefreshing(true);
    try {
      const transList = await apiGet('/api/transfers').catch(() => []);
      setTransfers(transList || []);
    } catch (err) {
      console.error('Failed to load received transfers:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchReceivedFiles();
    const interval = setInterval(fetchReceivedFiles, 8000);
    return () => clearInterval(interval);
  }, []);

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
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

  const completedTransfers = transfers.filter((t) => t.transferStatus === 'completed');

  return (
    <AppShell pageTitle="Receive Files">
      <div className="space-y-6 max-w-5xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Received Files
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Verified files received from peer devices.
            </p>
          </div>
          <button
            onClick={fetchReceivedFiles}
            disabled={refreshing}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>

        {/* Received Files Table */}
        <div className="rounded-lg bg-white border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">Verified Transferred Files</h3>
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200">
              {completedTransfers.length} verified
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4">Filename</th>
                  <th className="py-3 px-4">Size</th>
                  <th className="py-3 px-4">Sender</th>
                  <th className="py-3 px-4">Integrity</th>
                  <th className="py-3 px-4">Time</th>
                  <th className="py-3 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {loading ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-slate-400">
                      Loading files...
                    </td>
                  </tr>
                ) : completedTransfers.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-slate-400">
                      No verified files received yet.
                    </td>
                  </tr>
                ) : (
                  completedTransfers.map((t) => (
                    <tr key={t.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3 px-4 font-semibold text-slate-900 flex items-center space-x-2">
                        <FileText className="w-4 h-4 text-blue-600 flex-shrink-0" />
                        <span className="truncate max-w-[200px]">{t.filename}</span>
                      </td>
                      <td className="py-3 px-4 text-slate-600 font-mono">
                        {formatFileSize(t.fileSize)}
                      </td>
                      <td className="py-3 px-4 text-slate-600">
                        {t.senderName || t.receiverIp || 'Peer'}
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 inline-flex items-center space-x-1">
                          <CheckCircle2 className="w-3 h-3" />
                          <span>Verified</span>
                        </span>
                      </td>
                      <td className="py-3 px-4 text-slate-500 font-mono text-[11px]">
                        {new Date(t.createdAt).toLocaleTimeString()}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => handleDownload(t.id, t.filename)}
                          className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-sm transition-colors"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>Download</span>
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
