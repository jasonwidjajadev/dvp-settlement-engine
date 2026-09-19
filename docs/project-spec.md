# DvP Settlement and Reconciliation Engine

## 1. Project Overview

### What is this project?

The DvP Settlement and Reconciliation Engine is a backend system that simulates what happens **after two parties have already agreed to buy and sell securities**.

The system accepts an already-matched trade and safely exchanges:

* cash from the buyer to the seller
* securities from the seller to the buyer

The key rule is **Delivery-versus-Payment (DvP)**:

> Cash and securities must transfer together, or neither transfer happens.

The system also maintains a permanent settlement history and compares its internal records with a simulated external settlement report to detect discrepancies.

### Why am I building this?

This project is intended to model a simplified **post-trade transaction-processing system**.

The trade has already happened. The problem is now making sure that the resulting cash and securities movements are processed correctly.

```text
trade already happened
        |
        v
now make sure
cash + securities
settle correctly
```

This creates several important backend and systems-engineering problems:

* atomicity
* database transactions
* concurrency on financial balances
* idempotency
* retries
* failure recovery
* reconciliation
* audit history
* correctness of financial state
* basic scalability and contention

The goal is to reproduce these engineering problems at a small enough scale that the complete system can be built, tested and understood end to end.

The project has two connected halves:

```text
                  DvP PROJECT

          SETTLEMENT              RECONCILIATION
              |                         |
              v                         v
      move cash + securities     compare internal records
         safely and once         with external records
              |                         |
              +------------+------------+
                           |
                           v
                    POST-TRADE
                    ENGINEERING
```

#### Settlement

The settlement side asks:

> How can the system ensure that the buyer receives the securities if and only if the seller receives payment?

The system must preserve that guarantee even when:

* two transactions compete for the same cash or securities
* a request is submitted more than once
* the application fails during processing
* the database commits but the client does not receive the response

This makes settlement more than a simple database update. The system has to maintain financial correctness across concurrency, retries and failures.

#### Reconciliation

Correct internal processing does not guarantee that the system agrees with external records.

In a real post-trade workflow, internal trade, cash and position records may need to be compared with records maintained by another system involved in clearing, custody or settlement.

```text
Internal records
      |
      v
External records
      |
      v
Do they agree?
```

The reconciliation component therefore compares a saved internal state with a simulated external report and detects:

* missing records
* extra records
* duplicate records
* incorrect amounts
* incorrect quantities
* incorrect settlement status

Reconciliation does not automatically modify either side. Its purpose is to identify and explain disagreement.

Together, settlement and reconciliation let the project explore the broader problem of **post-trade correctness** rather than only implementing one atomic database transaction.

The project deliberately uses a much smaller domain than a real financial institution. It does not attempt to reproduce production scale, regulation or complete financial-market infrastructure.

Instead, it focuses on a small set of industry-realistic engineering concerns:

```text
CORRECTNESS
atomic settlement
financial invariants

      +

CONCURRENCY
competing balances
database locking

      +

RELIABILITY
idempotency
retry safety
failure recovery

      +

AUDITABILITY
immutable settlement history
traceable financial effects

      +

RECONCILIATION
internal vs external records

      +

SCALABILITY
basic throughput
latency
contention measurement
```

---

## 2. Simple Example

Alice wants to buy **10 shares of EQ1 from Bob for AUD 500**.

Before settlement:

```text
Alice                              Bob

Cash: $1,000                       Cash: $0
EQ1:  0 shares                     EQ1: 10 shares

                AGREED TRADE
            10 EQ1 for AUD 500
                     |
                     v
            +----------------+
            |   DvP Engine   |
            +----------------+
                     |
                     v

After successful settlement:

Alice                              Bob

Cash: $500                         Cash: $500
EQ1: 10 shares                     EQ1: 0 shares
```

The critical guarantee is:

```text
CASH MOVES
    AND
SECURITIES MOVE

     OR

NOTHING MOVES
```

Alice must never lose AUD 500 without receiving the shares.

Bob must never lose the shares without receiving AUD 500.

If Alice only has AUD 300, settlement is rejected and neither account changes.

