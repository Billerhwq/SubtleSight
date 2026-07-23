import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
export default defineConfig({
    plugins: [react()],
    resolve: { extensions: ['.tsx', '.ts', '.jsx', '.js', '.mjs', '.json'] },
    server: { port: 5173, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false } } },
    build: {
        target: 'es2022',
        sourcemap: true,
        rollupOptions: {
            input: {
                main: resolve(process.cwd(), 'index.html'),
                'knowledge-editor': resolve(process.cwd(), 'knowledge-editor.html'),
            },
        },
    },
    test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], coverage: { provider: 'v8', reporter: ['text', 'html'], thresholds: { lines: 70, functions: 70, statements: 70, branches: 60 } } }
});
