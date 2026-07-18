-- Somimas MariaDB bootstrap
-- Creates databases and least-privilege users for Fineract and the SaaS control plane.
-- Run once as root during server bootstrap:
--   sudo mariadb < /etc/somimas/mariadb/bootstrap.sql
--
-- Replace placeholder passwords before running in production.

-- ---------------------------------------------------------------------------
-- Databases
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `somimas_control`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `fineract_tenants`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `fineract_default`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Application users (localhost only)
-- ---------------------------------------------------------------------------

-- Control plane: somimas_control database only
CREATE USER IF NOT EXISTS 'somimas_control'@'localhost'
  IDENTIFIED BY 'CHANGE_ME_CONTROL_PLANE_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `somimas_control`.*
  TO 'somimas_control'@'localhost';

-- Fineract: tenant registry, default tenant, and dynamically provisioned somimas_* tenant DBs
CREATE USER IF NOT EXISTS 'somimas_fineract'@'localhost'
  IDENTIFIED BY 'CHANGE_ME_FINERACT_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `fineract_tenants`.*
  TO 'somimas_fineract'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `fineract_default`.*
  TO 'somimas_fineract'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `somimas\_%`.*
  TO 'somimas_fineract'@'localhost';

-- Provisioner: creates per-tenant databases/users during self-service signup
CREATE USER IF NOT EXISTS 'somimas_provisioner'@'localhost'
  IDENTIFIED BY 'CHANGE_ME_PROVISIONER_PASSWORD';
GRANT CREATE, CREATE USER ON *.* TO 'somimas_provisioner'@'localhost' WITH GRANT OPTION;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE
  ON `fineract_tenants`.*
  TO 'somimas_provisioner'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE
  ON `somimas\_%`.*
  TO 'somimas_provisioner'@'localhost';

-- Backup operator: read-only + lock for consistent dumps (no DDL)
CREATE USER IF NOT EXISTS 'somimas_backup'@'localhost'
  IDENTIFIED BY 'CHANGE_ME_BACKUP_DB_PASSWORD';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES, PROCESS, RELOAD
  ON *.*
  TO 'somimas_backup'@'localhost';

FLUSH PRIVILEGES;
