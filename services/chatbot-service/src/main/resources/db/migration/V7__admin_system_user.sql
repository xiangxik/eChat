ALTER TABLE admin_users
    ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE admin_users
SET system_managed = TRUE
WHERE username = 'admin';
