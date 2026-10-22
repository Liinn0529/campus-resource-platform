COMMENT ON SCHEMA maintenance IS 'Service-owned schema; business tables follow their authorized phases';

REVOKE ALL PRIVILEGES ON TABLE maintenance.flyway_schema_history FROM maintenance_app;
