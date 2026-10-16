# 01 — Requirements Specification

> Multi-Tenant Helpdesk & Ticketing System
> Status: **Baseline v1.0** · Owner: Architecture · Last updated: 2026-10-15



## 1. Objective

A web application where **multiple organizations (tenants)** manage support tickets. Each organization has its own users and tickets. **No user can read or change another organization's data.** This is the most important rule in the system: it outranks every other requirement.

## 2. Actors & Roles

| Role | Who | Scope |
|---|---|---|
| **ORG_ADMIN** | Manages one organization | Full control inside their own organization: users, all tickets, settings |
| **AGENT** | Support staff | Works on all tickets in their organization: triage, assign, comment, add internal notes, change status |
| **CUSTOMER** | End user who raises issues | Creates tickets and sees **only their own** tickets and public comments |
| *Anonymous* | Not logged in | Can only register a new organization or log in |

A user belongs to **exactly one** organization and has **exactly one** role.

## 3. Functional Requirements

### 3.1 User management & authentication
| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-1 | Users log in with organization slug + email + password | Valid credentials → `200` with an access token. Invalid credentials, unknown org or disabled account → `401` with the same generic message |
| FR-2 | The system supports three roles: ORG_ADMIN, AGENT, CUSTOMER | The role travels in the token and is enforced on every endpoint |
| FR-3 | ORG_ADMIN can create users and change their name, role or active flag | Duplicate email in the same org → `409`. Users are deactivated, never hard-deleted |
| FR-4 | A deactivated user cannot log in or use a token they already hold | The next request after deactivation → `401` |
| FR-5 | `GET /auth/me` returns the current user and their organization | — |
| FR-6 | Self-service onboarding: anyone can register a new organization, and the first user becomes its ORG_ADMIN | Slug must be unique → otherwise `409` |

### 3.2 Organization management
| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-7 | Many organizations live side by side in one deployment | Each user, ticket, comment and history entry carries `organization_id` |
| FR-8 | ORG_ADMIN can view and rename their own organization | The slug cannot be changed (it is used for login) |
| FR-9 | Tenant isolation | Any request for a resource in another org → `404` (not `403`), so nobody can probe which IDs exist |

