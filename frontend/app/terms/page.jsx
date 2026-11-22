import Link from 'next/link';
import { ArrowLeft, FileText } from 'lucide-react';
import Navbar from '../../components/Navbar';
import Footer from '../../components/Footer';

export default function TermsPage() {
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
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-100 font-mono">Terms and Conditions</h1>
              <p className="text-xs text-slate-400 font-mono">Academic Project Usage Policy &amp; Security Protocol</p>
            </div>
          </div>

          <div className="space-y-6 text-sm text-slate-300 leading-relaxed font-sans">
            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">1. Authorized Use</h2>
              <p>
                This software is intended for educational demonstrations of computer networking protocols,
                secure sockets (JSSE), and cryptographic hashing. The simulation laboratory may only be operated
                on user-selected test artifacts within the sandboxed environment.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">2. Network Security Notice</h2>
              <p>
                Participating devices must ensure their local firewalls permit inbound TCP traffic on designated
                transfer ports (e.g., port 8443) only for authorized peers on the same local subnet.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-slate-100 font-mono mb-2">3. Integrity Verification Protocol</h2>
              <p>
                Files received whose SHA-256 digest fails to match the sender-authenticated digest are automatically
                quarantined and purged from temporary memory, triggering an immutable security event log.
              </p>
            </section>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}
