import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The proxy keeps cookies same-origin so sessions and CSRF work in development.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': process.env.VITE_API_TARGET ?? 'http://localhost:8080' } },
})
