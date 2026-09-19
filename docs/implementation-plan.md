# Implementation Plan

This document defines the high-level implementation sequence for the DvP Settlement and Reconciliation Engine.

It implements:

* `docs/project-spec.md`

  * Defines what the finished system must do.
* `docs/decisions.md`

  * Defines accepted architecture decisions.
* `docs/detailed-plan/phase-N.md`

  * Defines the detailed implementation steps for each phase.
* `docs/engineering-log.md`

  * Records what actually happened during implementation.

The project specification and architecture decisions are authoritative. This implementation plan defines sequencing only.

# Implementation Workflow

Each phase follows the same process:

```text
implementation-plan.md
        |
        v
inspect current repository
        |
        v
create detailed-plan/phase-N.md
        |
        v
review detailed phase plan
        |
        v
implement phase
        |
        v
verify phase exit criteria
        |
        v
record actual work
        |
        v
next phase
```

Before implementing a phase:

1. Inspect the current repository.
2. Read the relevant project documents.
3. Create or review `docs/detailed-plan/phase-N.md`.
4. Break the phase into numbered steps such as `1.1`, `1.2`, `1.3`.
5. Explain what, why and how before implementation.
6. Resolve any open contract decision required by that phase.
7. Implement only the approved phase.
8. Verify the phase exit criteria.
9. Record actual work using the same numbering.
10. Stop before beginning the next phase.

Do not silently change project scope or accepted architecture decisions during implementation.

# Project Conventions

* Java 21
* Spring Boot
* Spring MVC
* Spring JDBC with `NamedParameterJdbcTemplate`
* PostgreSQL
* Flyway
* JUnit
* AssertJ
* Testcontainers
* Maven with Maven Wrapper
* base package `com.jasonwidjaja.dvp`
* one modular Spring Boot application
* one PostgreSQL database

Do not introduce:

* JPA or Hibernate
* Kafka
* microservices
* Kubernetes
* frontend work
* event sourcing
* distributed database transactions

The transactional outbox remains optional and is not part of the core implementation plan.

# Critical Path

The highest-priority path is:

```text
Java + PostgreSQL foundation
        |
        v
Trade Capture
        |
        v
Atomic Settlement
        |
        v
Journal Inspection
        |
        v
Concurrency + Retry + Recovery
        |
        v
Reconciliation
        |
        v
Load Experiment
        |
        v
Final Polish
```

Correct financial behaviour takes priority over deployment or presentation polish.

The first major project milestone is:

```text
Alice has AUD 1,000
Bob has 10 EQ1
        |
        v
capture trade
        |
        v
settle trade
        |
        v
Alice: AUD 500 + 10 EQ1
Bob:   AUD 500 + 0 EQ1
        |
        v
exactly one journal
exactly four postings
        |
        v
PostgreSQL integration tests pass
```

Reach this vertical slice before spending significant time on CI, Docker packaging, Swagger polish or database permission hardening.

# Phase 1: Java and PostgreSQL Foundation

Goal:

Establish the minimum Java, Spring Boot and PostgreSQL foundation required before trade capture can be implemented.

Detailed execution:

* `docs/detailed-plan/phase-1.md`

The detailed plan should cover areas such as:

* repository baseline and `AGENTS.md`
* Java 21
* Maven and Maven Wrapper
* `pom.xml`
* Spring Boot application skeleton
* standard Java/Spring source structure
* application configuration
* PostgreSQL connection
* Spring JDBC
* Flyway
* participant, asset and account schema
* deterministic Alice/Bob seed data
* Testcontainers
* PostgreSQL integration tests

The exact steps and dependency order belong in `phase-1.md`.

Core data established:

```text
Participants:
- Alice
- Bob

Assets:
- AUD
- EQ1

Accounts:
- Alice AUD = 100000 minor units
- Alice EQ1 = 0
- Bob AUD = 0
- Bob EQ1 = 10
```

No trades exist yet.

Exit criteria:

* Java 21 project builds.
* Maven Wrapper works.
* Spring Boot application starts.
* PostgreSQL connection works.
* Flyway applies the first migration to a clean database.
* participant, asset and account tables exist.
* deterministic seed data loads safely.
* seeded accounts can be read through Spring JDBC.
* negative balances are rejected.
* duplicate participant/asset accounts are rejected.
* Testcontainers successfully runs tests against PostgreSQL.
* `./mvnw verify` passes.

# Phase 2: Trade Capture and Read-Back

Goal:

Implement F1 Trade Capture and the capture side of F4 Idempotency.

Dependencies:

* Phase 1 complete.

Core work:

