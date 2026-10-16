# 10 — Implementation Notes (As Built)

> Status: **Phases 0–8 implemented** · Last updated: 2026-10-16
> This document records what was built, where it differs from the design in docs 01–09, and how each requirement was verified.

---

## 1. What Was Delivered

| Phase | Output | Where |
|---|---|---|
| 0 Foundations | Maven wrapper, Spring Boot 3.5.16 / Java 21, profiles `dev` `test` `demo` `prod`, JaCoCo, CI, Vite scaffold | `backend/pom.xml`, `.github/workflows/ci.yml` |
| 1 Domain & persistence | `V1__init_schema.sql` (composite FKs, CHECKs, indexes), entities, org-scoped repositories, `TicketStateMachine` | `backend/src/main/resources/db/migration`, `ticket/domain` |
| 2 Auth & tenancy | HS256 JWT (Nimbus), per-request principal re-load, ProblemDetail 401/403, MDC (`traceId`, `orgId`, `userId`) | `security/`, `auth/`, `common/` |
| 3 Org & users | `/organization`, `/users`, `/agents`, `/customers`, LAST_ADMIN / SELF_DEACTIVATION rules | `organization/`, `user/` |
| 4 Ticket core | Create (per-org number, on-behalf-of), update (version check), assign/unassign, status machine, search + pagination, stats | `ticket/` |
| 5 Comments & history | Visibility filter in SQL, status-change comment in the same transaction, batched name resolution | `comment/`, `history/` |
| 6 Docs & hardening | OpenAPI with Problem schemas, `docs/openapi.yaml`, Postman collection, isolation suite, coverage gate, perf smoke test | `docs/openapi.yaml`, `postman/` |
| 7 Frontend | All screens of doc 06, role-aware via `permissions` / `allowedTransitions` | `frontend/` |
| 8 Packaging | Backend and frontend Dockerfiles, `docker-compose.yml`, `DemoDataSeeder`, README | repo root |

Phase 9 (optional enhancements) was not started, apart from the stretch endpoint `GET /tickets/stats`, which is implemented and shown as a summary strip above the staff ticket list.

## 2. Deviations From the Design

