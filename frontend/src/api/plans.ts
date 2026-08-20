import { apiRequest } from './client';
import type { CuratedDraft, OperationPlan, StartExecutionResult } from './types';

export function selectBranch(conflictId: string, forkId: string): Promise<OperationPlan | CuratedDraft> {
  return apiRequest<OperationPlan | CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/branch-selection`,
    { method: 'POST', body: { forkId } },
  );
}

export function getActivePlan(conflictId: string): Promise<OperationPlan> {
  return apiRequest<OperationPlan>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/operation-plans/active`,
  );
}

export function approvePlan(planId: string): Promise<OperationPlan> {
  return apiRequest<OperationPlan>(
    `/api/operation-plans/${encodeURIComponent(planId)}/approve`,
    { method: 'POST' },
  );
}

export function startExecution(planId: string): Promise<StartExecutionResult> {
  return apiRequest<StartExecutionResult>(
    `/api/operation-plans/${encodeURIComponent(planId)}/start-execution`,
    { method: 'POST' },
  );
}
