# 06 — Frontend Design

> React 18 · TypeScript (strict) · Vite · React Router 6 · TanStack Query 5 · React Hook Form + Zod · MUI 6 · Axios

---

## 1. Principles
1. **The backend is the source of truth for authorization.** The UI hides or shows actions based on `permissions` and `allowedTransitions` from the API. It never re-implements the rules.
2. **Filters live in the URL.** The list state (`?status=OPEN&page=2`) can be bookmarked and shared, and it survives a reload.
3. **Server state goes through TanStack Query.** No Redux. Auth state lives in a small React context.
4. **Forms mirror the backend's validation** in Zod, so users get instant feedback. Server-side `errors[]` are mapped back onto form fields.

## 2. Routes & Screens

| Route | Screen | Access |
|---|---|---|
| `/login` | Login (org slug, email, password) | public |
| `/register` | Register organization | public |
| `/tickets` | Ticket list + filters + pagination | any |
| `/tickets/new` | Create ticket | any |
| `/tickets/:ticketId` | Ticket detail: info, actions, comments, history | any (backend decides visibility) |
| `/admin/users` | User management (list, create, edit, deactivate) | ORG_ADMIN |
| `/admin/organization` | Org settings | ORG_ADMIN |
| `*` | 404 page | — |

Route guards: `<RequireAuth>` redirects to `/login?next=…`. `<RequireRole roles={['ORG_ADMIN']}>` shows a "Not authorized" page.

## 3. Wireframes

> **As built:** screenshots of every screen are in [11 — User Guide](11-user-guide.md). For example: [ticket list](images/03-ticket-list-agent.png), [ticket detail](images/04-ticket-detail-agent.png), [customer view](images/10-ticket-detail-customer.png).

**Ticket list**
```
┌──────────────────────────────────────────────────────────────────────────┐
│ Helpdesk · Acme Corp            Tickets  Users(admin)      Alice ▾ Logout │
├──────────────────────────────────────────────────────────────────────────┤
│ [🔍 Search subject/description/#]  Status[Open,In progress ▾]             │
│ Priority[All ▾]  Assignee[Anyone ▾ | Unassigned | Me]   [Reset] [+ New]  │
├────┬───────────────────────────────┬──────────┬────────┬─────────┬───────┤
│ #  │ Subject                       │ Status   │Priority│Assignee │Created│
├────┼───────────────────────────────┼──────────┼────────┼─────────┼───────┤
│ 42 │ Cannot reset password         │ ●OPEN    │ HIGH   │ —       │ 2h ago│
│ 41 │ Invoice PDF is blank          │ ●IN_PROG │ MEDIUM │ Bob     │ 1d ago│
├────┴───────────────────────────────┴──────────┴────────┴─────────┴───────┤
│                               « 1 2 3 … 7 »    Rows: [20 ▾]   134 total   │
└──────────────────────────────────────────────────────────────────────────┘
```
Customers see the same list without the Assignee filter, labelled "My tickets".

**Ticket detail**
```
┌──────────────────────────────────────────┬───────────────────────────────┐
│ #42 Cannot reset password      [Edit]    │ Status:   [IN_PROGRESS ▾]     │
│ Requested by Carol · 2h ago              │   (options = allowedTransitions)│
│                                          │ Priority: [HIGH ▾]            │
│ The reset email never arrives...         │ Assignee: [Bob Agent ▾][Me]   │
│                                          │ Created / Updated / Resolved  │
├──────────────────────────────────────────┴───────────────────────────────┤
│ [Conversation] [History]                                                 │
│ ┌ Bob Agent · AGENT · 1h ─────────────────────────────────────────────┐ │
│ │ We are looking into it.                                              │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ┌ 🔒 INTERNAL NOTE · Bob Agent · 50m ──────── (yellow background) ─────┐ │
│ │ Probably SMTP relay — check logs.                                    │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Write a reply…                                                       │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ( ) Public reply  ( ) Internal note   ← staff only        [Send]         │
└──────────────────────────────────────────────────────────────────────────┘
```

## 4. Project Structure
```
frontend/src/
├── main.tsx, App.tsx, router.tsx, theme.ts
├── api/
│   ├── client.ts          axios instance: baseURL '/api/v1', bearer interceptor, 401 → logout
│   ├── problem.ts         ProblemDetail type + toFieldErrors()
│   └── types.ts           DTO types (generated from OpenAPI via openapi-typescript in Phase 7)
├── auth/                  AuthProvider, useAuth, RequireAuth, RequireRole, LoginPage, RegisterPage
├── features/
│   ├── tickets/           api.ts (hooks), TicketListPage, TicketFilters, TicketTable,
│   │                      CreateTicketPage, TicketDetailPage, TicketActionsPanel,
│   │                      StatusChip, PriorityChip, EditTicketDialog
│   ├── comments/          CommentList, CommentComposer
│   ├── history/           HistoryTimeline
│   └── admin/             UsersPage, UserFormDialog, OrganizationPage
├── components/            AppLayout, ConfirmDialog, ErrorAlert, EmptyState, Pagination
└── utils/                 useUrlState, formatDate
```

## 5. Data Layer

| Query key | Endpoint | Invalidated by |
|---|---|---|
| `['me']` | `GET /auth/me` | login, logout |
| `['tickets', filters]` | `GET /tickets` | create, update, assign, status |
| `['ticket', id]` | `GET /tickets/:id` | update, assign, status, comment (for status change with comment) |
| `['comments', id]` | `GET /tickets/:id/comments` | add comment, status change with comment |
| `['history', id]` | `GET /tickets/:id/history` | update, assign, status |
| `['agents']` | `GET /agents` | — (staleTime 5 min) |
| `['users', filters]` | `GET /users` | user create/update |

Mutations use `onSuccess` invalidation. On `409 CONCURRENT_MODIFICATION`, show a "This ticket was changed by someone else" banner with a **Reload** button.

## 6. Error & UX Handling
- `400` with `errors[]` → `setError(field, message)` on the form.
- `401` → clear auth, redirect to `/login?next=…`, show the toast "Session expired".
- `403` → toast "You don't have permission to do that".
- `404` → in-page "Ticket not found" (this also covers tickets from another tenant).
- `409` → show `detail` from the ProblemDetail in an alert.
- Loading states use skeletons. Empty states include a call to action ("No tickets yet — create one").
- Accessibility: MUI components, labelled inputs, keyboard navigation, and colour plus text on status chips.

## 7. Dev & Build
- `npm run dev` starts Vite on `:5173` with `server.proxy['/api'] = 'http://localhost:8080'` (no CORS issues).
- `npm run build` outputs `dist/`. The Docker image is multi-stage (node → nginx), and `nginx.conf` uses `try_files $uri /index.html` plus `location /api { proxy_pass http://backend:8080; }`.
- Quality: ESLint + Prettier, and `tsc --noEmit` in CI. Vitest + React Testing Library cover key components (TicketFilters URL sync, CommentComposer visibility toggle hidden for customers).
