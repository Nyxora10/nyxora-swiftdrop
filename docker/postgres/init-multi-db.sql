-- docker/postgres/init-multi-db.sql
CREATE DATABASE auth_db;
CREATE DATABASE parcel_db;
CREATE DATABASE notification_db;

\c auth_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c parcel_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c notification_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";