# Helpdesk — Frontend

React 18 · TypeScript (strict) · Vite 6 · React Router 6 · TanStack Query 5 · React Hook Form + Zod · MUI 6 · Axios.
Design reference: [`docs/06-frontend-design.md`](../docs/06-frontend-design.md); API contract: [`docs/04-api-design.md`](../docs/04-api-design.md).

## Scripts

| Command             | What it does                                                                       |
| ------------------- | ---------------------------------------------------------------------------------- |
| `npm run dev`       | Vite dev server on http://localhost:5173, proxies `/api` → `http://localhost:8080` |
| `npm run build`     | Type-check (`tsc --noEmit`) then production build into `dist/`                     |
| `npm run preview`   | Serve the built `dist/` locally                                                    |
| `npm run lint`      | ESLint (flat config, typescript-eslint, react-hooks)                               |
| `npm run typecheck` | `tsc --noEmit`                                                                     |
| `npm test`          | Vitest + React Testing Library (jsdom), single run                                 |
| `npm run format`    | Prettier write (`format:check` to verify only)                                     |

Docker: `docker build -t helpdesk-frontend .` — multi-stage (node:20-alpine → nginx:1.27-alpine).
`nginx.conf` serves the SPA (`try_files $uri /index.html`) and proxies `/api/` to `http://backend:8080`.

Demo logins (seeded by the backend): org `acme` or `globex`, `admin@<org>.test`, `agent1@<org>.test`,
`customer1@<org>.test`, password `Password123!`.

## Structure

```
src/
├── main.tsx, App.tsx, router.tsx, theme.ts, queryClient.ts
├── api/          client.ts (axios, bearer + 401 handling), problem.ts (RFC 7807 helpers),
│                 types.ts (hand-written DTOs), queryKeys.ts
├── auth/         AuthProvider, useAuth, tokenStorage, RequireAuth, RequireRole, Login/Register pages
├── features/
│   ├── tickets/  api.ts hooks, list (filters/table/stats), create, detail, actions panel, chips, dialogs
│   ├── comments/ CommentList, CommentComposer
│   ├── history/  HistoryTimeline
│   └── admin/    UsersPage, UserFormDialog, OrganizationPage
├── components/   AppLayout, ConfirmDialog, ErrorAlert, EmptyState, Pagination, toasts, status pages
├── utils/        useUrlState, formatDate, labels, useDebouncedValue
└── test/         render helpers, fixtures, page smoke tests
```

## Conventions

- **Backend decides.** Actions are shown from `TicketDetail.permissions` and `allowedTransitions`; the UI
  never re-implements business rules (it only mirrors field validation in Zod for instant feedback).
- **Filters live in the URL** (`useUrlState` + a codec per screen). Page numbers in the URL are **1-based**
  (`?page=2`) and converted to the API's 0-based `page`. Defaults are omitted from the URL.
- **Server state = TanStack Query** (keys in `api/queryKeys.ts`); mutations update/invalidate on success.
  Auth state is a small React context; the JWT is kept in memory and mirrored to `sessionStorage`.
- **Errors:** `400 errors[]` → `setError` on the form; `401` → logout + `/login?next=…` + "Session expired"
  toast; `403` → global toast; `404` → in-page "not found"; `409` → the problem `detail` in an alert,
  and `CONCURRENT_MODIFICATION` → "changed by someone else" banner with **Reload**.
- Toasts: `useToast()` in components, `toastBus.emit()` from non-React code.
