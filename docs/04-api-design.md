# 04 — REST API Design

> Base path: `/api/v1` · Format: JSON (UTF-8) · Auth: `Authorization: Bearer <JWT>`
> Live docs once built: `http://localhost:8080/swagger-ui.html` · Spec: `/v3/api-docs`

---

## 1. Endpoint Catalogue

| # | Method | Path | Roles | Purpose | Success |
|---|---|---|---|---|---|
| **Auth** |
| 1 | POST | `/auth/register-organization` | public | Create an org and its first ORG_ADMIN, then log in | 201 |
| 2 | POST | `/auth/login` | public | Exchange credentials for a JWT | 200 |
| 3 | GET | `/auth/me` | any | Current user and org | 200 |
| **Organization** |
| 4 | GET | `/organization` | any | Current org details | 200 |
| 5 | PATCH | `/organization` | ORG_ADMIN | Rename the org | 200 |
| **Users** |
| 6 | GET | `/users` | ORG_ADMIN | List and search users (paginated) | 200 |
| 7 | POST | `/users` | ORG_ADMIN | Create an agent, customer or admin | 201 |
| 8 | GET | `/users/{userId}` | ORG_ADMIN | User detail | 200 |
| 9 | PATCH | `/users/{userId}` | ORG_ADMIN | Change name, role or active flag | 200 |
| 10 | GET | `/agents` | ORG_ADMIN, AGENT | Active users you can assign tickets to (for dropdowns) | 200 |
| 11 | GET | `/customers` | ORG_ADMIN, AGENT | Active customers (for "raise on behalf of") | 200 |
| **Tickets** |
| 12 | POST | `/tickets` | any | Create a ticket | 201 |
| 13 | GET | `/tickets` | any | Search, filter and paginate (customers see only their own) | 200 |
| 14 | GET | `/tickets/{ticketId}` | any (if visible) | Ticket detail | 200 |
| 15 | PATCH | `/tickets/{ticketId}` | any (with rules) | Update subject, description or priority | 200 |
| 16 | PUT | `/tickets/{ticketId}/assignee` | ORG_ADMIN, AGENT | Assign the ticket | 200 |
| 17 | DELETE | `/tickets/{ticketId}/assignee` | ORG_ADMIN, AGENT | Unassign the ticket | 200 |
| 18 | PUT | `/tickets/{ticketId}/status` | any (with rules) | Change status (close and reopen included) | 200 |
| **Comments** |
| 19 | GET | `/tickets/{ticketId}/comments` | any (if visible) | List comments (INTERNAL ones filtered out for customers) | 200 |
| 20 | POST | `/tickets/{ticketId}/comments` | any (if visible) | Add a comment or internal note | 201 |
| **History** |
| 21 | GET | `/tickets/{ticketId}/history` | any (if visible) | Status, assignment and priority history | 200 |
| *Stretch* |
| 22 | GET | `/tickets/stats` | ORG_ADMIN, AGENT | Ticket counts by status and priority, for a dashboard | 200 |

"Any" means any authenticated user. The org is always the caller's own org, and the API never takes an org ID as input.

## 2. Conventions

### 2.1 Resource & naming
- JSON fields use camelCase. Enums are UPPER_SNAKE strings. IDs are UUID strings.
- Timestamps are ISO-8601 UTC, for example `2026-10-15T09:30:00Z`.
- A `201 Created` response includes a `Location` header and the created resource.
- `null` fields are included. Missing optional fields in a PATCH body mean "no change".

### 2.2 Pagination
Query parameters: `page` (0-based, default 0), `size` (default 20, max 100, larger values are capped), `sort=field,dir` (can repeat).

```json
{
  "content": [ ... ],
  "page": { "number": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
}
```
This uses Spring Data's `PagedModel` DTO serialization (`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`).

**Sort allow-list for tickets:** `createdAt`, `updatedAt`, `number`, `priority` (mapped to `priorityRank`), `status`. Any other field → `400 INVALID_SORT`. Default sort: `createdAt,desc`.

### 2.3 Optimistic concurrency
`TicketDetail` and `UserDetail` responses include `version`. `PATCH /tickets/{id}` and `PATCH /users/{id}` **require** `version` in the body. If it doesn't match the current version → `409 CONCURRENT_MODIFICATION`. Command endpoints (assignee, status) don't need a version. JPA `@Version` still catches true races between them.

