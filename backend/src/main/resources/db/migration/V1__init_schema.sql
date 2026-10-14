-- SmartQueue initial schema (PostgreSQL 13+; uses built-in gen_random_uuid())

CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE branches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    address         VARCHAR(300),
    city            VARCHAR(100),
    time_zone       VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_branch_org_name UNIQUE (organization_id, name)
);
CREATE INDEX idx_branches_org ON branches (organization_id);

CREATE TABLE departments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id  UUID         NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_department_branch_name UNIQUE (branch_id, name)
);
CREATE INDEX idx_departments_branch ON departments (branch_id);

CREATE TABLE services (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID         NOT NULL REFERENCES departments (id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_department_name UNIQUE (department_id, name)
);
CREATE INDEX idx_services_department ON services (department_id);

CREATE TABLE queues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_ref      VARCHAR(32)  NOT NULL UNIQUE,
    service_id      UUID         NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    priority_streak INT          NOT NULL DEFAULT 0,
    last_order      BIGINT       NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_queue_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CLOSED')),
    CONSTRAINT uq_queue_service_name UNIQUE (service_id, name)
);
CREATE INDEX idx_queues_service ON queues (service_id);

CREATE TABLE queue_configurations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id                UUID        NOT NULL UNIQUE REFERENCES queues (id) ON DELETE CASCADE,
    token_prefix            VARCHAR(6)  NOT NULL DEFAULT 'A',
    priority_prefix         VARCHAR(6)  NOT NULL DEFAULT 'P',
    starting_number         INT         NOT NULL DEFAULT 1,
    number_padding          INT         NOT NULL DEFAULT 3,
    max_queue_size          INT         NOT NULL DEFAULT 200,
    priority_mode           VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    priority_after_n        INT         NOT NULL DEFAULT 3,
    avg_service_minutes     INT         NOT NULL DEFAULT 5,
    opening_time            TIME,
    closing_time            TIME,
    break_start             TIME,
    break_end               TIME,
    auto_expire_minutes     INT         NOT NULL DEFAULT 240,
    no_show_timeout_minutes INT         NOT NULL DEFAULT 5,
    allow_duplicate_active  BOOLEAN     NOT NULL DEFAULT FALSE,
    direct_serving          BOOLEAN     NOT NULL DEFAULT FALSE,
    near_turn_threshold     INT         NOT NULL DEFAULT 3,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_priority_mode CHECK (priority_mode IN ('DISABLED', 'ALWAYS_FIRST', 'AFTER_N')),
    CONSTRAINT ck_cfg_numbers CHECK (starting_number >= 0 AND max_queue_size > 0
        AND avg_service_minutes > 0 AND priority_after_n > 0
        AND number_padding BETWEEN 1 AND 8
        AND auto_expire_minutes > 0 AND no_show_timeout_minutes > 0)
);

CREATE TABLE counters (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id   UUID         NOT NULL REFERENCES queues (id) ON DELETE CASCADE,
    name       VARCHAR(80)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_counter_queue_name UNIQUE (queue_id, name)
);
CREATE INDEX idx_counters_queue ON counters (queue_id);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations (id) ON DELETE SET NULL,
    email           VARCHAR(190) NOT NULL,
    username        VARCHAR(60)  NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'STAFF', 'CUSTOMER')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE UNIQUE INDEX uq_users_username ON users (lower(username));
CREATE INDEX idx_users_org_role ON users (organization_id, role);

-- Which queues a staff member may operate
CREATE TABLE staff_queue_assignments (
    user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    queue_id UUID NOT NULL REFERENCES queues (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, queue_id)
);
CREATE INDEX idx_staff_assign_queue ON staff_queue_assignments (queue_id);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

-- Per-queue, per-day number sequences. Always mutated while holding the queue row lock.
CREATE TABLE queue_daily_counters (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id     UUID    NOT NULL REFERENCES queues (id) ON DELETE CASCADE,
    service_date DATE    NOT NULL,
    priority     BOOLEAN NOT NULL,
    last_number  INT     NOT NULL,
    CONSTRAINT uq_daily_counter UNIQUE (queue_id, service_date, priority)
);

CREATE TABLE queue_tokens (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference        VARCHAR(40)  NOT NULL UNIQUE,
    queue_id         UUID         NOT NULL REFERENCES queues (id) ON DELETE CASCADE,
    counter_id       UUID REFERENCES counters (id) ON DELETE SET NULL,
    user_id          UUID REFERENCES users (id) ON DELETE SET NULL,
    session_id       VARCHAR(64)  NOT NULL,
    service_date     DATE         NOT NULL,
    sequence_number  INT          NOT NULL,
    token_number     VARCHAR(20)  NOT NULL,
    priority         BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(16)  NOT NULL DEFAULT 'WAITING',
    customer_name    VARCHAR(100),
    queue_order      BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    called_at        TIMESTAMPTZ,
    serving_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_token_status CHECK (status IN
        ('WAITING', 'CALLED', 'SERVING', 'COMPLETED', 'SKIPPED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT uq_token_number_per_day UNIQUE (queue_id, service_date, token_number)
);
-- Hot path: next waiting token per queue, ordered
CREATE INDEX idx_tokens_queue_status_order ON queue_tokens (queue_id, status, queue_order);
CREATE INDEX idx_tokens_queue_date ON queue_tokens (queue_id, service_date);
CREATE INDEX idx_tokens_session ON queue_tokens (session_id);
CREATE INDEX idx_tokens_user ON queue_tokens (user_id);
CREATE INDEX idx_tokens_created ON queue_tokens (created_at);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id   UUID REFERENCES queue_tokens (id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users (id) ON DELETE CASCADE,
    channel    VARCHAR(16)  NOT NULL DEFAULT 'IN_APP',
    type       VARCHAR(32)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP', 'BROWSER', 'EMAIL', 'SMS'))
);
CREATE INDEX idx_notifications_token ON notifications (token_id, created_at DESC);
CREATE INDEX idx_notifications_user ON notifications (user_id, read);

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID,
    actor_label VARCHAR(190) NOT NULL,
    action      VARCHAR(48)  NOT NULL,
    entity_type VARCHAR(48)  NOT NULL,
    entity_id   VARCHAR(64),
    ip_address  VARCHAR(64),
    metadata    TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
