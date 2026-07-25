import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
describe('prototype static shell', () => {
    const root = resolve(process.cwd());
    it('uses the prototype shell as the Vite entry instead of the old React root', () => {
        const html = readFileSync(resolve(root, 'index.html'), 'utf8');
        expect(html).toContain('data-page="discover"');
        expect(html).toContain('app.js');
        expect(html).not.toContain('/src/main.tsx');
        expect(html).not.toContain('id="root"');
    });
    it('hydrates prototype pages from the backend workspace overview endpoint', () => {
        const app = readFileSync(resolve(root, 'public/app.js'), 'utf8');
        expect(app).toContain('/api/v1/workspace/overview');
        expect(app).toContain('hydrateLiveData');
        expect(app).toContain('hydrateTrends');
        expect(app).toContain('/api/v1/sources/collect-real');
        expect(app).toContain('data-refresh-live');
        expect(app).toContain('真实信源热榜');
        expect(app).toContain('Live · 本机数据');
    });
    it('never falls back to prototype discovery cards when live data is empty', () => {
        const app = readFileSync(resolve(root, 'public/app.js'), 'utf8');
        expect(app).toContain('没有回退到原型静态数据');
        expect(app).toContain('liveFeedItems.filter(item => matchesChannel(item, channelKey) && matchesPlatform(item, platform))');
        expect(app).toContain('data-channel-count');
        expect(app).not.toContain('<b id="scopeResultCount">126</b>');
        expect(app).not.toContain('<div class="intel-list">${inboxSignals.map(signalInboxRow).join(\'\')}</div>');
    });
    it('adds a real financial calendar surface backed by calendar APIs', () => {
        const app = readFileSync(resolve(root, 'public/app.js'), 'utf8');
        const html = readFileSync(resolve(root, 'public/calendar.html'), 'utf8');
        expect(html).toContain('data-page="calendar"');
        expect(app).toContain('/api/v1/calendar/events');
        expect(app).toContain('/api/v1/calendar/sources');
        expect(app).toContain('/api/v1/calendar/refresh?scope=today');
        expect(app).toContain('/api/v1/calendar.ics');
        expect(app).toContain('不会展示静态样例');
        expect(app).toContain('只显示真实入库');
    });
    it('supports real playable video entries in the live discovery feed', () => {
        const app = readFileSync(resolve(root, 'public/app.js'), 'utf8');
        expect(app).toContain('Internet Archive');
        expect(app).toContain("sourceType === 'VIDEO'");
        expect(app).toContain('/api/v1/sources/collect-real?maxSources=8');
        expect(app).toContain('mediaUrl');
        expect(app).toContain('<video class="inline-video" controls preload="metadata"');
        expect(app).toContain('已下载到本机 blob，可直接播放');
    });
    it('exposes the knowledge editor while keeping deep research agent surfaces disabled', () => {
        const app = readFileSync(resolve(root, 'public/app.js'), 'utf8');
        expect(app).not.toContain("['research', 'research.html'");
        expect(app).toContain("['knowledge', 'knowledge-editor.html#/knowledge'");
        expect(app).not.toContain("['editor', 'knowledge-editor.html#/knowledge/editor'");
        expect(app).not.toContain('href="research.html');
        expect(app).not.toContain('href="story.html');
        expect(app).not.toContain('id="agentButton"');
        expect(app).not.toContain('id="globalSearch"');
    });
});
