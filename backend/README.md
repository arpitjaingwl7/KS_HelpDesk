# Helpdesk — Backend

Spring Boot 3.5 · Java 21 · Spring Security (OAuth2 resource server, HS256 JWT) · Spring Data JPA · PostgreSQL 16 · Flyway · springdoc · MapStruct.
Design: [`docs/02`](../docs/02-architecture.md) (architecture), [`docs/04`](../docs/04-api-design.md) (API), [`docs/05`](../docs/05-security-and-multitenancy.md) (security). As built: [`docs/10`](../docs/10-implementation-notes.md).

## Commands

| Command | What it does |
|---|---|
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` | Run locally (needs `JWT_SECRET` and a PostgreSQL 16 on `DB_URL`) |
| `./mvnw test` | Unit, web-slice and ArchUnit tests + JaCoCo report and gate |
| `./mvnw verify` | The above, plus the integration tests (`*IT`, Testcontainers or `IT_DB_URL`) |
| `./mvnw package -DskipTests` | Build `target/helpdesk-backend-1.0.0.jar` |
| `docker build -t helpdesk-backend .` | Multi-stage image (JRE 21, non-root user, layered jar) |

## Package Layout (package-by-feature)

```
com.helpdesk
├── common/        error/ (ErrorCode, ApiException, GlobalExceptionHandler, ProblemDetailFactory)
│                  persistence/ (BaseEntity, TenantOwnedEntity)   web/ (PageResponse, SortSanitizer, RequestTracingFilter)
├── config/        Clock, JPA auditing, OpenAPI (+ Problem schema customizer), CORS
├── security/      SecurityConfig, JwtTokenService, JwtProperties, AuthenticatedUser(+Converter, Token),
│                  Role, PrincipalLoader (port), @CurrentUser, ProblemAuthEntryPoint/AccessDeniedHandler, MdcUserFilter
├── auth/          AuthController, AuthService (register-organization, login, me)
├── organization/  Organization, OrganizationService, OrganizationController
├── user/          User, UserRepository, UserService, UserController (/users), AgentController (/agents, /customers),
│                  UserPrincipalLoader (implements PrincipalLoader)
├── ticket/
│   ├── domain/      Ticket, TicketStatus, TicketPriority, CommentVisibility, TicketStateMachine,
│   │                TicketAccessPolicy, TicketPermissions
│   ├── repository/  TicketRepository (nextTicketNumber, stats), TicketSpecifications, TicketSearchCriteria
│   ├── service/     TicketService (commands), TicketQueryService (reads),
│   │                TicketAuditTrail + StatusChangeCommentWriter (ports, ADR-010)
│   └── web/         TicketController, TicketMapper, dto/
├── comment/       TicketComment, CommentRepository, CommentService (implements StatusChangeCommentWriter), CommentController
├── history/       TicketHistory, TicketHistoryRecorder (implements TicketAuditTrail), HistoryService, HistoryController
└── seed/          DemoDataSeeder (@Profile("demo"))
```

Dependencies point one way: `comment`, `history` → `ticket` → `user` → `security` → `common`. `ArchitectureTest` enforces this.

## Rules Every Change Must Keep

1. **The tenant comes from the actor, never from the request.** Services take `AuthenticatedUser actor` as an explicit parameter. No request DTO has an `organizationId` (ArchUnit rule 4).
2. **Tenant repositories are queried only with `organizationId`.** Calling `findById` / `findAll()` / `getReferenceById` / `existsById` on `TicketRepository`, `UserRepository`, `CommentRepository` or `TicketHistoryRepository` fails the build (ArchUnit rule 2). `findAll(Specification, …)` is allowed because `TicketSpecifications.search` always starts with the org predicate.
3. **Every ticket sub-resource starts with `TicketAccessPolicy.loadVisible`.** Not visible → 404. Visible but not allowed → 403. Wrong state → 409.
4. **Customer-facing filters live in SQL** (`requester_id`, `visibility = 'PUBLIC'`), so page counts are correct too.
5. **History is written through `TicketAuditTrail`** in the same transaction (`Propagation.MANDATORY`).
6. **Errors go through `ApiException(ErrorCode)`.** Unexpected exceptions become a generic 500. Stack traces only go to the log.
7. **A new endpoint needs** a web-slice role test (403) and a tenant-isolation test (Org B → 404).

## Configuration

`src/main/resources/application.yml` holds the defaults and the `dev` / `prod` profiles. The `test` profile lives in `src/test/resources/application-test.yml` and uses BCrypt cost 4 to keep tests fast. Environment variables are listed in the [root README](../README.md#environment-variables). Extra properties:

| Property | Default | Meaning |
|---|---|---|
| `helpdesk.security.bcrypt-strength` | `12` | BCrypt cost |
| `helpdesk.jwt.issuer` | `helpdesk` | `iss` claim, validated on every request |
| `spring.data.web.pageable.max-page-size` | `100` | Larger `size` values are capped |

## Testing

| Layer | Classes |
|---|---|
| Domain | `TicketStateMachineTest` (exhaustive), `TicketTest`, `TicketAccessPolicyTest` (role × action × status) |
| Services | `TicketServiceTest`, `TicketQueryServiceTest`, `CommentServiceTest`, `UserServiceTest`, `AuthServiceTest`, `OrganizationServiceTest`, `HistoryTest` |
| Security | `AuthenticatedUserConverterTest`, `JwtPropertiesTest` |
| Web slice | `TicketControllerWebTest`, `UserControllerWebTest`, `AuthControllerWebTest`. They use the real security chain and error handler, plus `@WithAuthenticatedUser(role = …)` |
| Architecture | `ArchitectureTest` (6 rules) |
| Integration | `TenantIsolationIT` (16 cases), `AuthIT`, `TicketLifecycleIT`, `TicketSearchIT`, `ConcurrencyIT` (ticket numbers, optimistic locking), `FlywayMigrationIT` |

Integration tests extend `support/AbstractIntegrationTest`. They make real HTTP calls with real JWTs. Each class registers its own organizations with unique slugs, so no cleanup is needed and the database can be shared.
