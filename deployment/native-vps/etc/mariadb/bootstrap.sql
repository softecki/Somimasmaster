-- Somimas MariaDB bootstrap
-- Creates databases and application users for Fineract and the SaaS control plane.
-- Run once as root during server bootstrap:
--   sudo mariadb < /etc/somimas/mariadb/bootstrap.sql
-- If root socket login is denied, log in with the root password instead:
--   mariadb -u root -p < /etc/somimas/mariadb/bootstrap.sql
--
-- NOTE: skip-name-resolve is ON (99-somimas.cnf), so TCP connections from
-- 127.0.0.1 do not match 'localhost' accounts. Every user is therefore
-- created for BOTH 'localhost' (socket) and '127.0.0.1' (TCP).

-- ---------------------------------------------------------------------------
-- Root account: keep passwordless sudo socket login AND allow TCP login
-- with a password.
-- ---------------------------------------------------------------------------
ALTER USER 'root'@'localhost'
  IDENTIFIED VIA unix_socket
  OR mysql_native_password USING PASSWORD('skdcnwauicn2ucnaecasdsajdnizucawencascdca');

CREATE USER IF NOT EXISTS 'root'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'root'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;

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
-- Control plane: somimas_control database only
-- ---------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'somimas_control'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_control'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
CREATE USER IF NOT EXISTS 'somimas_control'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_control'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `somimas_control`.*
  TO 'somimas_control'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `somimas_control`.*
  TO 'somimas_control'@'127.0.0.1';

-- ---------------------------------------------------------------------------
-- Fineract: tenant registry, default tenant, and dynamically provisioned
-- somimas_* tenant DBs
-- ---------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'somimas_fineract'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_fineract'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
CREATE USER IF NOT EXISTS 'somimas_fineract'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_fineract'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `fineract_tenants`.*
  TO 'somimas_fineract'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `fineract_tenants`.*
  TO 'somimas_fineract'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `fineract_default`.*
  TO 'somimas_fineract'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `fineract_default`.*
  TO 'somimas_fineract'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `somimas\_%`.*
  TO 'somimas_fineract'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE, CREATE TEMPORARY TABLES
  ON `somimas\_%`.*
  TO 'somimas_fineract'@'127.0.0.1';

-- ---------------------------------------------------------------------------
-- Provisioner: creates per-tenant databases/users during self-service signup
-- ---------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'somimas_provisioner'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_provisioner'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
CREATE USER IF NOT EXISTS 'somimas_provisioner'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_provisioner'@'127.0.0.1'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
GRANT CREATE, CREATE USER ON *.* TO 'somimas_provisioner'@'localhost' WITH GRANT OPTION;
GRANT CREATE, CREATE USER ON *.* TO 'somimas_provisioner'@'127.0.0.1' WITH GRANT OPTION;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE
  ON `fineract_tenants`.*
  TO 'somimas_provisioner'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE
  ON `fineract_tenants`.*
  TO 'somimas_provisioner'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE
  ON `somimas\_%`.*
  TO 'somimas_provisioner'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, LOCK TABLES, EXECUTE
  ON `somimas\_%`.*
  TO 'somimas_provisioner'@'127.0.0.1';
-- Provisioner must also grant per-tenant privileges to the dynamically created
-- tenant users, which requires holding those privileges WITH GRANT OPTION.
GRANT ALL PRIVILEGES ON `somimas\_%`.* TO 'somimas_provisioner'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON `somimas\_%`.* TO 'somimas_provisioner'@'127.0.0.1' WITH GRANT OPTION;

-- ---------------------------------------------------------------------------
-- Backup operator: read-only + lock for consistent dumps (no DDL)
-- ---------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'somimas_backup'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
ALTER USER 'somimas_backup'@'localhost'
  IDENTIFIED BY 'skdcnwauicn2ucnaecasdsajdnizucawencascdca';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES, PROCESS, RELOAD
  ON *.*
  TO 'somimas_backup'@'localhost';

FLUSH PRIVILEGES;