* define the trade domain model
* define the capture request and response
* add trade persistence
* add durable command identity
* validate trade terms
* enforce unique external trade identity
* store new trades as `READY`
* implement trade capture API
* implement trade read API
* implement account inspection API
* make repeated capture requests safe

Core endpoints:

```text
POST /v1/trades
GET  /v1/trades/{id}
GET  /v1/accounts
```

Exit criteria:

* valid matched trade can be captured
* invalid trade is rejected
* duplicate business identity cannot create a second trade
* same idempotent request returns the recorded result
* conflicting reuse is rejected
* captured trade can be inspected
* account balances remain unchanged by trade capture
* accumulated tests pass

# Phase 3: Atomic Settlement and Financial Inspection

Goal:

Implement the first complete DvP financial vertical slice.

Dependencies:

* Phase 2 complete.

Core work:

* add settlement journal
* add financial postings
* add settlement attempts
* add trade-to-journal relationship
* implement PostgreSQL settlement transaction
* implement trade row locking
* implement deterministic account locking
* validate available cash and securities
* create exactly four postings
* update balances
* mark trade `SETTLED`
* persist settlement outcome
* add journal inspection
* complete command and settlement-attempt inspection

Core settlement sequence:

```text
settlement request
      |
      v
idempotency check
      |
      v
lock trade
      |
      v
lock accounts
      |
      v
validate resources
      |
      v
journal + postings
      |
      v
update balances
      |
      v
mark SETTLED
      |
      v
commit
```

Core endpoints introduced:

```text
POST /v1/trades/{id}/settle
GET  /v1/journals/{id}
GET  /v1/commands/{key}
GET  /v1/trades/{id}/attempts
```

Exit criteria:

* Alice/Bob settlement succeeds end to end
* exactly one journal is created
* exactly four correct postings are created
* balances are correct
* trade becomes `SETTLED`
* insufficient cash causes no financial movement
* insufficient securities causes no financial movement
* basic pre-commit failure rolls back completely
* balance reconstruction matches stored balances
* accumulated tests pass

# Phase 4: Concurrency, Idempotency and Failure Recovery

Goal:

Prove that settlement remains correct under competing requests, retries and failures.

Dependencies:

* Phase 3 complete.

Core work:

* controlled cash contention test
* controlled securities contention test
* same-trade race
* same-idempotency-key race
* competing capture requests
* deterministic database locking verification
* failure before commit
* response loss after commit
* retry after ambiguous response
* recovery across application restart

Required demonstrations include:

```text
Alice cash = AUD 1,000

Trade A = AUD 800
Trade B = AUD 800

both requested concurrently

Expected:
- one succeeds
- one fails
- Alice ends with AUD 200
```

Exit criteria:

* cash cannot be overspent
* securities cannot be oversold
* trade cannot settle twice
* same command cannot create duplicate financial effects
* pre-commit failure leaves no partial state
* post-commit retry returns the original outcome
* retries remain safe across restart
* unrelated account activity is not globally serialized
* all financial invariants remain valid

At this point the core settlement engine should be considered technically strong before reconciliation work begins.

# Phase 5: Reconciliation Inputs

Goal:

Create stable internal and external evidence that can later be reconciled.

Dependencies:

* Phase 4 complete.

Core work:

* capture a consistent internal reconciliation state
* store the relevant trade and settlement evidence
* upload simulated external settlement CSV
* preserve report rows and duplicate references
* validate input safely
* ensure later settlement activity cannot alter previously saved reconciliation inputs

Core endpoints:

```text
POST /v1/reconciliation/scopes
POST /v1/reconciliation/reports
```

Exit criteria:

* internal state can be captured consistently
* external CSV can be uploaded
* original reconciliation evidence is preserved
* duplicate external records are not silently overwritten
* later settlements do not change saved reconciliation evidence
* reconciliation input operations do not modify financial state

# Phase 6: Reconciliation Engine

Goal:

Compare saved internal and external records and produce deterministic reconciliation results.

Dependencies:

* Phase 5 complete.

Required result types:

```text
MATCHED
MISSING_EXTERNAL
MISSING_INTERNAL
DUPLICATE_EXTERNAL
FIELD_MISMATCH
```

Compare:

* buyer
* seller
* security
* quantity
* cash amount
* settlement date
* settlement status

Core endpoints:

```text
POST /v1/reconciliation/runs
GET  /v1/reconciliation/runs/{id}
```

Required fixtures:

* clean report
* missing external record
* missing internal record
* duplicate external record
* incorrect amount
* incorrect quantity
* incorrect status
* other required field mismatches

Exit criteria:

* all required discrepancy types are detected
* results are reproducible from the same saved inputs
* findings contain enough information to explain the disagreement
* reconciliation does not modify financial state
* F1 through F7 work together successfully

# Phase 7: Load Experiment and Final Project Polish

Goal:

Complete the scalability experiment and all supporting requirements needed for a polished repository.

Dependencies:

* Phases 1 through 6 complete.

## Load experiment

Run two workload types.

Independent accounts:

```text
Trade 1: A -> B
Trade 2: C -> D
Trade 3: E -> F
Trade 4: G -> H
```

Contended account:

```text
            Seller B
               ^
               |
Seller C <- Buyer A -> Seller D
               |
               v
            Seller E
```

Test increasing client concurrency such as:

```text
1
4
8
```

Record:

* attempted transactions per second
* successful settlements per second
* p50 latency
* p95 latency
* successful settlements
* business rejections
* technical errors

The experiment must explain observed behaviour rather than claim production financial-system capacity.

## Final engineering requirements

Complete:

* restricted runtime PostgreSQL permissions
* application Dockerfile
* Docker Compose startup
* health endpoint
* OpenAPI
* Swagger/API explorer
* GitHub Actions
* final README
* final demo scripts and fixtures
* full clean-start verification

Exit criteria:

* full automated test suite passes
* restricted runtime role preserves historical records
* application can be started from a clean checkout
* Swagger/OpenAPI exposes the supported API
* complete demo workflow is documented
* reconciliation demo works
* concurrency/failure demos work
* load results are documented
* GitHub Actions passes
* project definition of done is satisfied

# Optional Extension

Transactional outbox is optional.

Only consider it after every core exit criterion passes.

If implemented, it should demonstrate:

```text
settlement commits
      |
      v
outbox event stored
      |
      v
event delivered
      |
      v
acknowledgement lost
      |
      v
delivery retried
      |
      v
receiver deduplicates
```

Kafka is not required.

# Open Contract Decisions

Some public/domain details still need to be fixed before the phase that depends on them.

These should be resolved in the relevant detailed phase plan, not silently decided during implementation.

## C1: Numeric representation

Resolve before the Phase 1 database schema is finalized.

Questions:

* AUD represented in integer minor units?
* securities restricted to whole units?
* allowed numeric bounds?
* zero-value trades allowed?

## C2: Settlement due-date rule

Resolve before Phase 3.

Questions:

* exact definition of a trade being due
* treatment of overdue trades
* business timezone used for the simplified model

## C3: Idempotency-key scope

Resolve before Phase 2 command persistence.

Questions:

* HTTP transport for the key
* whether the namespace is global or operation-scoped
* how command lookup remains unambiguous

## C4: Durable command-result policy

Resolve before Phase 2/3 API behaviour is fixed.

Questions:

* which validation/business failures receive durable results
* exact retry behaviour
* exact HTTP status/error contract

## C5: Repeated trade identity

Resolve before Phase 2.

Question:

If a new command key submits the same external trade reference with identical terms, should the system return the existing trade or return a conflict?

Different terms must always conflict.

## C6: Reconciliation scope

Resolve before Phase 5.

Question:

What exact population of internal trades belongs to one saved reconciliation scope?

## C7: External reconciliation format

Resolve before Phase 5.

Define:

* CSV fields
* identifiers
* encoding
* status representation
* input limits
* malformed-report behaviour

## C8: Duplicate reconciliation behaviour

Resolve before Phase 6.

Define how duplicate external trade references interact with:

* `DUPLICATE_EXTERNAL`
* `FIELD_MISMATCH`
* `MISSING_INTERNAL`

All original external rows must remain preserved.

# Current State

Planning and architecture are complete.

Current authoritative documentation:

```text
docs/
├── project-spec.md
├── decisions.md
├── implementation-plan.md
└── detailed-plan/
```

Detailed phase files are created immediately before their corresponding phase is implemented.

The next planning artifact is:

```text
docs/detailed-plan/phase-1.md
```

It should describe the complete Phase 1 implementation in enough detail to understand:

* what will be done
* why it is needed
* how it works
* which files and tools are involved
* how each step will be verified

After Phase 1 is approved, implementation begins.

# One-Week Priority

The project is time constrained.

Prioritize:

```text
1. working Java/PostgreSQL foundation
2. capture
3. atomic settlement
4. concurrency/idempotency/recovery
5. reconciliation
6. load experiment
7. operational and repository polish
```

Do not sacrifice correctness to meet an arbitrary calendar estimate.

If time becomes constrained, optional work is cut before required core behaviour.