Each change keeps the intent of the original design. Items 1–2 are recorded as [ADR-010](09-architecture-decisions.md#adr-010--dependency-inversion-between-ticket-history-and-comment).

| # | Design said | As built | Why |
|---|---|---|---|
| 1 | `TicketService` calls `TicketHistoryRecorder` and `CommentService` directly | `TicketService` depends on two small interfaces in `ticket.service`: `TicketAuditTrail` (implemented by `history.TicketHistoryRecorder`) and `StatusChangeCommentWriter` (implemented by `comment.CommentService`) | `history` and `comment` need `ticket`'s `TicketAccessPolicy`, so direct calls form package cycles, which ArchUnit rule 5 forbids. Calls are still synchronous and in the same transaction (ADR-009). The recorder uses `Propagation.MANDATORY`, so it cannot run outside the caller's transaction |
| 2 | `Role` in `user/`, `AuthenticatedUserConverter` loads the user from the database | `Role` lives in `security/`. The converter calls a `PrincipalLoader` port, which `user.UserPrincipalLoader` implements | Same reason: `security` must not depend on `user` while `user` depends on `security` |
| 3 | `CommentVisibility` in `comment/` | In `ticket.domain` | `TicketAccessPolicy.requireCanComment` takes it as a parameter |
| 4 | `BaseEntity` holds `id`, `createdAt`, `updatedAt` | `BaseEntity` = `id` + `createdAt`. `updatedAt` is declared on `Organization`, `User` and `Ticket` only | `ticket_comments` and `ticket_history` have no `updated_at` column (append-only), and `ddl-auto=validate` would reject it |
| 5 | — | `BaseEntity` implements `Persistable<UUID>` | IDs are assigned in Java (UUIDv7), so without this `save()` would issue a `SELECT` + merge instead of an `INSERT` |
| 6 | `TicketPermissions` has 4 flags | Adds `canComment` (false when CLOSED) | Lets the UI hide the composer without re-implementing BR-6 |
| 7 | Comments and history reference users via `@ManyToOne` | Comments: `@ManyToOne author` (fetched with `@EntityGraph`). History: plain `actorId` UUID, names resolved in one batched lookup | Avoids a `getReferenceById` on a tenant repository (forbidden by ArchUnit), and the API already needs batched resolution for `ASSIGNEE_CHANGED` |
| 8 | Page envelope via `PagedModel` (`VIA_DTO`) | Explicit `common.web.PageResponse` record with the same JSON shape | Typed and documented properly in OpenAPI |
| 9 | Indexes per doc 03 | One extra index: `ix_tickets_org_created (organization_id, created_at DESC)` | Serves the default list (no status filter) — confirmed by the perf smoke test |
| 10 | — | `users.last_login_at` is `@OptimisticLock(excluded = true)` | A login would otherwise bump the user's version and make a concurrent admin edit fail with 409 |
| 11 | — | Customers sending `requesterId` equal to their own id are accepted (treated as "me") | Only sending *someone else's* id returns 403 |
| 12 | Integration tests use Testcontainers | Testcontainers by default. They can also run against an existing Postgres 16 via `IT_DB_URL` and are **skipped** (not failed) if neither is available | Lets the suite run where Docker isn't available |
| 13 | Frontend URL state | `?page=` in the URL is **1-based** and converted to the API's 0-based `page` | Friendlier links; documented in `frontend/README.md` |
| 14 | Frontend types generated with `openapi-typescript` | Hand-written in `frontend/src/api/types.ts`, matching `docs/openapi.yaml` | The spec was exported after the UI was built; generating them is a follow-up |

## 3. Verification Evidence

All results below are from runs on 2026-10-16.

| Check | Command | Result |
|---|---|---|
| Unit + web slice + ArchUnit | `./mvnw verify` (surefire) | **202 tests, 0 failures** |
| Integration (real HTTP, real JWT, PostgreSQL 16) | `./mvnw verify` (failsafe) | **35 tests, 0 failures**, including all **16 tenant-isolation cases** |
| Coverage gate (service, domain, security) | JaCoCo `check` | **Passed**: 99.8% line coverage on gated classes, 88% overall from unit tests |
| Postman collection | `npx newman run postman/helpdesk.postman_collection.json` | **55 requests, 89 assertions, 0 failures** |
| Frontend | `npm run lint && npm run typecheck && npm test && npm run build` | Lint and typecheck clean, **36 tests passed**, build OK |
| UI against the live API | Headless Chrome logging in as admin, agent and customer (Acme) | All screens render with seeded data, with no console errors. Internal notes are shown only to staff |
| Startup safety | Start without `JWT_SECRET` | Refuses to start: `JWT_SECRET must be set and at least 32 bytes long` |
| `prod` profile | Packaged jar with `prod,demo` | Starts. Logs are ECS JSON |

### 3.1 Performance smoke test (NFR-4)
100,000 tickets were inserted into one organization, then `EXPLAIN ANALYZE` was run on PostgreSQL 16:

| Query | Execution time | Plan |
|---|---|---|
| Default list (org, `created_at desc`, limit 20) | 0.02 ms | `ix_tickets_org_created` |
| Status filter `OPEN, IN_PROGRESS` | 0.02 ms | `ix_tickets_org_created` |
| Customer's own tickets | 0.02 ms | `ix_tickets_org_created` |
| Priority sort (business order) | 0.01 ms | `ix_tickets_org_priority` |
| Free text `ILIKE '%password%'` | 4.2 ms | `ix_tickets_subject_trgm` + `ix_tickets_description_trgm` |
| `count(*)` for page metadata | 9.7 ms | Sequential scan (the test org holds almost every row, so the planner prefers it) |

Every query is far below the 300 ms p95 target.

### 3.2 Not verified here
- **`docker compose up --build`** was not run, because no Docker daemon was available on the build machine. The Dockerfiles were checked piece by piece: the layered-jar extraction command and the nginx config were tested locally. CI (`docker` job) builds both images.
- **Testcontainers mode**: the ITs were run against a local PostgreSQL 16.14 through `IT_DB_URL`. In CI they run through Testcontainers (`postgres:16-alpine`).

## 4. Requirements → Tests

| Requirement | Verified by |
|---|---|
| FR-1, FR-5, FR-6 | `AuthServiceTest`, `AuthControllerWebTest`, `AuthIT.register_login_me`, `AuthIT.login_failures_areIndistinguishable` |
| FR-2, FR-28 | `TicketControllerWebTest`, `UserControllerWebTest` (role → 403), `TicketAccessPolicyTest.permissionsFor_eachRole` |
| FR-3, BR-8, BR-9 | `UserServiceTest`, `AuthIT.lastAdmin_andSelfDeactivation_areProtected`, `AuthIT.duplicateEmail_inSameOrg_returns409` |
| FR-4 | `AuthenticatedUserConverterTest`, `AuthIT.deactivatedUser_tokenRejectedOnNextRequest_andCannotLogIn`, `AuthIT.roleChange_appliesImmediately_toExistingToken` |
| FR-7, FR-9, BR-1 | `TenantIsolationIT` #1–16, `ArchitectureTest` (unscoped repository calls, no `organizationId` in requests) |
| FR-8 | `OrganizationServiceTest`, `UserControllerWebTest.organization_*` |
| FR-10, FR-11, BR-2 | `TicketServiceTest.create_*`, `ConcurrencyIT.ticketNumbers_areUniqueAndConsecutive_underParallelCreates` |
| FR-12, FR-19 | `TicketAccessPolicyTest.loadVisible_*`, `TenantIsolationIT` #12, `TicketSearchIT.customer_isAlwaysLimitedToOwnTickets` |
| FR-13, BR-3 | `TicketServiceTest.update_*`, `ConcurrencyIT.twoPatchesWithSameVersion_oneWins_otherGets409` |
| FR-14, BR-10 | `TicketServiceTest.assign_*`, `TenantIsolationIT` #9 |
| FR-15, BR-4, BR-5, BR-6 | `TicketStateMachineTest` (all 25 pairs × role), `TicketLifecycleIT` |
| FR-16, FR-17, FR-18 | `TicketSearchIT`, `TicketControllerWebTest.search_bindsFiltersAndCapsPageSize`, `SortSanitizerTest` |
| FR-20, FR-21 | `CommentServiceTest`, `TenantIsolationIT` #13 (also checks `totalElements`) |
| FR-22, FR-23, BR-7 | `HistoryTest`, `TicketLifecycleIT.fullLifecycle_recordsHistoryInOrder` |
| FR-24 | `docs/openapi.yaml`, Swagger UI, Newman run |
| FR-25, FR-26 | `TicketControllerWebTest` (400/401/403/404/409/415/500), `AuthControllerWebTest` |
| FR-27 | Frontend Vitest suite + the headless browser walkthrough (§3) |
| NFR-1 | `JwtPropertiesTest`, BCrypt cost 12 (`helpdesk.security.bcrypt-strength`) |
| NFR-3 | `TenantIsolationIT` |
| NFR-4 | Perf smoke test (§3.1) |
| NFR-5 | `@Version` on `Ticket` and `User`, `ConcurrencyIT`, `Instant` / `TIMESTAMPTZ` everywhere |
| NFR-6 | JaCoCo gate in `pom.xml`, `ArchitectureTest` |
| NFR-7 | `RequestTracingFilter`, `MdcUserFilter`, `/actuator/health`, ECS JSON logs in `prod` |

## 4a. Fixes After the First UI Review

| Issue | Cause | Fix | Regression check |
|---|---|---|---|
| Field labels overlapped their values (login, edit dialogs, filters) | Forms use react-hook-form's uncontrolled `register()`. Values set with `setValue`/`reset` or by browser autofill never reach MUI's "filled" state, so the floating label stayed on top of the text | App-wide theme defaults: `MuiInputLabel.shrink = true`, `MuiOutlinedInput.notched = true` (`frontend/src/theme.ts`) | Headless-browser screenshots of every form ([User Guide](11-user-guide.md)) |
| The "Ticket created" notification would not close with ✕ | `SnackbarProvider` reopened any queued toast whenever `open` was false. A closed toast leaves the queue only after its exit animation, so it reopened immediately | Track the id of the toast already shown and open only a new one | `SnackbarProvider.test.tsx` (fails on the old code), plus a browser check: toast gone after clicking ✕ |

## 5. Known Limitations

- **LAST_ADMIN race**: two admins demoting each other at the same moment could both pass the "at least one other active admin" check. Nothing locks the admin rows yet. Fix: `SELECT … FOR UPDATE` on the org's admins in `UserService.update`.
- **No server-side logout**: tokens stay valid until they expire (60 min). The per-request active check limits the risk (ADR-003). Refresh tokens and revocation are Phase 9.
- **No login rate limiting** (Phase 9, Bucket4j).
- **`count(*)` per list page**: about 10 ms at 100k tickets in one org. Very large tenants may want a capped or estimated count.
- **Frontend dependencies**: `npm audit` reports moderate advisories in React Router 6 (fixed only in v7, while doc 06 specifies v6), plus advisories in dev-only tooling. None of the dev-only ones ship in the production bundle.
- **`JWT_SECRET` fallback in `docker-compose.yml`** is a dev-only placeholder so the demo starts with one command. Set a real secret through `.env` for any shared deployment.
