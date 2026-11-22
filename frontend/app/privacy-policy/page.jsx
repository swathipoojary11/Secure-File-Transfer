import Link from 'next/link';
import { ArrowLeft, Shield } from 'lucide-react';
import Navbar from '../../components/Navbar';
import Footer from '../../components/Footer';

export default function PrivacyPolicyPage() {
  return (
    <div className="min-h-screen flex flex-col bg-navy-950">
      <Navbar />

      <main className="flex-1 max-w-4xl w-full mx-auto px-4 sm:px-6 py-12">
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 text-xs font-mono text-cyber-cyan hover:text-cyan-300 mb-6"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          Back to Overview
        </Link>

        <div className="border border-navy-800 rounded-lg p-6 sm:p-10 bg-navy-900/60">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-md bg-navy-950 border border-cyber-cyan/30 flex items-center justify-center text-cyber-cyan">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-100 font-mono">Privacy Policy</h1>
              <p className="text-xs text-slate-400 font-mono">Academic Project Compliance &amp; Data Handling</p>
            </div>
          </div>

          <div className="space-y-6 text-sm text-slate-300 leading-relaxed font-sans">
            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">1. Scope of the System</h2>
              <p>
                This application is developed strictly for academic demonstration in the Data Communication &amp;
                Networks (DCN) curriculum. It provides peer-to-peer file transfer over local Wi-Fi or LAN networks
                using direct TCP/TLS sockets.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">2. Data Transmission &amp; Storage</h2>
              <p>
                Transferred file contents are transmitted directly peer-to-peer between participating Java transfer
                agents. File payloads are never stored on external third-party servers. All transferred files remain on
                the local hard drives of the sending and receiving devices.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">3. Cryptographic Hashes &amp; Logs</h2>
              <p>
                To provide verifiable non-repudiation and integrity verification, cryptographic SHA-256 digests,
                transfer timestamps, file names, file sizes, and device IP addresses are recorded in PostgreSQL
                protected by Supabase Row Level Security (RLS). Users may only access transfer records where they are
                authenticated as the sender or receiver.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">4. Cryptographic Standards</h2>
              <p>
                In-transit data is protected using Transport Layer Security (TLS 1.3, RFC 8446) with authenticated
                cipher suites. File-level confidentiality is provided via AES-256-GCM authenticated encryption with
                ephemeral nonces.
              </p>
            </section>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