---

## 3. Reconciliation Example

Suppose the settlement engine records:

```text
Trade T-001
Cash amount: AUD 500
Quantity: 10 EQ1
Status: SETTLED
```

The simulated external report says:

```text
Trade T-001
Cash amount: AUD 550
Quantity: 10 EQ1
Status: SETTLED
```

The reconciliation engine returns:

```text
FIELD_MISMATCH

field: cash_amount
internal: 50000
external: 55000
```

The discrepancy is reported and preserved.

The system does not automatically assume that either side is correct.

---

## 4. Final Deliverable

The final product is a **locally runnable Java/Spring Boot REST backend backed by PostgreSQL**.

A reviewer can:

1. capture an already-matched trade
2. request settlement
3. inspect balances and settlement history
4. submit competing settlements and observe safe concurrency behaviour
5. retry a request without creating duplicate financial effects
6. simulate failures around the transaction boundary
7. upload a simulated external settlement report
8. inspect reconciliation breaks
9. run a small local load experiment
10. run the automated correctness tests

The completed repository contains:

* Spring Boot backend
* PostgreSQL database
* REST API
* OpenAPI specification
* Swagger UI or equivalent API explorer
* Flyway migrations
* synthetic demo data
* automated tests against PostgreSQL
* reconciliation CSV fixtures
* a repeatable load test
* README with setup and demo instructions

There is **no frontend requirement**.

The backend API is the product.

OpenAPI/Swagger is a convenient way to inspect and use the API.

Tests provide evidence that the system behaves correctly.

---

## 5. Technology Stack

| Technology                               | Purpose                                                      |
| ---------------------------------------- | ------------------------------------------------------------ |
| Java 21                                  | Application and domain logic                                 |
| Spring Boot                              | Backend application framework                                |
| Spring MVC                               | REST API                                                     |
| Spring JDBC `NamedParameterJdbcTemplate` | Explicit SQL and persistence                                 |
| PostgreSQL                               | Trades, accounts, balances, transactions, locks and journals |
| Flyway                                   | Database migrations                                          |
| JUnit + AssertJ                          | Automated tests                                              |
| Testcontainers                           | PostgreSQL integration and concurrency tests                 |
| OpenAPI / Swagger                        | API documentation and manual exploration                     |
| Docker Compose                           | Local runtime                                                |
| GitHub Actions                           | CI                                                           |

### Persistence approach

Use **Spring JDBC**, not an ORM.

There is:

* no Prisma
* no JPA
* no Hibernate

Explicit SQL keeps transaction boundaries, locking and financial writes visible and easy to reason about.

---

# 6. Core Features

## F1. Trade Capture

### Purpose

Record an already-agreed trade between a buyer and seller.

### Input

A trade contains:

* external trade reference
* buyer
* seller
* security
* quantity
* agreed cash amount
* settlement date

### Expected behaviour

A valid trade is stored as `READY`.

Captured economic terms are immutable.

### Done when

* valid trades can be created and retrieved
* invalid trades are rejected
* unknown participants or assets are rejected
* buyer and seller cannot be the same participant
* reusing a trade reference with different terms is rejected
* existing accepted trade terms cannot be silently overwritten

---

## F2. Atomic DvP Settlement

### Purpose

Move cash and securities together.

For a successful trade:

```text
Buyer cash       -500
Seller cash      +500

Buyer EQ1        +10
Seller EQ1       -10
```

### Expected behaviour

Before settlement the system checks:

* trade exists
* trade is `READY`
* trade is due
* buyer has sufficient cash
* seller has sufficient securities

If valid, all financial effects commit in one PostgreSQL transaction.

A successful settlement creates:

* one settlement journal
* four postings
* four account balance changes
* `SETTLED` trade status
* recorded settlement outcome

### Done when

A successful settlement produces all required state changes.

If settlement is rejected or the transaction fails before commit, none of the financial movements remain.

---

## F3. Concurrent Settlement Protection

### Purpose

Prevent multiple transactions from spending the same financial resources.

Example:

