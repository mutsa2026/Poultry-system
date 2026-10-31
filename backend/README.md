# Poultry Backend (Phase 1–2 slice)

Spring Boot 3.3 / Java 17 / PostgreSQL / Flyway.

The backend listens on port 8080 by default; set `SERVER_PORT` to override it.

## Run
    createdb poultry
    export DB_URL=jdbc:postgresql://localhost:5433/poultry DB_USER=poultry DB_PASSWORD=change-me
    export OWNER_USERNAME=owner OWNER_PASSWORD='choose-a-strong-one'
    mvn spring-boot:run

## Test (needs Docker for Testcontainers)
    mvn test

## Implemented
- Flyway V1: users, audit_logs, suppliers, poultry_batches, stock_movements (ledger), mortality_events
- OWNER / WORKER roles with method security; deactivated users cannot log in
- POST/GET /api/v1/batches, GET /batches/{id}/movements
- POST /api/v1/batches/{id}/mortality (row-locked, ledger-based, audited)
- GET  /api/v1/batches/{id}/mortality-rate?from=&to=   (deaths / initial quantity)

## Known placeholders
- Auth uses HTTP Basic; swap for JSON login + session cookie (FR-01) before the frontend work.
- Stock is derived from SUM(stock_movements); the batch row lock serialises writers.
  Future sale/processing services must use BatchRepository.findByIdForUpdate too.
