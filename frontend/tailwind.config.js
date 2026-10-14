/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Wayfinding-signage palette: deep navy ink, cool paper, call-light amber
        ink: { DEFAULT: '#0B2545', 700: '#1B3A63', 500: '#4A6285', 400: '#6B7F9C', 300: '#9AA9BF' },
        surface: '#FAFBFC',
        paper: { DEFAULT: '#EEF2F6', 100: '#F6F8FA', 200: '#E2E8EF', 300: '#CFD8E3' },
        signal: { DEFAULT: '#FFB703', 600: '#E5A100', 100: '#FFF3CC' },
        go: { DEFAULT: '#14866D', 100: '#DDF3EC' },
        stop: { DEFAULT: '#C8344B', 100: '#FBE3E7' },
        info: { DEFAULT: '#2F6FDE', 100: '#E1ECFD' },
      },
      fontFamily: {
        sans: ['Barlow', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"Barlow Condensed"', 'Barlow', 'ui-sans-serif', 'sans-serif'],
      },
      boxShadow: {
        ticket: '0 1px 0 rgba(11,37,69,.06), 0 10px 24px -12px rgba(11,37,69,.28)',
        lift: '0 1px 2px rgba(11,37,69,.08)',
      },
      keyframes: {
        callpulse: {
          '0%,100%': { boxShadow: '0 0 0 0 rgba(255,183,3,.55)' },
          '50%': { boxShadow: '0 0 0 14px rgba(255,183,3,0)' },
        },
        slidein: {
          from: { transform: 'translateY(8px)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' },
        },
      },
      animation: {
        callpulse: 'callpulse 1.8s ease-in-out infinite',
        slidein: 'slidein .22s ease-out',
      },
    },
  },
  plugins: [],
};
