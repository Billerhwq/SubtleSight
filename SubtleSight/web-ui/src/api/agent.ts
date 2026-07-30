import { post, get, patch, del } from './client';
import type { TurnRequest, TurnResponse, AssistantSession } from '../types';

/** Send a message to the agent, returns turnId and immediate response. */
export async function postTurn(req: TurnRequest): Promise<TurnResponse> {
  return post<TurnResponse>('/agent/turn', req);
}

/** Subscribe to SSE events for a specific turn. */
export function subscribeTurnEvents(turnId: string): EventSource {
  return new EventSource(`/api/v1/agent/turns/${turnId}/events`) as EventSource;
}

/** Confirm (or reject) a high-risk pending action. */
export async function confirmTurn(turnId: string, approved: boolean): Promise<void> {
  await post<void>(`/agent/turns/${turnId}/confirm`, { approved });
}

/** Stop a running turn. Cancellation is best-effort for an in-flight tool call. */
export async function cancelTurn(turnId: string): Promise<void> {
  await post<void>(`/agent/turns/${turnId}/cancel`, {});
}

/** List recent sessions. */
export async function listSessions(limit = 20, offset = 0): Promise<AssistantSession[]> {
  return get<AssistantSession[]>(`/agent/sessions?limit=${limit}&offset=${offset}`);
}

/** Get session detail with last message. */
export async function getSessionDetail(sessionId: string): Promise<{ session: AssistantSession; lastMessage: string }> {
  return get<{ session: AssistantSession; lastMessage: string }>(`/agent/sessions/${sessionId}`);
}

/** Update session title. */
export async function updateSession(sessionId: string, title: string): Promise<void> {
  await patch<void>(`/agent/sessions/${sessionId}`, { title });
}

/** Delete (archive) a session. */
export async function deleteSession(sessionId: string): Promise<void> {
  await del<void>(`/agent/sessions/${sessionId}`);
}

/** Fork a new session from a specific turn. */
export async function forkSession(sessionId: string, fromTurn: string): Promise<{ sessionId: string; title: string; history: { role: string; content: string }[] }> {
  return post<{ sessionId: string; title: string; history: { role: string; content: string }[] }>(`/agent/sessions/${sessionId}/fork?fromTurn=${fromTurn}`);
}

/** Get turns for a session. */
export async function listTurns(sessionId: string, limit = 100): Promise<any[]> {
  return get<any[]>(`/agent/sessions/${sessionId}/turns?limit=${limit}`);
}
