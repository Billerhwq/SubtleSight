import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
    plugins: [react()],
    server: { port: 5173, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false } } },
    build: { target: 'es2022', sourcemap: true },
    test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], coverage: { provider: 'v8', reporter: ['text', 'html'], thresholds: { lines: 70, functions: 70, statements: 70, branches: 60 } } }
});
