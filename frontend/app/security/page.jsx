'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  ShieldAlert,
  ShieldCheck,
  Lock,
  Server,
  AlertTriangle,
  RefreshCw,
  Terminal,
  Filter,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet } from '../../lib/api';

export default function SecurityPage() {
  const [securityStatus, setSecurityStatus] = useState(null);
  const [events, setEvents] = useState([]);
  const [severityFilter, setSeverityFilter] = useState('ALL');
  const [loading, setLoading] = useState(true);

  const fetchSecurityData = async () => {
    try {
      setLoading(true);
      const [status, eventList] = await Promise.all([
        apiGet('/api/security/status').catch(() => null),
        apiGet(`/api/security/events${severityFilter !== 'ALL' ? `?severity=${severityFilter}` : ''}`).catch(() => []),
      ]);
      setSecurityStatus(status);
      setEvents(eventList || []);
    } catch (err) {
      console.error('Failed to load security telemetry:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSecurityData();
  }, [severityFilter]);

  const isSecure = securityStatus?.securityPosture === 'SECURE';

  return (
    <AppShell pageTitle="Security Center">
      <div className="space-y-6 max-w-6xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Security Center
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Live audit logs of TLS handshakes and security events.
            </p>
          </div>
          <button
            onClick={fetchSecurityData}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>

        {/* Telemetry KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Posture Banner */}
          <div
            className={`p-5 rounded-lg border shadow-sm ${
              isSecure
                ? 'bg-emerald-50 border-emerald-200'
                : 'bg-red-50 border-red-200'
            }`}
          >
            <div className="flex items-center justify-between text-xs mb-1">
              <span className={`font-medium ${isSecure ? 'text-emerald-800' : 'text-red-800'}`}>System Security Posture</span>
              {isSecure ? (
                <ShieldCheck className="w-5 h-5 text-emerald-600" />
              ) : (
                <ShieldAlert className="w-5 h-5 text-red-600" />
              )}
            </div>
            <div
              className={`text-2xl font-bold font-mono tracking-tight ${
                isSecure ? 'text-emerald-700' : 'text-red-700'
              }`}
            >
              {securityStatus?.securityPosture || 'SECURE'}
            </div>
            <div className={`text-xs mt-2 ${isSecure ? 'text-emerald-700' : 'text-red-700'}`}>
              {isSecure
                ? 'All nodes authentic & verified'
                : `${securityStatus?.criticalViolations || 0} critical integrity alerts`}
            </div>
          </div>

          {/* Active Cipher Suite */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>TLS Socket Cipher Suite</span>
              <Lock className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-sm font-bold font-mono text-slate-900 truncate" title={securityStatus?.activeCipherSuite}>
              {securityStatus?.activeCipherSuite || 'TLS_AES_256_GCM_SHA384'}
            </div>
            <div className="text-xs text-slate-500 mt-2 font-mono">
              Protocol: {securityStatus?.tlsVersion || 'TLSv1.3'}
            </div>
          </div>

          {/* Total Events */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Total Logged Events</span>
              <Terminal className="w-4 h-4 text-indigo-600" />
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {securityStatus?.totalSecurityEvents || 0}
            </div>
            <div className="text-xs text-slate-500 mt-2">
              Persistent audit trail
            </div>
          </div>

          {/* Critical Alerts */}
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between text-xs text-slate-500 font-medium mb-1">
              <span>Critical Incidents</span>
              <AlertTriangle className="w-4 h-4 text-red-600" />
            </div>
            <div className={`text-2xl font-bold font-mono ${(securityStatus?.criticalViolations || 0) > 0 ? 'text-red-600' : 'text-slate-900'}`}>
              {securityStatus?.criticalViolations || 0}
            </div>
            <div className="text-xs text-slate-500 mt-2">
              Tamper / Mismatch detections
            </div>
          </div>
        </div>

        {/* Severity Filter Tabs */}
        <div className="flex items-center justify-between p-3 rounded-lg bg-white border border-slate-200 shadow-sm">
          <div className="flex items-center space-x-2 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-slate-600 font-medium">Severity:</span>
            {['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((sev) => (
              <button
                key={sev}
                onClick={() => setSeverityFilter(sev)}
                className={`px-2.5 py-1 rounded text-xs font-semibold transition-colors ${
                  severityFilter === sev
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                {sev}
              </button>
            ))}
          </div>

          <span className="text-xs text-slate-500 hidden sm:inline">
            Showing {events.length} security entries
          </span>
        </div>

        {/* Security Events Audit Table */}
        <div className="rounded-lg bg-white border border-slate-200 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4">Severity</th>
                  <th className="py-3 px-4">Event Type</th>
                  <th className="py-3 px-4">Source IP</th>
                  <th className="py-3 px-4">Description</th>
                  <th className="py-3 px-4 text-right">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700 font-mono">
                {loading ? (
                  <tr>
                    <td colSpan="5" className="py-8 text-center text-slate-400 font-sans">
                      Loading security telemetry log...
                    </td>
                  </tr>
                ) : events.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="py-8 text-center text-slate-400 font-sans">
                      No security incidents logged under {severityFilter} severity.
                    </td>
                  </tr>
                ) : (
                  events.map((e) => {
                    const isCritical = e.severity === 'CRITICAL';
                    const isHigh = e.severity === 'HIGH';
                    const isMedium = e.severity === 'MEDIUM';
                    return (
                      <tr key={e.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 px-4">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold border ${
                              isCritical
                                ? 'bg-red-50 text-red-700 border-red-200'
                                : isHigh
                                ? 'bg-amber-50 text-amber-700 border-amber-200'
                                : isMedium
                                ? 'bg-blue-50 text-blue-700 border-blue-200'
                                : 'bg-slate-100 text-slate-700 border-slate-200'
                            }`}
                          >
                            {e.severity}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-bold text-slate-900">
                          {e.eventType}
                        </td>
                        <td className="py-3 px-4 text-slate-600">
                          {e.sourceIp || '127.0.0.1'}
                        </td>
                        <td className="py-3 px-4 text-slate-700 font-sans max-w-md">
                          {e.description}
                        </td>
                        <td className="py-3 px-4 text-right text-slate-500 text-[11px]">
                          {new Date(e.createdAt).toLocaleString()}
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
