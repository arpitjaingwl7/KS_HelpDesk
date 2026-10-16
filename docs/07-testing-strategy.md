# 07 — Testing Strategy

> JUnit 5 · Mockito · AssertJ · Spring Boot Test · Testcontainers (PostgreSQL 16) · ArchUnit · JaCoCo · Vitest (frontend)

---

## 1. Test Pyramid

| Level | Scope | Tooling | Runs in | Target count |
|---|---|---|---|---|
| **Unit** | Domain classes and services with mocked repositories | JUnit 5 + Mockito + AssertJ | `mvn test` (surefire), < 10 s | ~80–120 |
| **Web slice** | Controller validation, status codes, JSON shape, security rules | `@WebMvcTest` + `spring-security-test` | `mvn test` | ~30 |
| **Persistence** | Repository queries, Specifications, constraints, migrations | `@DataJpaTest` + Testcontainers | `mvn verify` (failsafe) | ~15 |
| **Integration (E2E API)** | The full stack over HTTP against real Postgres, **including tenant isolation** | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers + `RestClient`/MockMvc | `mvn verify` | ~30 |
| **Architecture** | Layering and tenant-repository rules | ArchUnit | `mvn test` | ~6 |
| **Frontend** | Key components and hooks | Vitest + React Testing Library + MSW | `npm test` | ~15 |

**Coverage gate:** JaCoCo fails the build if line coverage is below **80%** on `..service..`, `..domain..` and `..security..`. DTOs, config and the seeder are excluded.

## 2. Unit Tests — Important Business Logic

### `TicketStateMachineTest` (parameterized)
- Every allowed transition in the matrix succeeds for staff (`@MethodSource` over all pairs).
- Every disallowed transition throws `INVALID_STATUS_TRANSITION`.
- Customers: may close from OPEN, IN_PROGRESS, ON_HOLD and RESOLVED. May reopen from RESOLVED. **May not** reopen from CLOSED, or move to IN_PROGRESS, ON_HOLD or RESOLVED.
- Side effects: RESOLVED sets `resolvedAt`. CLOSED sets `closedAt` (and `resolvedAt` if it was empty). OPEN clears both. A fixed `Clock` is used.

### `TicketServiceTest` (Mockito)
| Test | Verifies |
|---|---|
| `create_setsOrgAndRequesterFromActor` | `organizationId` comes from the actor. The requester is the actor. Status is OPEN. Priority defaults to MEDIUM. The number comes from `nextTicketNumber(orgId)` |
| `create_byAgentOnBehalfOfCustomer_setsRequester` | Uses `requesterId`. Checks that the user is a customer in the same org |
| `create_byCustomerWithRequesterId_throws403` | — |
| `create_recordsCreatedHistory` | `historyRecorder.created(...)` is called once |
| `assign_toAgentInSameOrg_succeedsAndRecordsHistory` | The assignee is set. History has the old and new values |
| `assign_toCustomer_throwsInvalidAssignee` | — |
| `assign_toInactiveAgent_throwsInvalidAssignee` | — |
| `assign_toUserOfOtherOrg_throwsInvalidAssignee` | The repository is called with the **actor's** orgId, gets an empty result, and the service throws |
| `assign_sameAssignee_isNoOpWithoutHistory` | — |
| `assign_closedTicket_throwsTicketClosed` | — |
| `changeStatus_valid_recordsHistory_andOptionalComment` | — |
| `changeStatus_invalid_throws409_andWritesNothing` | `verifyNoInteractions(historyRecorder)` |
| `update_customerOwnOpenTicket_canEditSubject` | — |
| `update_customerChangesPriority_throws403` | — |
| `update_customerOnInProgressTicket_throws403` | — |
| `update_staleVersion_throwsConcurrentModification` | — |
| `update_priorityChange_recordsPriorityHistory` | — |
| `load_ticketOfOtherOrg_throwsNotFound` | `findByIdAndOrganizationId` returns empty → 404 |
| `load_otherCustomersTicket_throwsNotFound` | Same org, different requester → 404 (not 403) |

