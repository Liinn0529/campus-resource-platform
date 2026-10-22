COMMENT ON SCHEMA reservation IS 'Service-owned schema; business tables follow their authorized phases';

REVOKE ALL PRIVILEGES ON TABLE reservation.flyway_schema_history FROM reservation_app;
