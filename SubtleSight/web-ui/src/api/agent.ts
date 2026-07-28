import { post } from './client';
import type { TurnRequest, TurnResponse } from '../types';

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
