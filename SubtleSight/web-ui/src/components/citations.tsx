/** Shared citation types and helpers for the Agent UI. */
import { useState } from 'react';
import type { ReactNode } from 'react';

export type CitationMeta = {
  resourceId: string;
  resourceType: string;
  locatorJson: string;
  resourceName: string;
  url?: string;
  publishedAt?: string;
  summary?: string;
  exactQuote?: string;
  locator?: string;
};

export type Reference = {
  index: number;
  resourceId: string;
  resourceType: string;
  resourceName: string;
  url?: string;
  publishedAt?: string;
  summary?: string;
  locator?: string;
  exactQuote?: string;
};

/** Convert the turn-level references array into the renderer's Record<number, CitationMeta>. */
export function buildCitationMap(references?: Reference[]): Record<number, CitationMeta> | undefined {
  if (!references || references.length === 0) return undefined;
  const map: Record<number, CitationMeta> = {};
  for (const r of references) {
    map[r.index] = {
      resourceId: r.resourceId,
      resourceType: r.resourceType,
      locatorJson: r.locator ?? '{}',
      resourceName: r.resourceName,
      url: r.url,
      publishedAt: r.publishedAt,
      summary: r.summary,
      exactQuote: r.exactQuote,
      locator: r.locator,
    };
  }
  return map;
}

/** Build a navigation URL from a citation marker's metadata, or null when not in-app navigable. */
export function buildCitationUrl(meta: CitationMeta): string | null {
  try {
    const loc = JSON.parse(meta.locatorJson || '{}');
    const type = meta.resourceType.toUpperCase();
    if (type === 'DOCUMENT' || type === 'DRAW_NODE') {
      const blockId = loc.metadata?.blockId || loc.blockId;
      const nodeId = loc.metadata?.nodeId || loc.nodeId;
      let url = `/knowledge?documentId=${meta.resourceId}`;
      if (blockId) url += `&blockId=${encodeURIComponent(blockId)}`;
      if (nodeId) url += `&nodeId=${encodeURIComponent(nodeId)}`;
      return url;
    }
    if (type === 'FILE') {
      return `/knowledge?fileId=${meta.resourceId}`;
    }
    if (type === 'STORY') {
      return `/stories/${meta.resourceId}`;
    }
    // DOCUMENTVERSION / INTELLIGENCE / FOLDER: not navigable in-app — hover shows the external url instead
    if (type === 'DOCUMENTVERSION' || type === 'INTELLIGENCE' || type === 'FOLDER') {
      return null;
    }
    return `/knowledge?documentId=${meta.resourceId}`;
  } catch {
    return `/knowledge?documentId=${meta.resourceId}`;
  }
}

/** Navigate citations via HashRouter — no full page reload. */
export function handleCitationNav(url: string): void {
  window.location.hash = url.startsWith('/') ? url : `/${url}`;
}

/** Short, human-friendly date for hover cards. */
export function formatCitationTime(iso?: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' });
}

/** Extract a short locator label (block/node) from a locator JSON blob. */
export function citationLocatorLabel(locatorJson?: string): string {
  if (!locatorJson) return '';
  try {
    const loc = JSON.parse(locatorJson);
    const blockId = loc.metadata?.blockId ?? loc.blockId;
    const nodeId = loc.metadata?.nodeId ?? loc.nodeId;
    if (blockId) return `段落 ${blockId}`;
    if (nodeId) return `节点 ${nodeId}`;
    if (loc.stableLocator) return String(loc.stableLocator);
    return '';
  } catch {
    return '';
  }
}

/** Inline [N] marker with a hover preview card and click-to-jump. */
export function CitationMarker({ num, meta, onNavigate }: {
  num: string;
  meta: CitationMeta;
  onNavigate?: (url: string | null, meta: CitationMeta) => void;
}): ReactNode {
  const [open, setOpen] = useState(false);
  const url = buildCitationUrl(meta);
  const locator = citationLocatorLabel(meta.locatorJson);
  const hasContent = !!(meta.summary || meta.exactQuote || meta.publishedAt || meta.url || locator);

  return (
    <span
      className="citation-marker-wrap"
      onMouseEnter={() => { if (hasContent) setOpen(true); }}
      onMouseLeave={() => setOpen(false)}
    >
      <sup
        className="citation-marker"
        title={meta.resourceName}
        onClick={(e) => {
          e.stopPropagation();
          onNavigate?.(url, meta);
        }}
      >
        [{num}]
      </sup>
      {open && hasContent && (
        <div className="citation-popover" role="tooltip">
          <strong>{meta.resourceName}</strong>
          {meta.exactQuote && <blockquote className="citation-pop-quote">“{meta.exactQuote}”</blockquote>}
          {!meta.exactQuote && meta.summary && <p className="citation-pop-summary">{meta.summary}</p>}
          {(meta.publishedAt || locator || meta.resourceType) && (
            <div className="citation-pop-meta">
              {meta.publishedAt && <span>📅 {formatCitationTime(meta.publishedAt)}</span>}
              {locator && <span>📍 {locator}</span>}
              {meta.resourceType && <span>{meta.resourceType}</span>}
            </div>
          )}
          {meta.url && (
            <a
              className="citation-pop-link"
              href={meta.url}
              target="_blank"
              rel="noreferrer"
              onClick={(e) => e.stopPropagation()}
            >
              查看原文 ↗
            </a>
          )}
        </div>
      )}
    </span>
  );
}

/** Static preview of what citations will look like — shown when the agent asks the user for a source URL. */
export function CitationTemplateHint(): ReactNode {
  return (
    <div className="citation-template-hint">
      <span className="citation-template-title">引用示例</span>
      <p>
        该功能支持并行计算<span className="citation-marker">[1]</span>，并通过认证机制保证正确性
        <span className="citation-marker">[2]</span>。
      </p>
      <div className="citation-template-refs">
        <span>[1] 来源标题 — 链接</span>
        <span>[2] 来源标题 — 链接</span>
      </div>
    </div>
  );
}
