# 09 — Architecture Decision Records (ADRs)

> Format: Context → Decision → Alternatives → Consequences. Status: **Accepted** unless noted.

---

## ADR-001 — Shared database, shared schema, `organization_id` discriminator
**Context.** Tenants must be strictly isolated. We expect many small or medium tenants, and there is one deployment.
**Decision.** Every tenant-owned table carries `organization_id`. Isolation is enforced by (1) the tenant coming only from the token, (2) explicit org-scoped repository methods, (3) ArchUnit rules, and (4) composite foreign keys `(organization_id, x_id)`.
**Alternatives considered.**
| Option | Why not (for now) |
|---|---|
| Database per tenant | Strongest isolation, but heavy to operate (connections, migrations ×N). Overkill here |
| Schema per tenant | Migration fan-out and connection routing. Hard to report across tenants |
| Hibernate 6 `@TenantId` (automatic discriminator) | Elegant, but hidden "magic": it doesn't cover native queries, it needs a "root" bypass for login, and it is harder to show in Mockito unit tests. Explicit scoping is easier to review |
| Postgres Row-Level Security alone | Strong, but it depends on per-transaction session variables and on database role setup. **Kept as an optional extra layer** (Phase 9) |

**Consequences.** Cheap and simple to run. Correctness depends on discipline, which the automated tests and the database constraints enforce. Large tenants can be moved out later.

## ADR-002 — PostgreSQL 16 + Spring Data JPA + Flyway; Spring Boot 3.5.x on Java 21
**Context.** The client allows MongoDB, MySQL or PostgreSQL. The data is relational: tickets reference users, and comments and history reference tickets.
**Decision.** PostgreSQL, for composite foreign keys, CHECK constraints, generated columns, `pg_trgm` search and future RLS. Hibernate validates against a schema owned by Flyway.
**Consequences.** Database-level isolation guarantees that MongoDB couldn't give. Spring Boot 3.5.x is chosen for ecosystem stability (springdoc 2.x, MapStruct, Testcontainers). Moving to Boot 4.x is a contained upgrade once the libraries are confirmed compatible.

## ADR-003 — Stateless JWT via Spring Security OAuth2 Resource Server (HS256)
**Context.** The API is consumed by a single-page app. We want horizontal scalability without server sessions.
**Decision.** Short-lived (60 min) signed JWTs carrying `sub`, `org` and `role`. They are validated with Spring's built-in `NimbusJwtDecoder` rather than a hand-written filter. The user is **re-loaded from the database on each request**, so deactivation and role changes apply immediately.
**Alternatives.** Server sessions + cookie (needs CSRF handling and sticky or shared sessions). An external IdP such as Keycloak (too heavy for the scope). RS256 (only needed when third parties verify the tokens).
**Consequences.** There is no server-side logout until refresh-token or revocation support arrives (Phase 9). Mitigated by the short TTL and the per-request active check.

## ADR-004 — 404 (not 403) for resources outside the caller's visibility
**Context.** Returning 403 for another tenant's ticket confirms that the ID exists.
**Decision.** A resource in another org, or another customer's ticket, is treated as non-existent → `404`. `403` is used only when the caller can see the resource but lacks permission for the action.
**Consequences.** Blocks ID enumeration. Debugging is a little less obvious, which is acceptable and documented in the API guide.

## ADR-005 — Monorepo with a React + TypeScript SPA served by nginx (same origin)
**Context.** "Any frontend technology" is allowed. Reviewers should be able to run everything with one command.
**Decision.** React 18 + TS + Vite + MUI + TanStack Query in `frontend/`. In Docker, nginx serves the static build and reverse-proxies `/api` to the backend.
**Alternatives.** Thymeleaf server-side rendering (simpler, but a weaker showcase for a REST API). Angular (heavier). Next.js (needs a Node server, unnecessary here).
**Consequences.** No CORS in production, since it is the same origin. Dev uses the Vite proxy. Both API and UI are versioned together.

## ADR-006 — UUID primary keys + per-org sequential ticket number
**Decision.** UUIDv7 (time-ordered, index-friendly) for every ID exposed in the API. Tickets also get a human-friendly `number` that is unique per org and allocated with an atomic `UPDATE … RETURNING`.
**Consequences.** IDs can't be guessed or enumerated. Users see a friendly "#42". There is a small row-lock contention on the org's counter under heavy parallel ticket creation in one org, which is acceptable.

## ADR-007 — RFC 7807 Problem Details for all errors
**Decision.** Use Spring 6's built-in `ProblemDetail`, extended with `code`, `traceId`, `timestamp` and `errors[]`. Stable machine-readable `ErrorCode` values.
**Consequences.** Clients branch on `code`, not on message text. The frontend maps `errors[]` onto form fields.

## ADR-008 — Package-by-feature modular monolith with explicit actor passing
**Decision.** Features (`ticket`, `comment`, `history`, `user`, `organization`, `auth`) are top-level packages. Services receive `AuthenticatedUser actor` as a parameter instead of reading a ThreadLocal or `SecurityContextHolder` inside business code.
**Consequences.** Tenant scoping is visible in every signature and trivial to unit test. ArchUnit enforces the boundaries, and the features could be pulled out into modules later (Spring Modulith) if needed.

## ADR-009 — History written synchronously in the same transaction
**Decision.** `TicketHistoryRecorder` is called directly by `TicketService` inside the same `@Transactional` method. Domain events are not used yet.
**Alternatives.** `@TransactionalEventListener` or a Hibernate Envers audit table.
**Consequences.** History is always consistent with the change it records. Once notifications arrive in Phase 9, the recorder becomes the single place that also publishes events.

## ADR-010 — Dependency inversion between `ticket`, `history` and `comment`
**Context.** ADR-008 requires feature packages without cycles (enforced by ArchUnit). `history` and `comment` need `ticket`'s `TicketAccessPolicy` to check visibility. But `TicketService` must also write history entries and the optional status-change comment in its own transaction (ADR-009). Direct calls in both directions form a cycle. `security` ↔ `user` had the same problem: the JWT converter needs to load users, and every service takes the `AuthenticatedUser` principal.
**Decision.** The core package owns small ports, and the dependent features implement them:
- `ticket.service.TicketAuditTrail` ← `history.TicketHistoryRecorder` (`@Transactional(propagation = MANDATORY)`)
- `ticket.service.StatusChangeCommentWriter` ← `comment.CommentService`
- `security.PrincipalLoader` ← `user.UserPrincipalLoader`. `Role` and `AuthenticatedUser` live in `security`.

**Alternatives.** Relax the cycle rule (loses the guard rail). Spring application events (asynchronous by default, and less obvious to read). Merge the packages into `ticket` (loses the feature boundaries).
**Consequences.** Dependencies point one way only: `comment`, `history` → `ticket` → `user` → `security` → `common`. ADR-009 still holds: history and comments are written synchronously in the same transaction, and `MANDATORY` makes that impossible to get wrong. Unit tests mock the ports directly.
