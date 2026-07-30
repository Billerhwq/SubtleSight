export type SourceType = 'RSS' | 'WEBSITE' | 'SITEMAP' | 'GITHUB' | 'ARXIV' | 'HN' | 'HUGGING_FACE' | 'PRODUCT_HUNT' | 'VIDEO' | 'REDDIT' | 'NEWS_API' | 'NEWSLETTER' | 'CUSTOM_API' | 'UPLOAD';
export type ViewType = 'FOR_YOU' | 'EMERGING' | 'IMPORTANT' | 'LATEST' | 'SAVED';
export interface Source {
    id: string;
    name: string;
    type: SourceType;
    kind: string;
    endpoint: string;
    schedule?: string;
    tier: string;
    health: string;
    topics: string[];
    enabled: boolean;
    updatedAt: string;
}
export interface Story {
    id: string;
    title: string;
    summary?: string;
    status: string;
    firstObservedAt: string;
    lastObservedAt: string;
    sourceCount: number;
    sourceFamilyCount: number;
    entities: string[];
    topics: string[];
    manualOverride: boolean;
}
export interface Signal {
    score: number;
    reasonCodes: string[];
    features: Record<string, number>;
    computedAt: string;
    viewType: ViewType;
}
export interface FeedItem {
    story: Story;
    signal: Signal;
    saved: boolean;
    hidden: boolean;
    timeline: StoryMember[];
}
export interface StoryMember {
    storyId: string;
    documentVersionId: string;
    role: string;
    sourceFamily: string;
    similarity: number;
    addedAt: string;
    title?: string;
    summary?: string;
    text?: string;
    canonicalUrl?: string;
    publishedAt?: string;
    author?: string;
    language?: string;
}
export interface ResearchRun {
    id: string;
    storyId?: string;
    question: string;
    mode: string;
    status: string;
    usage: {
        queries: number;
        pages: number;
        tokens: number;
        cost: number;
    };
    gaps: string[];
    updatedAt: string;
}
export interface Claim {
    id: string;
    statement: string;
    status: string;
    critical: boolean;
}
export interface WatchTarget {
    id: string;
    type: string;
    name: string;
    expression: string;
    baselineJson?: string;
    baselineVersion: number;
    enabled: boolean;
    updatedAt: string;
}
export interface ChangeEvent {
    id: string;
    field: string;
    oldValue?: string;
    newValue?: string;
    source: string;
    rule: string;
    confidence: number;
    severity: string;
    status: string;
    detectedAt: string;
}
export interface ReportVersion {
    id: string;
    researchRunId: string;
    reportType: string;
    version: number;
    title: string;
    markdown: string;
    html: string;
    citationsVerified: boolean;
    createdAt: string;
}
export interface SavedView {
    id: string;
    name: string;
    expression: string;
    enabled: boolean;
    version: number;
}
export interface Job {
    id: string;
    type: string;
    status: string;
    priority: number;
    attempt: number;
    errorCode?: string;
    createdAt: string;
}
export interface KnowledgeFolder {
    id: string;
    parentId?: string;
    name: string;
    createdAt: string;
    updatedAt: string;
}
export interface KnowledgeFile {
    id: string;
    folderId?: string;
    name: string;
    ext: string;
    mimeType?: string;
    sizeBytes: number;
    sha256: string;
    createdAt: string;
    updatedAt: string;
}
export interface KnowledgeDocument {
    id: string;
    folderId?: string;
    title: string;
    contentHtml: string;
    drawingJson: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}
export interface KnowledgeDocumentVersion {
    documentId: string;
    version: number;
    title: string;
    contentHtml: string;
    drawingJson: string;
    changeSummary: string;
    createdAt: string;
}
export interface KnowledgeAiSuggestion {
    suggestion: string;
    provider: string;
    model: string;
    fallback: boolean;
}
export interface KnowledgePreview {
    id: string;
    name: string;
    mimeType: string;
    sizeBytes: number;
    ext: string;
    kind: 'text' | 'image' | 'pdf' | 'binary';
    content: string | null;
    contentBase64: string | null;
}
export interface TurnRequest {
    message: string;
    context: Record<string, unknown>;
    sessionId?: string;
}
export interface TurnResponse {
    turnId: string;
    sessionId: string;
    message: string;
    tools: string[];
    confirmationRequired: boolean;
}
export interface TurnEvent {
    turnId: string;
    sessionId?: string;
    message: string;
    tools: string[];
    confirmationRequired: boolean;
    result?: Record<string, unknown>;
}
export interface AssistantSession {
    id: string;
    title: string;
    createdAt: string;
    updatedAt: string;
    archived: boolean;
}
