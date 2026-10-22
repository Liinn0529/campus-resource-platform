COMMENT ON SCHEMA classroom IS 'Service-owned schema; business tables follow their authorized phases';

REVOKE ALL PRIVILEGES ON TABLE classroom.flyway_schema_history FROM classroom_app;
