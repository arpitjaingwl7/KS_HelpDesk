/**
 * Hand-written DTO types mirroring docs/04-api-design.md.
 * (Planned: replace with openapi-typescript output once the spec is exported.)
 */

export type Uuid = string;
/** ISO-8601 UTC timestamp, e.g. `2026-10-15T09:30:00Z`. */
export type IsoDateTime = string;

export const ROLES = ['ORG_ADMIN', 'AGENT', 'CUSTOMER'] as const;
export type Role = (typeof ROLES)[number];

export const TICKET_STATUSES = ['OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED'] as const;
export type TicketStatus = (typeof TICKET_STATUSES)[number];

export const TICKET_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const;
export type TicketPriority = (typeof TICKET_PRIORITIES)[number];

export type CommentVisibility = 'PUBLIC' | 'INTERNAL';
export type HistoryEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'ASSIGNEE_CHANGED'
  | 'PRIORITY_CHANGED';

export interface PageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  content: T[];
  page: PageMeta;
}

// ---------- Auth ----------

export interface OrganizationRef {
  id: Uuid;
  name: string;
  slug: string;
}

export interface CurrentUser {
  id: Uuid;
  fullName: string;
  email: string;
  role: Role;
  organization: OrganizationRef;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: CurrentUser;
}

export interface LoginRequest {
  organizationSlug: string;
  email: string;
  password: string;
}

export interface RegisterOrganizationRequest {
  organizationName: string;
  organizationSlug: string;
  adminFullName: string;
  adminEmail: string;
  adminPassword: string;
}

// ---------- Organization ----------

export interface Organization extends OrganizationRef {
  createdAt: IsoDateTime;
}

export interface UpdateOrganizationRequest {
  name: string;
}

// ---------- Users ----------

export interface UserSummary {
  id: Uuid;
  fullName: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: IsoDateTime;
}

export interface UserDetail extends UserSummary {
  lastLoginAt: IsoDateTime | null;
  version: number;
}

export interface CreateUserRequest {
  fullName: string;
  email: string;
  password: string;
  role: Role;
}

export interface UpdateUserRequest {
  fullName?: string;
  role?: Role;
  active?: boolean;
  version: number;
}

export interface UserFilters {
  q?: string;
  role?: Role;
  active?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

/** Item returned by GET /agents and GET /customers. */
export interface UserOption {
  id: Uuid;
  fullName: string;
  email: string;
  role: Role;
}

// ---------- Tickets ----------

export interface UserRef {
  id: Uuid;
  fullName: string;
}

export interface UserRefWithEmail extends UserRef {
  email: string;
}

export interface TicketSummary {
  id: Uuid;
  number: number;
  subject: string;
  status: TicketStatus;
  priority: TicketPriority;
  requester: UserRef;
  assignee: UserRef | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface TicketPermissions {
  canEdit: boolean;
  canChangePriority: boolean;
  canAssign: boolean;
  canAddInternalNote: boolean;
  canComment: boolean;
}

export interface TicketDetail {
  id: Uuid;
  number: number;
  subject: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  requester: UserRefWithEmail;
  assignee: UserRefWithEmail | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
  resolvedAt: IsoDateTime | null;
  closedAt: IsoDateTime | null;
  version: number;
  allowedTransitions: TicketStatus[];
  permissions: TicketPermissions;
}

export interface CreateTicketRequest {
  subject: string;
  description: string;
  priority?: TicketPriority;
  requesterId?: Uuid;
}

export interface UpdateTicketRequest {
  subject?: string;
  description?: string;
  priority?: TicketPriority;
  version: number;
}

export interface ChangeStatusRequest {
  status: TicketStatus;
  comment?: string;
}

export interface AssignRequest {
  assigneeId: Uuid;
}

/** Query parameters for GET /tickets. */
export interface TicketQuery {
  q?: string;
  status?: TicketStatus[];
  priority?: TicketPriority[];
  assigneeId?: Uuid;
  unassigned?: boolean;
  requesterId?: Uuid;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TicketStats {
  total: number;
  unassigned: number;
  byStatus: Partial<Record<TicketStatus, number>>;
  byPriority: Partial<Record<TicketPriority, number>>;
}

// ---------- Comments ----------

export interface CommentAuthor {
  id: Uuid;
  fullName: string;
  role: Role;
}

export interface Comment {
  id: Uuid;
  body: string;
  visibility: CommentVisibility;
  author: CommentAuthor;
  createdAt: IsoDateTime;
}

export interface CreateCommentRequest {
  body: string;
  visibility?: CommentVisibility;
}

// ---------- History ----------

interface HistoryBase {
  id: Uuid;
  actor: UserRef;
  createdAt: IsoDateTime;
}

export interface AssigneeHistoryEntry extends HistoryBase {
  eventType: 'ASSIGNEE_CHANGED';
  oldValue: UserRef | null;
  newValue: UserRef | null;
}

export interface ValueHistoryEntry extends HistoryBase {
  eventType: 'CREATED' | 'STATUS_CHANGED' | 'PRIORITY_CHANGED';
  oldValue: string | null;
  newValue: string | null;
}

export type HistoryEntry = AssigneeHistoryEntry | ValueHistoryEntry;
