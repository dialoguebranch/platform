import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

const packageJson = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'));

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  define: {
    __APP_VERSION__: JSON.stringify(packageJson.version),
  },
  build: {
    rollupOptions: {
      output: {
        // Keeps dlb-lib (see its own README on the original intent: a plain-JS client library
        // other JS projects could reuse, independent of this particular Vue app) in its own chunk,
        // separate from the rest of the app — its own file, cacheable independently of app changes.
        manualChunks(id) {
          if (id.includes('/src/dlb-lib/')) {
            return 'dlb-lib';
          }
        },
      },
    },
  },
  server: {
    // This app talks to the BFF only, same-origin, never to the Dialogue Branch Web Service or
    // Keycloak directly (see src/auth.js and src/dlb-lib/DialogueBranchClient.js) — the dev
    // server proxies every path the BFF owns so local development matches that in production.
    // Point VITE_BFF_TARGET at a different BFF instance (e.g. one deployed on Forge) to develop
    // against it instead of a local one.
    proxy: {
      '/api': { target: process.env.VITE_BFF_TARGET ?? 'http://localhost:8082', changeOrigin: true },
      '/oauth2': { target: process.env.VITE_BFF_TARGET ?? 'http://localhost:8082', changeOrigin: true },
      '/login': { target: process.env.VITE_BFF_TARGET ?? 'http://localhost:8082', changeOrigin: true },
      '/logout': { target: process.env.VITE_BFF_TARGET ?? 'http://localhost:8082', changeOrigin: true },
      '/whoami': { target: process.env.VITE_BFF_TARGET ?? 'http://localhost:8082', changeOrigin: true },
      '/actuator': { target: process.env.VITE_BFF_TARGET ?? 'http://localhost:8082', changeOrigin: true },
    },
  },
})
