import './globals.css';

export const metadata = {
  title: 'Secure File Transfer | DCN Mini-Project',
  description: 'Academic Data Communication & Networks project implementing TLS 1.3 socket transfer, SHA-256 integrity verification, and AES-256-GCM file encryption.',
  icons: {
    icon: '/favicon.svg',
  },
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <head>
        <link rel="icon" href="/favicon.svg" type="image/svg+xml" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </head>
      <body className="bg-slate-50 text-slate-900 min-h-screen flex flex-col font-sans antialiased">
        {children}
      </body>
    </html>
  );
}
