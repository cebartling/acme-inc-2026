// Dedicated Vitest config so unit tests don't pay for the heavy dev plugins
// (devtools / nitro / tanstackStart) that vite.config.ts loads for `vite dev`.
// Those plugins start their own file watchers and were the cause of ~90
// hanging FILEHANDLEs after each test run.
import { defineConfig } from 'vitest/config'
import viteReact from '@vitejs/plugin-react'
import viteTsConfigPaths from 'vite-tsconfig-paths'

export default defineConfig({
  plugins: [
    viteTsConfigPaths({ projects: ['./tsconfig.json'] }),
    viteReact(),
  ],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{js,ts,jsx,tsx}'],
    reporters: ['default', 'hanging-process'],
    server: {
      deps: {
        inline: ['@radix-ui', 'lucide-react'],
      },
    },
    alias: {
      '@/': new URL('./src/', import.meta.url).pathname,
    },
    deps: {
      optimizer: {
        web: {
          include: ['react', 'react-dom', 'react-dom/client'],
        },
      },
    },
  },
})
