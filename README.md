# DvP Settlement Engine

Java/Spring Boot backend that simulates post-trade Delivery-versus-Payment settlement for already-matched securities trades.

The engine captures agreed trades as `READY`, then atomically exchanges simulated cash and securities in a single PostgreSQL transaction using deterministic row locking, durable idempotency, immutable settlement journals, and financial invariant checks.

## Current status

Core settlement is complete through Phase 3.

- [x] Phase 1 — PostgreSQL foundation, accounts, Flyway, Spring JDBC, Testcontainers
- [x] Phase 2 — Trade capture, durable idempotency, REST API
- [x] Phase 3 — Atomic DvP settlement, journals, postings, financial inspection
- [ ] Phase 4 — Concurrency and recovery demonstrations
- [ ] Phase 5–6 — Reconciliation
- [ ] Phase 7 — Packaging, CI, and final documentation polish

## What it does

- Captures already-matched trades as `READY`
- Settles due trades atomically in one PostgreSQL transaction
- Locks the trade first, then the four affected accounts in deterministic UUID order
- Validates cash and securities only after locks are held
- Rejects `NOT_DUE`, `INSUFFICIENT_CASH`, and `INSUFFICIENT_SECURITIES`
- Creates one settlement journal and exactly four postings
- Updates all four balances atomically
- Transitions `READY -> SETTLED`
- Replays completed commands without repeating financial effects
- Rolls back all settlement effects on technical failure before commit
- Exposes trades, accounts, attempts, journals, postings, and durable command outcomes through REST

## Settlement flow

```text
capture trade
    ↓
READY
    ↓
claim idempotency key
    ↓
lock trade
    ↓
resolve + lock four accounts
    ↓
validate buyer cash + seller securities
    ↓
create journal
    ↓
create four postings
    ↓
update four balances
    ↓
mark SETTLED
    ↓
record attempt + durable outcome
    ↓
COMMIT
```

If a technical failure occurs before commit, PostgreSQL rolls the entire settlement back.

## Example

Trade:

```text
Alice buys 10 EQ1 from Bob for AUD 500.00
```

Before:

```text
Alice AUD = 100000
Alice EQ1 = 0
Bob AUD   = 0
Bob EQ1   = 10
```

Successful settlement creates:

```text
Alice AUD   DEBIT   50000
Bob AUD     CREDIT  50000
Alice EQ1   CREDIT  10
Bob EQ1     DEBIT   10
```

After:

```text
Alice AUD = 50000
Alice EQ1 = 10
Bob AUD   = 50000
Bob EQ1   = 0
```

The trade becomes `SETTLED` with one journal, four immutable postings, and one `SETTLED` attempt.

## API

```text
POST /v1/trades
GET  /v1/trades/{id}

POST /v1/trades/{id}/settle
GET  /v1/trades/{id}/attempts

GET  /v1/accounts
GET  /v1/journals/{id}
GET  /v1/commands/{key}
```

With the application running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Stack

- Java 21
- Spring Boot
- Spring MVC
- Spring JDBC / `NamedParameterJdbcTemplate`
- PostgreSQL 18
- Flyway
- Testcontainers
- JUnit / AssertJ
- springdoc OpenAPI / Swagger UI
- Maven Wrapper
- Docker Desktop

## Local development

Prerequisites:

- Java 21
- Docker Desktop

Setup:

```bash
cp .env.example .env
docker compose --env-file .env up -d

set -a
source .env
set +a

./mvnw spring-boot:run
```

Stop Spring Boot with `Ctrl+C`.

Stop PostgreSQL when needed:

```bash
docker compose --env-file .env down
```

Seed the demo data:

```bash
docker exec -i dvp-postgres psql -U dvp -d dvp < scripts/seed-demo.sql
```

## Testing

Run the complete build and test suite:

```bash
./mvnw clean verify
```

Phase 3 verification:

```text
Tests run: 200
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

The integration suite runs against real PostgreSQL 18.6 with Testcontainers and covers:

- Flyway V1 → V2 → V3 upgrades
- trade capture and idempotent replay
- deterministic row locking
- due-date and balance validation
- atomic settlement
- rollback after financial writes
- immutable journal and posting history
- balance reconstruction
- cash and security conservation
- REST settlement and inspection

## Database evolution

```text
V1
├── participant
├── asset
└── account

V2
├── trade
└── command_result

V3
├── settlement_journal
├── posting
├── settlement_attempt
├── READY / SETTLED trade support
└── SETTLE_TRADE command support
```

## Documentation

Detailed design and implementation history:

```text
docs/project-spec.md
docs/decisions.md
docs/implementation-plan.md
docs/detailed-plan/phase-1.md
docs/detailed-plan/phase-2.md
docs/detailed-plan/phase-3.md
docs/engineering-log.md
```

## Next work

Future phases will add controlled concurrency and recovery demonstrations, reconciliation, CI, packaging, and final documentation polish.