```text
Alice cash = AUD 1,000

Trade A needs AUD 800
Trade B needs AUD 800

Both settlement requests arrive concurrently.
```

Incorrect:

```text
Trade A succeeds
Trade B succeeds

Alice spends AUD 1,600.
```

Required:

```text
one succeeds
one is rejected

Alice never goes below zero.
```

### Design

Each settlement uses a PostgreSQL transaction.

The system:

1. locks the trade
2. identifies the four affected accounts
3. locks those accounts in deterministic account-ID order
4. checks balances only after acquiring the locks
5. applies the settlement

PostgreSQL is the source of concurrency correctness. Java in-process locks are not used to protect financial state.

### Done when

Controlled concurrency tests demonstrate:

* cash cannot be overspent
* securities cannot be oversold
* one trade cannot create multiple settlement journals
* unrelated trades can still proceed independently

---

## F4. Idempotent Commands and Safe Retry

### Purpose

Protect against duplicate requests and ambiguous client outcomes.

Example:

```text
client sends settlement request
        |
        v
database commits
        |
        v
HTTP response is lost
        |
        v
client does not know if settlement happened
        |
        v
client retries
```

Without idempotency, the transaction could be applied twice.

### Expected behaviour

Capture and settlement commands include an idempotency key.

The same key and same request return the original recorded outcome.

Changing the request while reusing the same key is rejected.

Business trade identity independently prevents the same trade from being settled twice under a new key.

### Done when

A lost-response scenario followed by retry still produces:

```text
1 settlement
1 journal
4 postings
```

---

## F5. Trade, Account and Journal Inspection

### Purpose

Allow a reviewer to understand exactly what happened.

The API exposes:

* trade terms
* current trade state
* settlement attempts
* account balances
* command outcome
* settlement journal
* journal postings

### Expected workflow

```text
Trade
  |
  v
Settlement outcome
  |
  v
Journal
  |
  v
4 postings
  |
  v
Updated balances
```

### Done when

A reviewer can trace the complete financial effect of a trade through the API without directly inspecting PostgreSQL.

---

## F6. Reconciliation

### Purpose

Compare internal records with a simulated external settlement report.

### Workflow

```text
Save internal state
        |
        v
Upload external CSV
        |
        v
Compare records
        |
        v
Return reconciliation breaks
```

### Required discrepancy types

| Break                | Meaning                                             |
| -------------------- | --------------------------------------------------- |
| `MISSING_EXTERNAL`   | Internal trade has no matching external record      |
| `MISSING_INTERNAL`   | External record has no matching internal trade      |
| `DUPLICATE_EXTERNAL` | External report contains duplicate trade references |
| `FIELD_MISMATCH`     | Same trade exists on both sides but values differ   |

Compare:

* buyer
* seller
* security
* quantity
* cash amount
* settlement date
* settlement status

### Expected behaviour

The system preserves the uploaded report and the internal comparison state used for the reconciliation.

Repeated reconciliation against the same saved inputs produces the same result.

Reconciliation does not modify settlement history.

### Done when

Fixtures demonstrate:

* clean report
* missing trade
* extra trade
* duplicate
* incorrect amount
* incorrect quantity
* incorrect status

and each produces the expected result.

---

## F7. Failure and Recovery Demonstration

### Purpose

Demonstrate what happens when failures occur around a financial transaction.

Two boundaries are required.

### Failure before commit

```text
transaction starts
      |
database writes begin
      |
APPLICATION FAILURE
      |
PostgreSQL rollback
```

Expected:

```text
no partial settlement
```

### Failure after commit but before response

```text
transaction commits
      |
response is lost
      |
client retries
```

Expected:

```text
original result returned
no duplicate settlement
```

### Done when

Both scenarios can be reproduced through automated tests or demo tooling.

A full chaos-engineering system is not required.

---

## F8. Basic Load and Scalability Experiment

### Purpose

Explore how the transaction-processing design behaves as concurrency and contention increase.

This is **not intended to reproduce bank-scale infrastructure**.

The goal is to understand what begins to limit the system as the workload changes.

### Workload A: Independent accounts

Many trades use different buyers and sellers.