### `TicketAccessPolicyTest`
- A table-driven role × action × ownership × status matrix that mirrors [04 §4](04-api-design.md#4-role--endpoint-authorization-matrix).
- `permissionsFor` returns the correct flags for each role.

### `CommentServiceTest`
- A customer posting INTERNAL → 403. A customer posting PUBLIC on their own ticket → OK.
- A customer listing comments → the repository is called with `visibility = PUBLIC` (verified with Mockito).
- Staff listing → the unfiltered query is used.
- Commenting on a CLOSED ticket → 409.

### `UserServiceTest`
- Duplicate email (case-insensitive) → 409. The email is stored lowercase. The password is hashed (and never stored as plain text).
- Demoting or deactivating the last admin → `LAST_ADMIN`. Self-deactivation → `SELF_DEACTIVATION`.

### `AuthServiceTest`
- Valid login → a token with `sub`, `org` and `role` claims. `lastLoginAt` is updated.
- Wrong password, unknown org, unknown email or inactive user → the same `INVALID_CREDENTIALS`.
- `registerOrganization` → creates the org and an ORG_ADMIN. A duplicate slug → 409.

### `AuthenticatedUserConverterTest`
- An active user with a matching org → principal built from **database** values.
- A deactivated user → 401. An org claim that doesn't match the database → 401.

## 3. Web Slice Tests (`@WebMvcTest`)
- `POST /tickets` with a blank subject → 400, `code=VALIDATION_FAILED`, `errors[0].field=subject`.
- An invalid enum value → 400 `MALFORMED_REQUEST`. An unknown sort field → 400 `INVALID_SORT`. `size=1000` → capped at 100.
- No token → 401 ProblemDetail. A customer calling `PUT /tickets/{id}/assignee` → 403. A customer calling `GET /users` → 403.
- `POST` responses → 201 with a `Location` header.
- Uses a custom `@WithAuthenticatedUser(role = AGENT)` security-context factory.

## 4. Tenant Isolation Integration Suite — `TenantIsolationIT` ⭐

**Fixture:** two orgs, **Acme** and **Globex**, each with an admin, an agent and two customers, and tickets with comments. The suite runs against real Postgres (Testcontainers) over HTTP with real JWTs.

| # | Actor | Request | Expected |
|---|---|---|---|
| 1 | Globex agent | `GET /tickets/{acmeTicket}` | 404 |
| 2 | Globex agent | `PATCH /tickets/{acmeTicket}` | 404, Acme ticket unchanged in the database |
| 3 | Globex agent | `PUT /tickets/{acmeTicket}/status` | 404 |
| 4 | Globex agent | `PUT /tickets/{acmeTicket}/assignee` | 404 |
| 5 | Globex agent | `GET/POST /tickets/{acmeTicket}/comments` | 404 |
| 6 | Globex agent | `GET /tickets/{acmeTicket}/history` | 404 |
| 7 | Globex agent | `GET /tickets` | Only Globex tickets. Total = number of Globex tickets |
| 8 | Globex agent | `GET /tickets?requesterId={acmeCustomer}` | Empty page |
| 9 | Acme agent | `PUT /tickets/{acmeTicket}/assignee {globexAgentId}` | 400 `INVALID_ASSIGNEE` |
| 10 | Globex admin | `GET/PATCH /users/{acmeUser}` | 404 |
| 11 | Globex admin | `GET /agents` | Only Globex agents |
| 12 | Acme customer A | `GET /tickets/{customerB-ticket}` (same org) | 404 |
| 13 | Acme customer | `GET /tickets/{id}/comments` | No INTERNAL comments. `totalElements` counts only PUBLIC ones |
| 14 | Any | Body containing `"organizationId": "<globex>"` on create | Ignored. The ticket is created in the actor's org |
| 15 | DB-level | Insert a ticket with Acme org + Globex assignee via JDBC | Foreign key violation |
| 16 | Same email in both orgs | Login with each slug | Each returns the correct user and org |

## 5. Other Integration Tests
- `TicketLifecycleIT`: create → assign → in progress → internal note → public reply → resolve → customer reopens → close. Checks the history order and the timestamps.
- `TicketSearchIT`: seeds about 60 tickets and checks every filter, combined filters, `q` matching subject, description and number, sorting by priority in business order, and pagination metadata.
- `TicketNumberConcurrencyIT`: 20 parallel creates in one org give 20 unique, consecutive numbers.
- `OptimisticLockingIT`: two PATCH requests with the same version → one gets 200, the other 409.
- `FlywayMigrationIT`: migrations apply cleanly to an empty database. Hibernate `ddl-auto=validate` passes.

## 6. Architecture Tests (ArchUnit)
1. `..web..` must not depend on `..repository..` or on entity classes in response signatures.
2. Services must not call `findById`, `findAll`, `getReferenceById` or `existsById` on tenant-owned repositories.
3. No `@RestController` returns a JPA `@Entity`.
4. Request DTOs (`*Request` records) must not declare a field named `organizationId`.
5. Feature packages don't form cycles (`slices().matching("com.helpdesk.(*)..").should().beFreeOfCycles()`).

## 7. Conventions
- Naming: `methodUnderTest_condition_expectedResult`. Arrange / Act / Assert blocks.
- Test data builders (`TicketFixtures.aTicket().withStatus(OPEN).build()`). No shared mutable state.
- Integration tests share one Postgres container (singleton pattern, `@ServiceConnection`) for speed. Each test cleans up with `TRUNCATE … CASCADE` or runs in its own fresh org.
- The CI pipeline (`.github/workflows/ci.yml`) runs `./mvnw -B verify` (unit + IT + JaCoCo gate) and `npm ci && npm run lint && npm run typecheck && npm test && npm run build`.
