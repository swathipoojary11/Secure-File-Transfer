/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,jsx}',
    './components/**/*.{js,jsx}',
    './lib/**/*.{js,jsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        navy: {
          950: '#060a12',
          900: '#0a101d',
          850: '#0e172a',
          800: '#132038',
          700: '#1b2d4f',
          600: '#27406e',
        },
        cyber: {
          cyan: '#06b6d4',
          blue: '#3b82f6',
          electric: '#38bdf8',
          emerald: '#10b981',
          crimson: '#ef4444',
          amber: '#f59e0b',
        },
      },
      fontFamily: {
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
      },
      borderRadius: {
        // Enforcing non-pill styling: buttons must be rounded-md or rounded-lg
        DEFAULT: '0.375rem',
      },
    },
  },
  plugins: [],
};