```text
Trade 1: A -> B
Trade 2: C -> D
Trade 3: E -> F
Trade 4: G -> H
```

This tests how much useful parallelism the application and database can achieve.

### Workload B: Contended account

Many trades share the same buyer cash account.

```text
            Seller B
               ^
               |
Seller C <- Buyer A -> Seller D
               |
               v
            Seller E
```

This demonstrates how contention on one financial resource limits concurrency.

### Suggested concurrency

Test a small progression such as:

```text
1 client
4 clients
8 clients
```

Increase further only if useful.

### Record

* transactions per second
* p50 request latency
* p95 request latency
* successful settlements
* business rejections
* technical errors

### Done when

The README contains reproducible results for both workloads and explains why their behaviour differs.

No performance target is defined in advance.

No claim is made that the result represents production financial-system capacity.

---

# 7. API

The core REST API is:

| Method | Route                          | Purpose                                 |
| ------ | ------------------------------ | --------------------------------------- |
| `POST` | `/v1/trades`                   | Capture a matched trade                 |
| `GET`  | `/v1/trades/{id}`              | Inspect a trade                         |
| `POST` | `/v1/trades/{id}/settle`       | Attempt settlement                      |
| `GET`  | `/v1/trades/{id}/attempts`     | Inspect settlement attempts             |
| `GET`  | `/v1/accounts`                 | Inspect account balances                |
| `GET`  | `/v1/journals/{id}`            | Inspect settlement journal and postings |
| `GET`  | `/v1/commands/{key}`           | Inspect an idempotent command outcome   |
| `POST` | `/v1/reconciliation/scopes`    | Save internal reconciliation state      |
| `POST` | `/v1/reconciliation/reports`   | Upload simulated external CSV           |
| `POST` | `/v1/reconciliation/runs`      | Run reconciliation                      |
| `GET`  | `/v1/reconciliation/runs/{id}` | Inspect reconciliation result           |
| `GET`  | `/actuator/health`             | Basic application health                |

Keep the API deliberately small.

Do not create endpoints solely for possible future functionality.

---

# 8. Data Model

## Participant

Represents a participant in a simulated trade.

Important fields:

```text
id
name
```

---

## Asset

Represents AUD cash or a fictional security.

Important fields:

```text
id
code
type
```

Examples:

```text
AUD
EQ1
EQ2
EQ3
```

---

## Account

Represents one participant's holdings of one asset.

```text
id
participant
asset
opening_balance
current_balance
```

Each participant has one account for each supported asset.

Accounts are created before settlement begins.

---

## Trade

Represents an already-matched trade.

```text
id
external_trade_id
buyer
seller
security
quantity
cash_amount
settlement_date
status
journal_id
```

States:

```text
READY
SETTLED
```

A `READY` trade has no settlement journal.

A `SETTLED` trade has exactly one settlement journal.

---

## Journal

Represents one completed settlement.

A trade can have at most one journal.

---

## Posting

Represents one movement within a settlement journal.

A successful settlement creates exactly four postings:

```text
buyer cash debit
seller cash credit
buyer security credit
seller security debit
```

---

## SettlementAttempt

Records a committed settlement decision.

Examples:

```text
SETTLED
ALREADY_SETTLED
INSUFFICIENT_CASH
INSUFFICIENT_SECURITIES
NOT_DUE
```

---

## CommandResult

Stores the durable response associated with an idempotency key.

---

## ReconciliationScope

Stores a fixed internal state used for reproducible reconciliation.

---

## ReconciliationReport

Stores an uploaded simulated external report.

---

## ReconciliationRun

Stores the comparison result and discrepancy details.

---

# 9. Architecture

The project uses one modular Spring Boot application and one PostgreSQL database.

```text
                    HTTP
                      |
                      v
             +------------------+
             |  Spring MVC API  |
             +------------------+
                      |
                      v
             +------------------+
             | Application      |
             | Services         |
             +------------------+
                |           |
                v           v
       +--------------+  +----------------+
       | Settlement   |  | Reconciliation |
       +--------------+  +----------------+
                \           /
                 \         /
                  v       v
             +------------------+
             |   Spring JDBC    |
             +------------------+
                      |
                      v
             +------------------+
             |   PostgreSQL     |
             +------------------+
```