### 3.3 Ticket management
| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-10 | Create a ticket | Required: subject, description. Priority defaults to MEDIUM. Status starts as OPEN. Requester = current user (agents and admins may raise a ticket on behalf of a customer) |
| FR-11 | Each ticket has: id, per-org number, subject, description, priority, status, requester, assignee, createdAt, updatedAt, resolvedAt, closedAt | Returned by the detail API |
| FR-12 | View a ticket | Agent/Admin: any ticket in their org. Customer: only tickets they requested, otherwise `404` |
| FR-13 | Update a ticket (subject, description, priority) | Optimistic locking: a stale `version` → `409` |
| FR-14 | Assign or unassign a ticket | Only to an **active AGENT or ORG_ADMIN in the same org**, otherwise `400 INVALID_ASSIGNEE` |
| FR-15 | Change status, including close and reopen | Must follow the state machine ([03](03-domain-and-data-model.md#3-ticket-status-state-machine)). An invalid transition → `409` |

### 3.4 Search, filtering, pagination
| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-16 | Filter by status (one or many), priority (one or many), assignee (`assigneeId` or `unassigned=true`) and requester | Filters combine with AND; multiple values for one filter combine with OR |
| FR-17 | Free-text search on subject and description, plus the ticket number | Case-insensitive |
| FR-18 | Paginate and sort | `page` (0-based), `size` (default 20, max 100), `sort` limited to an allow-list. The response includes page metadata |
| FR-19 | Customers are always limited to their own tickets, whatever filters they send | Enforced on the server |

### 3.5 Comments & history
| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-20 | Add a comment to a ticket | `visibility` = PUBLIC (default) or INTERNAL |
| FR-21 | Internal notes are hidden from customers | Customers never receive INTERNAL comments from any endpoint. A customer who tries to post one → `403` |
| FR-22 | History records ticket creation and every status, assignment and priority change | Each entry stores actor, old value, new value and timestamp. Entries are append-only |
| FR-23 | View the history of a ticket | Allowed for anyone who can view the ticket |

### 3.6 REST API
| ID | Requirement |
|---|---|
| FR-24 | Versioned REST API under `/api/v1`, documented with OpenAPI 3 (Swagger UI) and a Postman collection |
| FR-25 | Every request body is validated. Errors use RFC 7807 Problem Details with field-level errors |
| FR-26 | Correct HTTP status codes (see [04-api-design.md §2.5](04-api-design.md#25-status-codes)) |

### 3.7 Frontend
| ID | Requirement |
|---|---|
| FR-27 | Screens: login, register organization, ticket list (with filters and pagination), create ticket, ticket detail (comments and history), ticket actions (assign, status, edit), user management (admin) |
| FR-28 | The UI adapts to the user's role (hides actions they cannot take). The backend still enforces every rule |

## 4. Business Rules

| ID | Rule |
|---|---|
| BR-1 | `organization_id` always comes from the authenticated user's token, **never** from the request body, path or query |
| BR-2 | Ticket numbers are sequential **per organization** (Acme #1, #2… and Globex #1, #2… are independent) |
| BR-3 | Customers can edit subject and description only on their own tickets, and only while the status is OPEN. They cannot change priority after creating the ticket |
| BR-4 | Customers can **close** their own ticket from any status that isn't closed, and can **reopen** their own ticket when it is RESOLVED. Agents and admins can make any valid transition |
| BR-5 | Moving to RESOLVED sets `resolvedAt`. Moving to CLOSED sets `closedAt`. Reopening clears both |
| BR-6 | A CLOSED ticket is read-only (no edits, no assignment, no comments) until it is reopened |
| BR-7 | Every change to status, assignee or priority writes a history entry **in the same transaction** |
| BR-8 | An org must always keep at least one active ORG_ADMIN. Demoting or deactivating the last one → `409` |
| BR-9 | Emails are stored lowercase and are unique **within an organization**. The same email may exist in two orgs as two separate users |
| BR-10 | Deactivating an agent does not unassign their tickets automatically. Admins can filter by that agent and reassign |

## 5. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-1 | Security | Passwords are hashed with BCrypt. JWTs are signed (HS256) with a secret of at least 256 bits taken from the environment. No secrets are stored in the repo |
| NFR-2 | Security | Follow the OWASP API Top 10: object-level authorization on every resource (BOLA), no mass assignment, input validation, no stack traces in responses |
| NFR-3 | Isolation | An automated test suite proves that cross-tenant reads and writes fail for every ticket, comment, history and user endpoint |
| NFR-4 | Performance | Ticket list p95 < 300 ms with 100k tickets per org on a single Postgres instance (made possible by composite indexes) |
| NFR-5 | Consistency | Optimistic locking (`@Version`) on tickets and users. All timestamps are UTC (`Instant` / `TIMESTAMPTZ`) |
| NFR-6 | Maintainability | Modular monolith organized by feature. Unit coverage ≥ 80% on the service and domain layers (JaCoCo gate) |
| NFR-7 | Operability | One-command local start (`docker compose up`). Health endpoint. Structured logs with `traceId`, `orgId` and `userId` |
| NFR-8 | Portability | Java 21, Spring Boot 3.5.x, PostgreSQL 16. All config comes from environment variables (12-factor) |
| NFR-9 | Documentation | README, OpenAPI spec, Postman collection, and the architecture documents in this folder |

## 6. Assumptions & Clarifications

The brief left these points open. Each one is a decision we can revisit. The **bold** option is the one we chose.

| # | Question | Decision |
|---|---|---|
| A-1 | How are organizations created? | **Self-service registration** that creates the org and its first ORG_ADMIN. A platform super-admin is out of scope |
| A-2 | Is email unique globally or per org? | **Per org.** Login therefore asks for the org slug |
| A-3 | Can agents see every ticket in their org or only assigned ones? | **Every ticket in the org.** Teams and queues are out of scope |
| A-4 | Can customers register themselves? | **No (MVP).** The admin creates customer accounts. Optional `POST /auth/register-customer` is planned for Phase 9 |
| A-5 | Should customers see history? | **Yes, for their own tickets.** History holds nothing internal |
| A-6 | Which database? | **PostgreSQL 16** (see ADR-002) |
| A-7 | Which frontend? | **React 18 + TypeScript + Vite** (see ADR-005) |
| A-8 | Session model? | **Stateless JWT access token** (60 min). Refresh tokens are a Phase 9 stretch goal |
| A-9 | Should cross-tenant access return 403 or 404? | **404** (see ADR-004) |

## 7. Out of Scope (v1)

Email notifications, file attachments, SLAs and escalations, teams and queues, a platform super-admin, SSO/OAuth login, real-time updates (WebSocket), multiple languages, and editing or deleting comments. Several of these are listed as Phase 9 enhancements in the [plan](08-implementation-plan.md).
