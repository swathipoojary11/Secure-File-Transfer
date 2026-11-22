export default function Footer() {
  return (
    <footer className="w-full border-t border-slate-200 bg-white mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500">
        <div>
          Data Communication &amp; Networks (DCN) Academic Mini-Project
        </div>
        <div className="font-mono text-[11px] text-slate-400">
          TLS 1.3 (Port 8443) · SHA-256 · Java 21 · Next.js
        </div>
      </div>
    </footer>
  );
}
