import { post, get } from './client';

// ── Types ──

export interface ScopeRef {
  type: 'FILE' | 'DOCUMENT' | 'DRAWING' | 'DRAW_NODE' | 'FOLDER' | 'KNOWLEDGE_BASE';
  id?: string;
  resourceId?: string;
  nodeIds?: string[];
}

export interface QaAnswer {
  id: string;
  conversationId: string;
  parentAnswerId?: string;
  question: string;
  status: string;
  directAnswer: string;
  gapsJson: string;
  provider?: string;
  model?: string;
  errorCode?: string;
  createdAt: string;
  completedAt?: string;
}

export interface QaClaim {
  id: string;
  answerId: string;
  ordinal: number;
  type: string;
  statement: string;
  verificationStatus: string;
}

export interface QaCitation {
  id: string;
  claimId: string;
  relation: string;
  resourceType: string;
  resourceId: string;
  resourceName?: string;
  resourceVersion: string;
  unitId: string;
  locatorJson: string;
  exactQuote: string;
  snapshotHash: string;
  sourceFamily?: string;
  rank: number;
  createdAt: string;
}

export interface ClaimView {
  claim: QaClaim;
  citations: QaCitation[];
}

export interface AnswerView {
  answer: QaAnswer;
  claims: ClaimView[];
  scopeSnapshotJson: string;
  resourceNames?: Record<string, string>;
}

export interface CitationResolution {
  locatorValid: boolean;
  stale: boolean;
  currentVersion?: string;
  unit?: { stableLocator: string; text: string };
}

export interface StartResponse {
  answer: QaAnswer;
  scope: { resources: unknown[]; unitIds: string[]; excluded: unknown[] };
  jobId: string;
}

// ── API ──

/** Start a QA question. */
export function askQuestion(question: string, scopes: ScopeRef[]): Promise<StartResponse> {
  return post<StartResponse>('/qa/answers', { question, scopes });
}

/** Get the answer view with claims and citations. */
export function getAnswer(answerId: string): Promise<AnswerView> {
  return get<AnswerView>(`/qa/answers/${answerId}`);
}

/** Poll until the answer is complete. */
export async function pollAnswer(answerId: string, timeoutMs = 120000): Promise<AnswerView> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const view = await getAnswer(answerId);
    if (view.answer.status === 'COMPLETED' || view.answer.status === 'INSUFFICIENT' || view.answer.status === 'FAILED') {
      return view;
    }
    await new Promise(r => setTimeout(r, 500));
  }
  throw new Error('QA 回答超时');
}

/** Resolve a citation — check if it's still valid. */
export function resolveCitation(citationId: string): Promise<CitationResolution> {
  return get<CitationResolution>(`/qa/citations/${citationId}/resolve`);
}

/** Save an answer as a knowledge document. */
export function saveAnswerAsDocument(answerId: string, title: string, folderId?: string): Promise<{ id: string }> {
  return post<{ id: string }>(`/qa/answers/${answerId}/save-document`, { title, folderId });
}

/** Add a claim to a drawing. */
export function addClaimToDraw(claimId: string, documentId: string, expectedVersion: number): Promise<unknown> {
  return post(`/qa/claims/${claimId}/add-to-draw`, { documentId, expectedVersion });
}

/** Follow-up question. */
export function askFollowUp(answerId: string, question: string): Promise<StartResponse> {
  return post<StartResponse>(`/qa/answers/${answerId}/follow-ups`, { question });
}