### 2.4 Error format — RFC 7807 Problem Details
`Content-Type: application/problem+json`
```json
{
  "type": "https://helpdesk.local/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request has 2 invalid fields",
  "instance": "/api/v1/tickets",
  "code": "VALIDATION_FAILED",
  "traceId": "6f1c2a9e4b7d4c1a",
  "timestamp": "2026-10-15T09:30:00Z",
  "errors": [
    { "field": "subject", "message": "size must be between 3 and 200", "rejectedValue": "Hi" },
    { "field": "priority", "message": "must be one of LOW, MEDIUM, HIGH, URGENT" }
  ]
}
```

### 2.5 Status codes
| Code | When | `code` values |
|---|---|---|
| 200 | Successful read or update | — |
| 201 | Resource created | — |
| 400 | Bean validation failed, malformed JSON, bad enum, bad sort, invalid assignee | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_SORT`, `INVALID_ASSIGNEE`, `INVALID_REQUESTER` |
| 401 | Token missing, invalid or expired. Bad credentials. Deactivated user | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` |
| 403 | Authenticated, and the resource is visible, but the role or ownership doesn't allow the action (for example, a customer posting an INTERNAL note or changing priority) | `ACCESS_DENIED` |
| 404 | The resource doesn't exist, **or belongs to another org, or isn't visible to the caller** (another customer's ticket) | `RESOURCE_NOT_FOUND` |
| 409 | A business-state conflict | `INVALID_STATUS_TRANSITION`, `TICKET_CLOSED`, `CONCURRENT_MODIFICATION`, `EMAIL_ALREADY_EXISTS`, `SLUG_ALREADY_EXISTS`, `LAST_ADMIN`, `SELF_DEACTIVATION` |
| 415 / 405 | Wrong content type or method | `UNSUPPORTED_MEDIA_TYPE`, `METHOD_NOT_ALLOWED` |
| 500 | Unexpected error (generic message; the details go only to the log) | `INTERNAL_ERROR` |

**403 vs 404:** if the caller **may not see** the resource, the answer is `404`. If they **can see it but may not do this action**, the answer is `403`. This blocks ID enumeration across tenants and across customers (see ADR-004).

---

## 3. Endpoint Specifications

### 3.1 Auth

#### `POST /auth/register-organization` (public)
```json
// Request
{
  "organizationName": "Acme Corp",
  "organizationSlug": "acme",
  "adminFullName": "Alice Admin",
  "adminEmail": "alice@acme.com",
  "adminPassword": "S3cure!pass"
}
```
| Field | Rules |
|---|---|
| organizationName | required, 2–120 chars |
| organizationSlug | required, `^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$`, unique → otherwise `409 SLUG_ALREADY_EXISTS` |
| adminFullName | required, 1–120 chars |
| adminEmail | required, valid email, ≤ 254 chars, stored lowercase |
| adminPassword | required, 8–72 chars (72 is the BCrypt byte limit) |

`201 Created` returns the same body as login.

#### `POST /auth/login` (public)
```json
// Request
{ "organizationSlug": "acme", "email": "alice@acme.com", "password": "S3cure!pass" }

// 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "0192a6f0-...", "fullName": "Alice Admin", "email": "alice@acme.com",
    "role": "ORG_ADMIN",
    "organization": { "id": "0192a6ef-...", "name": "Acme Corp", "slug": "acme" }
  }
}
```
An unknown org, unknown email, wrong password or inactive user all return the **same** `401 INVALID_CREDENTIALS`.

#### `GET /auth/me`
Returns the same `user` object as login.

### 3.2 Organization
- `GET /organization` → `{ "id", "name", "slug", "createdAt" }`
- `PATCH /organization` (ORG_ADMIN) → body `{ "name": "Acme Inc" }` → `200` with the org

### 3.3 Users (ORG_ADMIN)

#### `GET /users?q=&role=AGENT&active=true&page=0&size=20&sort=fullName,asc`
`q` searches name and email. The response is a page of `UserSummary`: `{ id, fullName, email, role, active, createdAt }`.

#### `POST /users`
```json
{ "fullName": "Bob Agent", "email": "bob@acme.com", "password": "Initial#123", "role": "AGENT" }
```
`201` with `UserDetail` (`UserSummary` + `lastLoginAt`, `version`). Duplicate email in the org → `409 EMAIL_ALREADY_EXISTS`.

#### `PATCH /users/{userId}`
```json
{ "fullName": "Bob B. Agent", "role": "ORG_ADMIN", "active": true, "version": 0 }
```
Demoting or deactivating the last active admin → `409 LAST_ADMIN`. An admin deactivating their own account → `409 SELF_DEACTIVATION`.

#### `GET /agents` (ORG_ADMIN, AGENT) · `GET /customers` (ORG_ADMIN, AGENT)
Not paginated. Accepts `q`, max 50 results. Returns `[{ id, fullName, email, role }]`.

### 3.4 Tickets

#### `POST /tickets`
```json
{
  "subject": "Cannot reset password",
  "description": "The reset email never arrives...",
  "priority": "HIGH",
  "requesterId": null
}
```
| Field | Rules |
|---|---|
| subject | required, 3–200 chars, trimmed |
| description | required, 1–10 000 chars |
| priority | optional, defaults to `MEDIUM` |
| requesterId | optional. **Agents and admins only**: raise the ticket for an active customer in the same org. A customer who sends it → `403`. An unknown, inactive, non-customer or other-org user → `400 INVALID_REQUESTER` |

`201 Created` with `Location: /api/v1/tickets/{id}` and `TicketDetail`. A `CREATED` history entry is written.

#### `GET /tickets` — search
| Param | Type | Example | Notes |
|---|---|---|---|
| `q` | string | `password` | Matches subject or description with ILIKE. If numeric, it also matches `number` |
| `status` | enum[] | `status=OPEN&status=IN_PROGRESS` (or `status=OPEN,IN_PROGRESS`) | OR within the list |
| `priority` | enum[] | `priority=HIGH,URGENT` | OR within the list |
| `assigneeId` | uuid | — | Ignored for customers |
| `unassigned` | boolean | `true` | Can't be combined with `assigneeId` → `400` |
| `requesterId` | uuid | — | Agents and admins only. For customers it is forced to their own ID |
| `createdFrom`, `createdTo` | ISO date-time | — | Optional range |
| `page`, `size`, `sort` | — | `sort=priority,desc&sort=createdAt,asc` | See §2.2 |

Response: a page of `TicketSummary`:
```json
{
  "id": "0192a7...", "number": 42, "subject": "Cannot reset password",
  "status": "OPEN", "priority": "HIGH",
  "requester": { "id": "...", "fullName": "Carol Customer" },
  "assignee":  { "id": "...", "fullName": "Bob Agent" },
  "createdAt": "2026-10-15T09:30:00Z", "updatedAt": "2026-10-15T10:02:11Z"
}
```

Implementation: `TicketSpecifications` builds a JPA `Specification` that **always** starts with `organizationId = actor.orgId`, adds `requesterId = actor.userId` for customers, and then adds the optional filters. Requester and assignee are fetched in the same query (`@EntityGraph`), so there are no N+1 queries.

#### `GET /tickets/{ticketId}` → `TicketDetail`
```json
{
  "id": "0192a7...", "number": 42,
  "subject": "Cannot reset password", "description": "The reset email never arrives...",
  "status": "IN_PROGRESS", "priority": "HIGH",
  "requester": { "id": "...", "fullName": "Carol Customer", "email": "carol@example.com" },
  "assignee":  { "id": "...", "fullName": "Bob Agent", "email": "bob@acme.com" },
  "createdAt": "...", "updatedAt": "...", "resolvedAt": null, "closedAt": null,
  "version": 3,
  "allowedTransitions": ["OPEN", "ON_HOLD", "RESOLVED", "CLOSED"],
  "permissions": { "canEdit": true, "canChangePriority": true, "canAssign": true, "canAddInternalNote": true }
}
```
`allowedTransitions` and `permissions` are computed **for this caller** by `TicketAccessPolicy` and `TicketStateMachine`. The UI uses them to decide which actions to show, so authorization logic isn't duplicated in the frontend.

#### `PATCH /tickets/{ticketId}`
```json
{ "subject": "Password reset email not received", "description": "...", "priority": "URGENT", "version": 3 }
```
At least one of subject, description or priority is required, plus `version`. Customers: only subject and description, only on their own OPEN ticket (`403` otherwise). A closed ticket → `409 TICKET_CLOSED`. A priority change writes a `PRIORITY_CHANGED` history entry.

#### `PUT /tickets/{ticketId}/assignee` (ORG_ADMIN, AGENT)
```json
{ "assigneeId": "0192a6f3-..." }
```
The assignee must be an active AGENT or ORG_ADMIN **in the caller's org**. Otherwise → `400 INVALID_ASSIGNEE`. A user from another org gives the same error, so nothing leaks. Assigning to the current assignee → `200` with no history entry (idempotent). A closed ticket → `409 TICKET_CLOSED`.

#### `DELETE /tickets/{ticketId}/assignee` (ORG_ADMIN, AGENT)
Returns `200` with `TicketDetail` (not `204`, so the client gets the new version). Writes `ASSIGNEE_CHANGED` (old → null).

#### `PUT /tickets/{ticketId}/status`
```json
{ "status": "RESOLVED", "comment": "Fixed SMTP configuration." }
```
`comment` is optional. If present, a PUBLIC comment is added in the same transaction. The transition must be allowed for the caller's role (see [03 §3](03-domain-and-data-model.md#3-ticket-status-state-machine)). Otherwise → `409 INVALID_STATUS_TRANSITION`, or `403` if the transition exists but this role may not make it. **Closing a ticket** is `{ "status": "CLOSED" }`.

### 3.5 Comments

#### `GET /tickets/{ticketId}/comments?page=0&size=50` (oldest first)
```json
{
  "content": [
    { "id": "...", "body": "We are looking into it.", "visibility": "PUBLIC",
      "author": { "id": "...", "fullName": "Bob Agent", "role": "AGENT" },
      "createdAt": "..." },
    { "id": "...", "body": "Probably SMTP relay — check logs.", "visibility": "INTERNAL",
      "author": { "id": "...", "fullName": "Bob Agent", "role": "AGENT" },
      "createdAt": "..." }
  ],
  "page": { "number": 0, "size": 50, "totalElements": 2, "totalPages": 1 }
}
```
For a CUSTOMER, the repository query adds `visibility = 'PUBLIC'`. Internal notes are filtered **in SQL**, not in memory after the fact, so the page counts are correct too.

#### `POST /tickets/{ticketId}/comments`
```json
{ "body": "Any update?", "visibility": "PUBLIC" }
```
`body` is 1–5 000 chars. `visibility` defaults to PUBLIC. A customer sending INTERNAL → `403 ACCESS_DENIED`. A closed ticket → `409 TICKET_CLOSED`. `201` with the comment.

### 3.6 History

#### `GET /tickets/{ticketId}/history` (oldest first, not paginated; max 500)
```json
[
  { "id": "...", "eventType": "CREATED", "oldValue": null, "newValue": "OPEN",
    "actor": { "id": "...", "fullName": "Carol Customer" }, "createdAt": "..." },
  { "id": "...", "eventType": "ASSIGNEE_CHANGED",
    "oldValue": null, "newValue": { "id": "...", "fullName": "Bob Agent" },
    "actor": { "id": "...", "fullName": "Alice Admin" }, "createdAt": "..." },
  { "id": "...", "eventType": "STATUS_CHANGED", "oldValue": "OPEN", "newValue": "IN_PROGRESS",
    "actor": { "id": "...", "fullName": "Bob Agent" }, "createdAt": "..." }
]
```
`oldValue` and `newValue` are strings, except for `ASSIGNEE_CHANGED`, where they are user refs resolved in a single batched lookup.

---

## 4. Role × Endpoint Authorization Matrix

| Action | ORG_ADMIN | AGENT | CUSTOMER |
|---|---|---|---|
| Manage users / org settings | ✅ | ❌ 403 | ❌ 403 |
| List agents / customers | ✅ | ✅ | ❌ 403 |
| Create ticket | ✅ (may set requester) | ✅ (may set requester) | ✅ (self only) |
| List / view tickets | all in org | all in org | **own only** (others → 404) |
| Edit subject / description | ✅ | ✅ | own + OPEN only |
| Change priority | ✅ | ✅ | ❌ 403 |
| Assign / unassign | ✅ | ✅ | ❌ 403 |
| Change status | any valid transition | any valid transition | close own; reopen own RESOLVED |
| Public comment | ✅ | ✅ | own tickets |
| Internal note — create | ✅ | ✅ | ❌ 403 |
| Internal note — read | ✅ | ✅ | never returned |
| View history | ✅ | ✅ | own tickets |

Enforcement happens in two layers:
1. **Coarse** — `SecurityFilterChain` path rules plus `@PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")` on admin and agent endpoints.
2. **Fine** — `TicketAccessPolicy` in the service layer: ownership, ticket status, and field-level rules.

## 5. Postman / OpenAPI Deliverables
- `springdoc-openapi-starter-webmvc-ui` generates the spec. Controllers use `@Operation`, `@ApiResponse` and `@Tag` per feature. A global `bearerAuth` security scheme is defined.
- In Phase 6, the spec is exported to `docs/openapi.yaml` and imported into Postman. The collection lives at `postman/helpdesk.postman_collection.json`. It has a `{{baseUrl}}` variable, and a test script on the login request that saves `{{token}}`. There is one folder per feature, plus a **"Tenant isolation"** folder that logs in as an Org B user and tries to reach Org A resources (expects `404`).
