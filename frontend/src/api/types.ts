/** Unified HTTP envelope — matches backend ApiResponse. */
export type ApiResponse<T> = {
  success: boolean;
  code: string;
  message: string;
  data: T;
};

export type PlatformRole = 'SENIOR' | 'GENERAL';

export type CurrentUser = {
  userId: string;
  displayName: string;
  role: PlatformRole;
  roleLabel: string;
};

export type ConflictStatus = 'OPEN' | 'PENDING_CLOSE' | 'CLOSED' | 'SUSPENDED';

export type HandlerAcceptance = 'NONE' | 'PENDING_ACCEPT' | 'ACCEPTED';

export type TrackValue = {
  /** PRESENT | ABSENT | HOLLOW | IDENTITY_LOST (ask / 冲突 GET 投影，非 observed_fact) */
  availability: string;
  hostId: string | null;
  hostName: string | null;
};

export type UnboundReason = 'MISSING_LABEL' | 'UNKNOWN_OBJECT_ID';

export type UnboundCandidate = {
  id: string;
  sourceAgentId: string;
  sourceHostId: string;
  runtimeId: string;
  name: string;
  labels: Record<string, string>;
  reason: UnboundReason;
  upgradeChainPromised: boolean;
  observedAt: string;
};

export type IdentityLostMark = {
  curatedObjectId: string;
  reason: string;
  markedAt: string;
  sourceAgentId: string;
  sourceHostId: string;
  upgradeChainPromised: boolean;
};

export type ObservedValue = TrackValue;

export type ActualWhere = {
  question: string;
  track: string;
  relationType?: string;
  relationLabel?: string;
  subject?: CuratedObject;
  observedValue: ObservedValue;
  curatedValue: {
    hostId: string | null;
    hostName: string | null;
  };
  identityLost: boolean;
};

export type CuratedObject = {
  id: string;
  name?: string;
  kind?: string;
  [key: string]: unknown;
};

export type Collaboration = {
  acknowledged: boolean;
  acknowledgedAt: string | null;
  ownerUserId: string | null;
  handlerUserId: string | null;
  handlerAcceptance: HandlerAcceptance;
};

export type ConflictCase = {
  id: string;
  status: ConflictStatus;
  mergeKey: {
    subjectId: string;
    relationType: string;
    relationLabel: string;
  };
  subject: CuratedObject;
  curatedValue: TrackValue;
  observedValue: TrackValue;
  observedLineage: Array<{
    availability: string;
    hostId: string | null;
    hostName: string | null;
    at: string;
  }>;
  firstWarnedAt: string;
  updatedAt: string;
  pendingCloseAt: string | null;
  closedAt: string | null;
  suspendedAt: string | null;
  pendingCloseReminderVisible: boolean;
  observationHollow: boolean;
  identityLost: boolean;
  diagnosisStatus: string;
  collaboration: Collaboration;
};

export type ForkSuggestion = {
  id: string;
  label: string;
  kind: string;
  hypothesis: string;
  description: string;
};

export type ConflictDiagnosis = {
  id: string;
  conflictId: string;
  status: string;
  source: string;
  summary: string;
  forks: ForkSuggestion[];
  createdAt: string;
  completedAt: string | null;
  errorMessage: string | null;
};

export type PlanStep = {
  seq: number;
  action: string;
  description: string;
  params: Record<string, string>;
};

export type ExecutionStepLog = {
  seq: number;
  action: string;
  hostId: string;
  command: string;
  success: boolean;
  failureReason: string | null;
};

export type OperationPlan = {
  id: string;
  conflictId: string;
  diagnosisId: string;
  selectedForkId: string;
  branchKind: string;
  skipsDraft: boolean;
  status: string;
  steps: PlanStep[];
  createdBy: string;
  createdAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  approvedAt: string | null;
  executionIntent: boolean;
  currentStepSeq: number | null;
  voidReason: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  executionLog: ExecutionStepLog[] | null;
};

export type StartExecutionResult = {
  planId: string;
  status: string;
  message: string;
  completedSteps: number | null;
  voidReason: string | null;
  executionLog: ExecutionStepLog[] | null;
};

export type CuratedDraftItem = {
  id: string;
  seq: number;
  kind: string;
  status: string;
  subjectId: string | null;
  subjectName: string | null;
  fromHostId: string | null;
  fromHostName: string | null;
  toHostId: string | null;
  toHostName: string | null;
  mergeKey: boolean;
  payload?: Record<string, unknown>;
};

export type CuratedDraft = {
  id: string;
  conflictId: string | null;
  diagnosisId: string | null;
  selectedForkId: string | null;
  origin?: string;
  candidateId?: string | null;
  sourceHostId?: string | null;
  runtimeId?: string | null;
  status: string;
  items: CuratedDraftItem[];
  createdBy: string;
  createdAt: string;
};

export class ApiError extends Error {
  readonly code: string;
  readonly httpStatus: number;

  constructor(code: string, message: string, httpStatus: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
  }
}
