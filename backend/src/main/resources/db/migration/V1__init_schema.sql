-- Helpdesk schema v1 — see docs/03-domain-and-data-model.md §4
-- Every tenant-owned table carries organization_id. Composite foreign keys
-- (organization_id, x_id) make cross-tenant references impossible in the database itself.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ───────────── organizations ─────────────
CREATE TABLE organizations (
    id               UUID         PRIMARY KEY,
    name             VARCHAR(120) NOT NULL,
    slug             VARCHAR(50)  NOT NULL,
    ticket_sequence  BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_organizations_slug UNIQUE (slug),
    CONSTRAINT ck_organizations_slug CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$')
);

-- ───────────── users ─────────────
CREATE TABLE users (
    id               UUID         PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    full_name        VARCHAR(120) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at    TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_org_email UNIQUE (organization_id, email),
    CONSTRAINT uq_users_org_id    UNIQUE (organization_id, id),   -- target for composite FKs
    CONSTRAINT ck_users_role      CHECK (role IN ('ORG_ADMIN','AGENT','CUSTOMER')),
    CONSTRAINT ck_users_email_lc  CHECK (email = lower(email))
);
CREATE INDEX ix_users_org_role ON users (organization_id, role) WHERE active;

-- ───────────── tickets ─────────────
CREATE TABLE tickets (
    id               UUID         PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    number           BIGINT       NOT NULL,
    subject          VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    priority         VARCHAR(10)  NOT NULL,
    priority_rank    SMALLINT GENERATED ALWAYS AS (
                        CASE priority WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2
                                      WHEN 'HIGH' THEN 3 WHEN 'URGENT' THEN 4 END) STORED,
    status           VARCHAR(15)  NOT NULL,
    requester_id     UUID         NOT NULL,
    assignee_id      UUID,
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_tickets_org_number UNIQUE (organization_id, number),
    CONSTRAINT uq_tickets_org_id     UNIQUE (organization_id, id),
    CONSTRAINT ck_tickets_priority   CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_tickets_status     CHECK (status IN ('OPEN','IN_PROGRESS','ON_HOLD','RESOLVED','CLOSED')),
    -- Composite FKs: requester/assignee MUST belong to the same organization as the ticket
    CONSTRAINT fk_tickets_requester FOREIGN KEY (organization_id, requester_id) REFERENCES users (organization_id, id),
    CONSTRAINT fk_tickets_assignee  FOREIGN KEY (organization_id, assignee_id)  REFERENCES users (organization_id, id)
);
CREATE INDEX ix_tickets_org_status_created    ON tickets (organization_id, status, created_at DESC);
CREATE INDEX ix_tickets_org_assignee_status   ON tickets (organization_id, assignee_id, status);
CREATE INDEX ix_tickets_org_requester_created ON tickets (organization_id, requester_id, created_at DESC);
CREATE INDEX ix_tickets_org_priority          ON tickets (organization_id, priority_rank DESC);
CREATE INDEX ix_tickets_org_created           ON tickets (organization_id, created_at DESC);
CREATE INDEX ix_tickets_subject_trgm          ON tickets USING gin (subject gin_trgm_ops);
CREATE INDEX ix_tickets_description_trgm      ON tickets USING gin (description gin_trgm_ops);

-- ───────────── ticket_comments ─────────────
CREATE TABLE ticket_comments (
    id               UUID        PRIMARY KEY,
    organization_id  UUID        NOT NULL,
    ticket_id        UUID        NOT NULL,
    author_id        UUID        NOT NULL,
    body             TEXT        NOT NULL,
    visibility       VARCHAR(10) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_comments_visibility CHECK (visibility IN ('PUBLIC','INTERNAL')),
    CONSTRAINT fk_comments_ticket FOREIGN KEY (organization_id, ticket_id) REFERENCES tickets (organization_id, id),
    CONSTRAINT fk_comments_author FOREIGN KEY (organization_id, author_id) REFERENCES users (organization_id, id)
);
CREATE INDEX ix_comments_ticket_created ON ticket_comments (ticket_id, created_at);

-- ───────────── ticket_history ─────────────
CREATE TABLE ticket_history (
    id               UUID        PRIMARY KEY,
    organization_id  UUID        NOT NULL,
    ticket_id        UUID        NOT NULL,
    actor_id         UUID        NOT NULL,
    event_type       VARCHAR(20) NOT NULL,
    old_value        VARCHAR(100),
    new_value        VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_history_event CHECK (event_type IN ('CREATED','STATUS_CHANGED','ASSIGNEE_CHANGED','PRIORITY_CHANGED')),
    CONSTRAINT fk_history_ticket FOREIGN KEY (organization_id, ticket_id) REFERENCES tickets (organization_id, id),
    CONSTRAINT fk_history_actor  FOREIGN KEY (organization_id, actor_id)  REFERENCES users (organization_id, id)
);
CREATE INDEX ix_history_ticket_created ON ticket_history (ticket_id, created_at);
