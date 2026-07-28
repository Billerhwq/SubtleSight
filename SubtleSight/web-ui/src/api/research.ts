import { get, post } from './client';
import type { ResearchRun, Claim } from '../types';

export interface ResearchDetail {
  run: ResearchRun;
  claims: Claim[];
}

/** Start a new research run. */
export function startResearch(question: string, mode: string, storyId?: string): Promise<ResearchRun> {
  return post<ResearchRun>('/research', { question, mode, storyId: storyId ?? null });
}

/** List all research runs. */
export function listResearch(): Promise<ResearchRun[]> {
  return get<ResearchRun[]>('/research');
}

/** Get research detail with claims. */
export function getResearch(id: string): Promise<ResearchDetail> {
  return get<ResearchDetail>(`/research/${id}`);
}

/** Cancel a research run. */
export function cancelResearch(id: string): Promise<void> {
  return post<void>(`/research/${id}/cancel`);
}
