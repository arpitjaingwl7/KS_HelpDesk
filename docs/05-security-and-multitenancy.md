# 05 — Security & Multi-Tenancy Design

> The most critical part of the system. Read this before writing any repository or service code.

---

## 1. Threat Model (what we defend against)

| # | Threat | Example | Mitigation |
|---|---|---|---|
| T1 | **Cross-tenant data access** (BOLA/IDOR) | An Org B agent calls `GET /tickets/{orgA-ticket-id}` | Every query is scoped by `organization_id` taken from the token. The response is `404`. A test suite covers it |
| T2 | **Tenant spoofing** | The client sends `organizationId` in a body or header | The API never accepts an org ID as input. DTOs have no such field. Unknown JSON properties are ignored |
| T3 | **Cross-tenant references** | Assigning an Org A ticket to an Org B agent's UUID | The service checks the assignee inside the org. Composite foreign keys in the database also reject it |
| T4 | **Intra-tenant over-exposure** | A customer reads another customer's ticket or internal notes | `TicketAccessPolicy` requires ownership for customers. Internal notes are filtered in SQL |
| T5 | **Privilege escalation** | A customer PATCHes `priority` or `role`, or posts an INTERNAL note | Role checks on the endpoint plus field-level checks in the service. Request DTOs are separate per use case, so there is no mass assignment |
| T6 | Token theft or replay | A stolen JWT is reused | Short TTL (60 min). User active state is checked on every request. HTTPS. Tokens are never put in URLs or logs |
| T7 | Credential attacks | Brute force or user enumeration on login | BCrypt (cost 12). The same `401` for every failure. Rate limiting is a Phase 9 item |
| T8 | Injection | SQL injection or XSS in comments | JPA parameter binding and the Criteria API only, no string-built SQL. React escapes output by default. No `dangerouslySetInnerHTML` |
| T9 | Information leakage | Stack traces or SQL in error responses | `GlobalExceptionHandler` returns a generic `500`. `server.error.include-stacktrace=never` |

## 2. Multi-Tenancy Strategy

**Model:** a single database with a shared schema and an `organization_id` discriminator column (see ADR-001).

Isolation is enforced in **four layers** (defense in depth):

```
┌───────────────────────────────────────────────────────────────────┐
│ 1. Identity      orgId comes ONLY from the verified JWT + DB user │
├───────────────────────────────────────────────────────────────────┤
│ 2. Service       actor passed explicitly; every load is           │
│                  findByIdAndOrganizationId(id, actor.orgId())      │
├───────────────────────────────────────────────────────────────────┤
│ 3. Repository    tenant repos expose ONLY org-scoped methods;      │
│                  ArchUnit forbids findById/findAll on them         │
├───────────────────────────────────────────────────────────────────┤
│ 4. Database      composite FKs (organization_id, x_id) make        │
│                  cross-tenant references physically impossible     │
│                  (+ optional Postgres RLS in Phase 9)              │
└───────────────────────────────────────────────────────────────────┘
          ▲ verified continuously by the TenantIsolationIT test suite
```

### 2.1 Identity → tenant
```java
public record AuthenticatedUser(UUID userId, UUID organizationId, Role role, String email) {
    public boolean isCustomer() { return role == Role.CUSTOMER; }
    public boolean isStaff()    { return role == Role.AGENT || role == Role.ORG_ADMIN; }
}
```
`AuthenticatedUserConverter` (a `Converter<Jwt, AbstractAuthenticationToken>`) does the following on **every request**:
1. Reads `sub` (userId) and the `org` claim from the validated JWT.
2. Loads the user by primary key and checks that `user.active` is true **and** `user.organizationId == org claim`. If either fails → `401`.
3. Builds the principal from the **database** values (role included). A role change or deactivation therefore takes effect immediately, even while the old token is still valid.

Controllers get the principal with `@CurrentUser AuthenticatedUser actor`, a meta-annotation over `@AuthenticationPrincipal`.

> **Cost:** one primary-key lookup per request, which is negligible. If needed later, it can be cached with Caffeine for about 30 seconds.

### 2.2 Repository conventions
```java
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // No @Modifying: UPDATE ... RETURNING yields a result set, so it runs as a query.
    // Must be called inside the (non-readOnly) create transaction.
    @Query(value = "UPDATE organizations SET ticket_sequence = ticket_sequence + 1 WHERE id = :orgId RETURNING ticket_sequence",
           nativeQuery = true)
    long nextTicketNumber(UUID orgId);
}

public interface CommentRepository extends JpaRepository<TicketComment, UUID> {
    Page<TicketComment> findByOrganizationIdAndTicketId(UUID orgId, UUID ticketId, Pageable p);
    Page<TicketComment> findByOrganizationIdAndTicketIdAndVisibility(UUID orgId, UUID ticketId,
                                                                     CommentVisibility v, Pageable p);
}
```
**ArchUnit rule:** classes in `..service..` must not call `JpaRepository.findById`, `findAll`, `getReferenceById` or `existsById` on `TicketRepository`, `UserRepository`, `CommentRepository` or `TicketHistoryRepository`.