### API layer

Responsible for:

* HTTP request handling
* input validation
* response formatting

### Service layer

Responsible for:

* settlement orchestration
* transaction boundaries
* idempotency
* concurrency rules

### Persistence layer

Uses Spring JDBC and explicit parameterized SQL.

Repository operations participate in the service transaction.

### Reconciliation component

Responsible for:

* capturing internal comparison state
* parsing external reports
* comparing records
* returning discrepancies

---

# 10. Settlement Flow

For a new settlement command:

```text
1. Receive settlement request
        |
2. Check idempotency key
        |
3. Lock trade
        |
4. Check state and settlement date
        |
5. Lock four accounts in deterministic order
        |
6. Check cash and securities
        |
7. Create journal + postings
        |
8. Update balances
        |
9. Mark trade SETTLED
        |
10. Store settlement result
        |
11. COMMIT
        |
12. Return response
```

The financial movements, journal, trade state and successful command result belong to the same PostgreSQL transaction.

A technical failure before commit rolls back the transaction.

---

# 11. Core Invariants

The following properties must always hold.

### I1. Atomic DvP

Cash and securities settle together or neither settles.

### I2. No duplicate settlement

One trade can produce at most one settlement journal.

### I3. No negative holdings

No account may end with a negative balance.

### I4. Cash conservation

Settlement cannot create or destroy AUD.

### I5. Security conservation

Settlement cannot create or destroy securities.

### I6. Correct journal shape

A successful settlement creates exactly four postings to the correct participant and asset accounts.

### I7. Balance reconstruction

For every account:

```text
current balance
=
opening balance
+
all committed postings
```

### I8. Idempotency

Retrying the same command cannot create another financial effect.

### I9. Immutable settlement history

Committed journals and postings cannot be silently modified through normal application operations.

### I10. Reconciliation isolation

Reconciliation does not mutate settlement history or account balances.

---

# 12. Concurrency and Idempotency Design

Each settlement runs inside one PostgreSQL transaction.

The system:

1. locks the trade
2. resolves the four required account rows
3. locks the accounts in ascending account-ID order
4. checks balances after the locks are held
5. writes the settlement

Consistent lock ordering reduces application-level deadlock risk.

The idempotency model contains two identities:

### Command identity

An idempotency key protects against repeated network requests.

### Trade identity

The upstream trade reference protects against settling the same identified trade multiple times under different command keys.

These solve different problems and both are required.

---

# 13. Reconciliation Design

A reconciliation run compares:

```text
SAVED INTERNAL STATE
        |
        | exact business reference
        v
SIMULATED EXTERNAL REPORT
```

The internal state includes:

* trades
* settlement status
* relevant journal information
* balances required for internal consistency checking

The external CSV includes:

* trade reference
* buyer
* seller
* security
* quantity
* cash amount
* settlement date
* status

Records are matched by the external trade reference.

The reconciliation result contains:

```text
MATCHED
MISSING_EXTERNAL
MISSING_INTERNAL
DUPLICATE_EXTERNAL
FIELD_MISMATCH
```

Duplicate report records must not be silently overwritten.

Reconciliation preserves the evidence used for comparison and never automatically repairs financial state.

---

# 14. Basic Security and Reliability Requirements

The project is not intended to become a security project, but financial history should have a clear trust boundary.

Required practices:

* parameterized SQL
* strict request validation
* environment-based database credentials
* no committed secrets
* ordinary application APIs cannot edit historical postings
* no endpoint for arbitrary balance modification
* bounded CSV uploads
* errors must not expose SQL or credentials
* application is local by default

Reliability is demonstrated primarily through:

* PostgreSQL transaction semantics
* idempotent retries
* failure testing
* immutable journals
* reconciliation

---

# 15. Required Tests and Demonstrations

## Normal settlement

Alice buys 10 EQ1 from Bob for AUD 500.

Expected:

