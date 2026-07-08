CREATE TABLE admin_permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_role_permissions (
    role_id BIGINT NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES admin_permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE admin_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(160) NOT NULL DEFAULT 'default',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_user_roles (
    user_id BIGINT NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES admin_roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE admin_user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX admin_user_roles_role_id_idx ON admin_user_roles(role_id);
CREATE INDEX admin_role_permissions_permission_id_idx ON admin_role_permissions(permission_id);
CREATE INDEX admin_users_tenant_enabled_idx ON admin_users(tenant_id, enabled);
CREATE INDEX admin_user_sessions_user_id_idx ON admin_user_sessions(user_id);
CREATE INDEX admin_user_sessions_expires_at_idx ON admin_user_sessions(expires_at);

INSERT INTO admin_permissions (code, name, description)
VALUES
    ('ADMIN_READ', 'Admin Read', 'Read admin console resources'),
    ('ADMIN_WRITE', 'Admin Write', 'Create, update, and delete admin console resources'),
    ('AUDIT_READ', 'Audit Read', 'Read audit logs'),
    ('RBAC_MANAGE', 'RBAC Manage', 'Manage admin users, roles, and permissions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_roles (code, name, description, system_role)
VALUES
    ('SUPER_ADMIN', 'Super Admin', 'Full administrative access across tenants', TRUE),
    ('ADMIN', 'Admin', 'Administrative read/write access for one tenant', TRUE),
    ('AUDITOR', 'Auditor', 'Audit log and read-only access', TRUE),
    ('VIEWER', 'Viewer', 'Read-only admin console access', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code IN ('ADMIN_READ', 'ADMIN_WRITE', 'AUDIT_READ', 'RBAC_MANAGE')
WHERE role.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code IN ('ADMIN_READ', 'ADMIN_WRITE')
WHERE role.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code IN ('ADMIN_READ', 'AUDIT_READ')
WHERE role.code = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM admin_roles role
JOIN admin_permissions permission ON permission.code = 'ADMIN_READ'
WHERE role.code = 'VIEWER'
ON CONFLICT DO NOTHING;