### 2.3 Central visibility & permission policy
```java
@Component
public class TicketAccessPolicy {
    /** Loads a ticket the actor is allowed to SEE, otherwise throws NotFound (404). */
    public Ticket loadVisible(AuthenticatedUser actor, UUID ticketId) {
        Ticket t = tickets.findByIdAndOrganizationId(ticketId, actor.organizationId())
                          .orElseThrow(() -> NotFoundException.ticket(ticketId));
        if (actor.isCustomer() && !t.getRequester().getId().equals(actor.userId()))
            throw NotFoundException.ticket(ticketId);           // hide existence
        return t;
    }
    public void requireCanEdit(AuthenticatedUser a, Ticket t, boolean priorityChanged) { ... } // 403
    public void requireCanAssign(AuthenticatedUser a)                                    { ... } // 403
    public void requireCanTransition(AuthenticatedUser a, Ticket t, TicketStatus to)    { ... } // 403/409
    public void requireCanComment(AuthenticatedUser a, Ticket t, CommentVisibility v)   { ... } // 403/409
    public TicketPermissions permissionsFor(AuthenticatedUser a, Ticket t)              { ... } // for UI
}
```
Every ticket, comment and history endpoint goes through `loadVisible` first. This gives one choke point to review and test.

### 2.4 Optional layer 5 — PostgreSQL Row-Level Security (Phase 9)
```sql
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tickets
  USING (organization_id = current_setting('app.current_org')::uuid);
```
At the start of each transaction, the app sets `SET LOCAL app.current_org = ?` (through a `TransactionSynchronization` or a Hibernate `StatementInspector`). The app must connect as a non-owner role. Login and registration use a separate bypass role. This is deferred because it adds operational complexity. The four layers above already give strong guarantees.

## 3. Authentication Design

| Aspect | Decision |
|---|---|
| Mechanism | Stateless JWT bearer tokens through **Spring Security OAuth2 Resource Server** (`NimbusJwtDecoder` / `NimbusJwtEncoder`) |
| Algorithm | HS256 with a secret of ≥ 256 bits from `JWT_SECRET`. The app refuses to start if it is missing or too short |
| Claims | `iss=helpdesk`, `sub=<userId>`, `org=<orgId>`, `role=<ROLE>`, `iat`, `exp`, `jti` |
| TTL | 60 minutes (`JWT_TTL_MINUTES`) |
| Password hashing | `BCryptPasswordEncoder(12)` behind `DelegatingPasswordEncoder` |
| Login flow | Find org by slug → find user by (orgId, lowercased email) → check active + password → update `last_login_at` → issue token. When the org or user isn't found, a dummy BCrypt check still runs, so response timing doesn't reveal whether they exist |
| Logout | Client-side: the token is discarded. Server-side revocation is Phase 9, together with refresh tokens (stored hashed, rotated) |
| Frontend storage | The token is kept in memory, mirrored to `sessionStorage` so it survives a page reload, and cleared on logout or `401`. Trade-off: it is exposed to XSS, so mitigate with a strict CSP and React escaping. Phase 9 moves the refresh token into an httpOnly cookie |

### 3.1 Security filter chain (sketch)
```java
http
  .csrf(AbstractHttpConfigurer::disable)                      // stateless bearer API, no cookies
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .cors(Customizer.withDefaults())
  .authorizeHttpRequests(auth -> auth
      .requestMatchers(POST, "/api/v1/auth/login", "/api/v1/auth/register-organization").permitAll()
      .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
      .requestMatchers("/api/v1/users/**").hasRole("ORG_ADMIN")
      .requestMatchers(PATCH, "/api/v1/organization").hasRole("ORG_ADMIN")
      .requestMatchers("/api/v1/agents", "/api/v1/customers").hasAnyRole("ORG_ADMIN", "AGENT")
      .anyRequest().authenticated())
  .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(authenticatedUserConverter))
                              .authenticationEntryPoint(problemAuthEntryPoint))   // 401 as ProblemDetail
  .exceptionHandling(e -> e.accessDeniedHandler(problemAccessDeniedHandler))     // 403 as ProblemDetail
  .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
```

## 4. Secure Coding Checklist (PR review gate)

- [ ] No request DTO contains `organizationId`, `requester` (for customers), `status` (except on the status endpoint), `version` misuse, or `role` (except on admin user endpoints)
- [ ] Every new repository method on a tenant-owned entity takes `organizationId`
- [ ] Every ticket sub-resource endpoint calls `TicketAccessPolicy.loadVisible` first
- [ ] Customer-facing queries apply the `requesterId` and `visibility = PUBLIC` filters **in SQL**
- [ ] A new endpoint has a tenant-isolation test (Org B → `404`) and a role test (`403`)
- [ ] No passwords, tokens or full request bodies are logged
- [ ] Errors go through `ApiException(ErrorCode)`. No raw exception messages reach the client
