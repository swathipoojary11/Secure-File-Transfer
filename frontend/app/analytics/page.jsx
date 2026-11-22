'use client';

import { useState, useEffect } from 'react';
import {
  TrendingUp,
  HardDrive,
  Zap,
  CheckCircle2,
  RefreshCw,
  PieChart as PieIcon,
  Server,
  ShieldAlert,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import AppShell from '../../components/AppShell';
import { apiGet } from '../../lib/api';

const STATUS_COLORS = {
  Completed: '#10b981', // emerald
  'In Progress': '#3b82f6', // blue
  Failed: '#ef4444', // red
  Cancelled: '#f59e0b', // amber
};

const SEVERITY_COLORS = {
  LOW: '#64748b',
  MEDIUM: '#0284c7',
  HIGH: '#d97706',
  CRITICAL: '#dc2626',
};

export default function AnalyticsPage() {
  const [summary, setSummary] = useState(null);
  const [transferMetrics, setTransferMetrics] = useState(null);
  const [securityMetrics, setSecurityMetrics] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchAnalytics = async () => {
    try {
      setLoading(true);
      const [sumData, transData, secData] = await Promise.all([
        apiGet('/api/analytics/summary').catch(() => null),
        apiGet('/api/analytics/transfers').catch(() => null),
        apiGet('/api/analytics/security').catch(() => null),
      ]);

      setSummary(sumData);
      setTransferMetrics(transData);
      setSecurityMetrics(secData);
    } catch (err) {
      console.error('Failed to load analytics:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAnalytics();
  }, []);

  // Format real speed data
  const speedChartData = (summary?.speedHistory || []).map((pt, idx) => ({
    name: pt.filename?.length > 12 ? `${pt.filename.substring(0, 10)}...` : pt.filename || `File ${idx + 1}`,
    speed: Math.round(pt.speedKbps || 0),
    status: pt.status,
  }));

  // Real status breakdown data
  const statusData = (transferMetrics?.statusBreakdown || []).filter((d) => d.value > 0);

  // Real severity breakdown data
  const severityData = (securityMetrics?.severityBreakdown || []).map((s) => ({
    severity: s.severity,
    count: s.count,
  }));

  return (
    <AppShell pageTitle="Analytics">
      <div className="space-y-6 max-w-6xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Analytics
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Bandwidth, success rates, and performance metrics.
            </p>
          </div>
          <button
            onClick={fetchAnalytics}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Update</span>
          </button>
        </div>

        {/* High-Level KPI Summary */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Total Volume Transferred</span>
              <HardDrive className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {summary?.totalDataTransferredFormatted || '0 B'}
            </div>
            <div className="text-xs text-slate-500 mt-2 font-mono">
              {summary?.totalFilesTransferred || 0} files completed
            </div>
          </div>

          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Average Transfer Speed</span>
              <Zap className="w-4 h-4 text-amber-500" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {(summary?.averageTransferSpeedKbps || 0).toLocaleString(undefined, {
                maximumFractionDigits: 1,
              })}{' '}
              KB/s
            </div>
            <div className="text-xs text-slate-500 mt-2">
              Streaming throughput over TCP
            </div>
          </div>

          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Success Ratio</span>
              <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            </div>
            <div className="text-2xl font-bold font-mono text-emerald-600">
              {summary?.totalTransfers > 0
                ? `${Math.round(((summary.successfulTransfers || 0) / summary.totalTransfers) * 100)}%`
                : '100%'}
            </div>
            <div className="text-xs text-slate-500 mt-2">
              {summary?.failedTransfers || 0} failed attempts
            </div>
          </div>

          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Active Network Nodes</span>
              <Server className="w-4 h-4 text-indigo-600" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {summary?.activeDevicesCount || 1}
            </div>
            <div className="text-xs text-slate-500 mt-2">
              Registered peer devices
            </div>
          </div>
        </div>

        {/* Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Chart 1: Throughput History */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
                <TrendingUp className="w-4 h-4 text-blue-600" />
                <span>Transfer Throughput (KB/s)</span>
              </div>
              <span className="text-xs text-slate-400 font-mono">LAN Speed</span>
            </div>

            <div className="h-64 w-full">
              {speedChartData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={speedChartData}>
                    <defs>
                      <linearGradient id="speedGradLight" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="name" stroke="#64748b" tick={{ fontSize: 11 }} />
                    <YAxis stroke="#64748b" tick={{ fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#ffffff',
                        borderColor: '#cbd5e1',
                        borderRadius: '0.375rem',
                        fontSize: '12px',
                        color: '#0f172a',
                        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="speed"
                      name="Speed (KB/s)"
                      stroke="#2563eb"
                      strokeWidth={2}
                      fillOpacity={1}
                      fill="url(#speedGradLight)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-400">
                  No throughput records yet. Perform a file transfer to populate this chart.
                </div>
              )}
            </div>
          </div>

          {/* Chart 2: Status Breakdown */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
                <PieIcon className="w-4 h-4 text-emerald-600" />
                <span>Transfer Outcome Distribution</span>
              </div>
              <span className="text-xs text-slate-400 font-mono">Status Breakdown</span>
            </div>

            <div className="h-64 w-full">
              {statusData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={statusData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      innerRadius={55}
                      outerRadius={80}
                      paddingAngle={4}
                      label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                    >
                      {statusData.map((entry) => (
                        <Cell
                          key={entry.name}
                          fill={STATUS_COLORS[entry.name] || '#64748b'}
                        />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#ffffff',
                        borderColor: '#cbd5e1',
                        borderRadius: '0.375rem',
                        fontSize: '12px',
                        color: '#0f172a',
                      }}
                    />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-400">
                  No transfer status distribution yet.
                </div>
              )}
            </div>
          </div>

          {/* Chart 3: Security Severity */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm space-y-4 lg:col-span-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2 text-slate-900 font-bold text-sm">
                <ShieldAlert className="w-4 h-4 text-red-600" />
                <span>Security Events by Severity Level</span>
              </div>
              <span className="text-xs text-slate-400 font-mono">Incident Classification</span>
            </div>

            <div className="h-64 w-full">
              {severityData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={severityData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="severity" stroke="#64748b" tick={{ fontSize: 11 }} />
                    <YAxis stroke="#64748b" tick={{ fontSize: 11 }} allowDecimals={false} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#ffffff',
                        borderColor: '#cbd5e1',
                        borderRadius: '0.375rem',
                        fontSize: '12px',
                        color: '#0f172a',
                      }}
                    />
                    <Bar dataKey="count" name="Incidents" radius={[4, 4, 0, 0]}>
                      {severityData.map((entry) => (
                        <Cell
                          key={entry.severity}
                          fill={SEVERITY_COLORS[entry.severity] || '#3b82f6'}
                        />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-400">
                  No security incidents recorded. System running normally.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
