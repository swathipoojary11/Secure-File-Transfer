'use client';

import { useState, useEffect } from 'react';
import {
  Laptop,
  Plus,
  Trash2,
  RefreshCw,
  Zap,
} from 'lucide-react';
import AppShell from '../../components/AppShell';
import { apiGet, apiPost, apiDelete } from '../../lib/api';

export default function DevicesPage() {
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionMsg, setActionMsg] = useState(null);

  // New device form state
  const [showAddForm, setShowAddForm] = useState(false);
  const [deviceName, setDeviceName] = useState('');
  const [deviceIp, setDeviceIp] = useState('192.168.1.');
  const [listeningPort, setListeningPort] = useState(8443);
  const [registering, setRegistering] = useState(false);
  const [pingingId, setPingingId] = useState(null);

  const fetchDevices = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiGet('/api/devices');
      setDevices(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDevices();
  }, []);

  const handleRegister = async (e) => {
    e.preventDefault();
    setRegistering(true);
    setActionMsg(null);

    try {
      await apiPost('/api/devices/register', {
        deviceName,
        deviceIp,
        listeningPort: parseInt(listeningPort, 10),
      });

      setActionMsg(`Device "${deviceName}" registered successfully.`);
      setShowAddForm(false);
      setDeviceName('');
      setDeviceIp('192.168.1.');
      fetchDevices();
    } catch (err) {
      setActionMsg(`Error: ${err.message}`);
    } finally {
      setRegistering(false);
    }
  };

  const handlePing = async (id, name) => {
    setPingingId(id);
    setActionMsg(null);
    try {
      const data = await apiGet(`/api/devices/${id}/status`);
      if (data.reachable) {
        setActionMsg(`Node "${name}" is ONLINE (${data.pingDurationMs} ms round-trip latency).`);
      } else {
        setActionMsg(`Node "${name}" is OFFLINE (Port ${data.listeningPort} unreachable).`);
      }
      fetchDevices();
    } catch (err) {
      setActionMsg(`Ping failed for "${name}": ${err.message}`);
    } finally {
      setPingingId(null);
    }
  };

  const handleDelete = async (id, name) => {
    if (!confirm(`Are you sure you want to remove device "${name}"?`)) return;
    try {
      await apiDelete(`/api/devices/${id}`);
      setActionMsg(`Device "${name}" removed.`);
      fetchDevices();
    } catch (err) {
      setActionMsg(`Failed to delete: ${err.message}`);
    }
  };

  return (
    <AppShell pageTitle="Connected Devices">
      <div className="space-y-6 max-w-5xl mx-auto">
        {/* Header */}
        <div className="pb-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              Connected Devices
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Peer laptops in the transfer network.
            </p>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={fetchDevices}
              disabled={loading}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium transition-colors shadow-sm"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>

            <button
              onClick={() => setShowAddForm(!showAddForm)}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>Add Peer Device</span>
            </button>
          </div>
        </div>

        {/* Action / Notification Message */}
        {actionMsg && (
          <div className="p-3.5 rounded-md bg-blue-50 border border-blue-200 text-blue-900 text-xs flex items-center justify-between">
            <span>{actionMsg}</span>
            <button onClick={() => setActionMsg(null)} className="text-blue-500 hover:text-blue-700 font-bold">
              ×
            </button>
          </div>
        )}

        {/* Add Device Form Card */}
        {showAddForm && (
          <div className="p-5 rounded-lg bg-white border border-slate-200 shadow-sm space-y-4">
            <h3 className="text-sm font-bold text-slate-900">Register Peer Device</h3>
            <form onSubmit={handleRegister} className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
              <div>
                <label className="block text-slate-700 text-xs mb-1 font-medium">Device Name</label>
                <input
                  type="text"
                  required
                  value={deviceName}
                  onChange={(e) => setDeviceName(e.target.value)}
                  placeholder="Laptop 2 (Receiver)"
                  className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-slate-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-slate-700 text-xs mb-1 font-medium">IPv4 Address (LAN/Wi-Fi)</label>
                <input
                  type="text"
                  required
                  value={deviceIp}
                  onChange={(e) => setDeviceIp(e.target.value)}
                  placeholder="192.168.1.15"
                  className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-slate-900 focus:outline-none focus:ring-1 focus:ring-blue-500 font-mono"
                />
              </div>

              <div>
                <label className="block text-slate-700 text-xs mb-1 font-medium">Listening Port</label>
                <input
                  type="number"
                  required
                  value={listeningPort}
                  onChange={(e) => setListeningPort(e.target.value)}
                  className="w-full px-3 py-2 rounded-md bg-white border border-slate-300 text-slate-900 focus:outline-none focus:ring-1 focus:ring-blue-500 font-mono"
                />
              </div>

              <div className="sm:col-span-3 flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddForm(false)}
                  className="px-3 py-1.5 rounded-md bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 text-xs font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={registering}
                  className="px-4 py-1.5 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-sm"
                >
                  {registering ? 'Saving...' : 'Save Device'}
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Devices Table */}
        <div className="rounded-lg bg-white border border-slate-200 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4">Device Name</th>
                  <th className="py-3 px-4">IPv4 Address</th>
                  <th className="py-3 px-4">Port</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700 font-mono">
                {loading ? (
                  <tr>
                    <td colSpan="5" className="py-8 text-center text-slate-400 font-sans">
                      Loading registered devices...
                    </td>
                  </tr>
                ) : devices.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="py-8 text-center text-slate-400 font-sans">
                      No devices registered. Click &quot;Add Peer Device&quot; to configure a target machine.
                    </td>
                  </tr>
                ) : (
                  devices.map((d) => {
                    const isOnline = d.status === 'online';
                    return (
                      <tr key={d.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 px-4 font-sans font-semibold text-slate-900 flex items-center space-x-2">
                          <Laptop className="w-4 h-4 text-blue-600 flex-shrink-0" />
                          <span>{d.deviceName}</span>
                        </td>
                        <td className="py-3 px-4 text-slate-800">
                          {d.deviceIp}
                        </td>
                        <td className="py-3 px-4 text-slate-600">
                          {d.listeningPort}
                        </td>
                        <td className="py-3 px-4 font-sans">
                          <span
                            className={`inline-flex items-center space-x-1 px-2 py-0.5 rounded text-[10px] font-semibold border ${
                              isOnline
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                : 'bg-slate-100 text-slate-600 border-slate-200'
                            }`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full ${isOnline ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                            <span>{isOnline ? 'ONLINE' : 'OFFLINE'}</span>
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right space-x-2 font-sans">
                          <button
                            onClick={() => handlePing(d.id, d.deviceName)}
                            disabled={pingingId === d.id}
                            className="inline-flex items-center space-x-1 px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs border border-slate-200 transition-colors"
                          >
                            <Zap className="w-3 h-3 text-amber-500" />
                            <span>{pingingId === d.id ? 'Pinging...' : 'Ping'}</span>
                          </button>
                          <button
                            onClick={() => handleDelete(d.id, d.deviceName)}
                            className="p-1 rounded text-slate-400 hover:text-red-600 transition-colors"
                            title="Remove Device"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
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