```text
Alice  -500 AUD  +10 EQ1
Bob    +500 AUD  -10 EQ1
```

---

## Insufficient cash

Buyer does not have enough cash.

Expected:

```text
no financial movement
trade remains READY
```

---

## Insufficient securities

Seller does not have enough securities.

Expected:

```text
no financial movement
trade remains READY
```

---

## Cash race

Buyer has AUD 1,000.

Two concurrent trades each require AUD 800.

Expected:

```text
exactly one settles
one is rejected
buyer ends with AUD 200
```

---

## Securities race

Two buyers compete for a position that only one trade can consume.

Expected:

```text
exactly one settles
security balance never becomes negative
```

---

## Same-trade race

Two settlement requests target the same trade.

Expected:

```text
one settlement journal only
```

---

## Idempotent retry

Repeat the same settlement command.

Expected:

```text
same recorded result
no additional financial movement
```

---

## Failure before commit

Inject a failure during settlement.

Expected:

```text
no partial financial state
```

---

## Response lost after commit

Commit the settlement but simulate the client not receiving the response.

Retry using the same key.

Expected:

```text
original result returned
one settlement only
```

---

## Reconciliation

Test:

* clean report
* missing internal record
* missing external record
* duplicate record
* incorrect amount
* incorrect quantity
* incorrect status

Expected break types must be returned.

---

## Load experiment

Run independent and contended workloads at increasing concurrency.

Record:

* TPS
* p50 latency
* p95 latency
* outcome counts

Explain the observed difference.

---

# 16. Optional Stretch Feature

Only begin stretch work after all core acceptance criteria pass.

## Transactional Outbox

Add reliable delivery of a `SettlementCompleted` notification to a small independently committing local receiver.

The extension should demonstrate:

```text
settlement commits
      |
      v
notification queued
      |
      v
receiver processes
      |
      v
acknowledgement lost
      |
      v
sender retries
      |
      v
receiver deduplicates
```

This allows discussion of:

* transactional outbox
* at-least-once delivery
* idempotent consumers
* network failure
* independent transaction boundaries

Kafka is not required.

---

# 17. Non-Goals

The project does not implement:

* trade matching
* order books
* market-data feeds
* trading strategies
* pricing or valuation
* settlement netting
* settlement batches
* persistent resource reservations
* partial settlement
* short selling
* credit lines
* multiple currencies
* FX conversion
* fees or taxes
* corporate actions
* real bank, exchange or custodian connectivity
* legal settlement finality
* regulatory reporting
* customer authentication
* public production deployment
* Kafka
* Kubernetes
* microservices
* distributed database transactions
* event sourcing
* custom database or WAL
* large frontend

These features are deliberately excluded to keep the project focused and realistically finishable in approximately one focused week.

---

# 18. Definition of Done

The project is complete when a reviewer can:

* [ ] clone the repository and start the system using documented commands
* [ ] start PostgreSQL and run Flyway migrations
* [ ] seed synthetic participants and account balances
* [ ] inspect the API through OpenAPI/Swagger
* [ ] capture a matched trade
* [ ] settle the trade successfully
* [ ] inspect the buyer and seller balances
* [ ] inspect exactly four settlement postings
* [ ] observe insufficient-resource rejection without financial changes
* [ ] reproduce a concurrent overspending race safely
* [ ] reproduce a same-trade settlement race safely
* [ ] retry a command without creating duplicate financial effects
* [ ] reproduce a failure before commit with complete rollback
* [ ] reproduce a lost-response scenario after commit and recover safely
* [ ] upload a simulated external report
* [ ] inspect missing, duplicate and mismatched reconciliation results
* [ ] run a small repeatable scalability experiment
* [ ] run the automated test suite successfully against PostgreSQL

The README must explain:

* what DvP is
* why the project was built
* what the system simulates
* the core financial invariants
* the concurrency and idempotency design
* the failure scenarios tested
* how reconciliation works
* what the local scalability experiment showed
* what the project deliberately does not attempt to reproduce

This specification defines **what the project must become**.

The implementation order, repository changes and day-by-day development plan belong in a separate `implementation-plan.md`.
