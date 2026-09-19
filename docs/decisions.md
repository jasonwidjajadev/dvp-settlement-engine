# Architecture Decisions Reasons (ADR)

> WHY? The purpose of this file is to stop future implementation work from accidentally reopening decisions that have already been made, and preserve meaningful architecture decisions

These decisions are accepted and fixed for implementation. The frozen project specification remains authoritative for behaviour and scope. This file preserves the reasoning behind the major choices; it does not introduce requirements or reopen the architecture.

## ADR-001: Java 21 + Spring Boot

**Decision:**
Use Java 21 and Spring Boot, with Spring MVC for the REST API.

**Why:**

* Aligns with the settlement and post-trade backend environment targeted by the project.
* Provides a coherent backend environment for transaction processing, persistence, validation and testing.
* Supports explicit domain modelling and established reliability practices without requiring additional infrastructure.
* Fits the bounded project scope without relying on presumed language familiarity.

**Rejected alternatives:**

* Python: presumed implementation speed does not justify replacing the approved Java stack.
* Kotlin: no concrete project advantage warrants another language choice.

**Implication:**
Implement the backend in Java. Keep the request and service flow synchronous; the API is the product and requires no frontend.

## ADR-002: Spring JDBC instead of JPA/Hibernate

**Decision:**
Use Spring JDBC with `NamedParameterJdbcTemplate` and explicit parameterized SQL.

**Why:**

* Makes lock acquisition, balance updates and financial writes visible during review.
* Avoids ORM flush timing, entity lifecycle and persistence-context behaviour in the settlement path.

**Rejected alternatives:**

* JPA/Hibernate: adds persistence behaviour that this small, transaction-focused model does not need.
* Raw JDBC throughout: adds repetitive connection and resource management.

**Implication:**
Repositories execute explicit SQL inside service-owned transactions. They must participate in the same transaction when processing one settlement. Use Flyway for schema changes.

## ADR-003: PostgreSQL as the transactional store

**Decision:**
Store trades, accounts, journals, command results and reconciliation evidence in one PostgreSQL database.

**Why:**

* Transactions, row locks and relational constraints directly support the required correctness guarantees.
* One durable store makes financial effects and their supporting records commit consistently.

**Rejected alternatives:**

* In-memory storage: cannot provide durable outcomes across application restarts.
* Separate databases for financial records: introduce unnecessary coordination and consistency problems.

**Implication:**
Use database constraints alongside application validation. Run transaction, rollback and concurrency tests against PostgreSQL through Testcontainers; mocks alone cannot establish these guarantees.

## ADR-004: One modular application

**Decision:**
Build one modular Spring Boot application connected to one PostgreSQL database.

**Why:**

* Settlement and reconciliation need clear responsibilities, but do not require independent deployment.
* Keeps transaction ownership, local operation and failure diagnosis understandable for a solo project.

**Rejected alternatives:**

* Microservices: add network boundaries, deployment overhead and distributed failure handling without a core requirement.
* Unstructured application code: obscures domain responsibilities and transaction ownership.

**Implication:**
Separate the MVC API, application services, JDBC persistence and reconciliation component through code modules or packages. Demo tooling exercises the same application behaviour. Kubernetes is outside scope.

## ADR-005: Gross trade-by-trade settlement

**Decision:**
Settle each already-matched trade individually and in full.

**Why:**

* Gives each financial movement a direct relationship to one trade and journal.
* Exposes meaningful resource contention without adding batch allocation rules.

**Rejected alternatives:**

* Netting: requires obligation aggregation and allocation semantics beyond the core project.
* Batch or partial settlement: introduces group outcomes or residual obligations requiring additional lifecycle rules.

**Implication:**
Each settlement command targets one trade. Successful settlement creates one journal with exactly four postings. Insufficient cash or securities rejects the entire settlement without financial movement.

## ADR-006: One transaction for both simulated asset legs

**Decision:**
Commit cash and securities movements together in one PostgreSQL transaction.

**Why:**

* Both simulated assets are controlled by the same database, so local atomicity directly implements DvP.
* A pre-commit failure can roll back all financial effects and associated settlement records.

**Rejected alternatives:**

* Separate leg commits or compensation: allow an intermediate state where only one asset has moved.
* Distributed transactions: unnecessary within the simulation boundary.

**Implication:**
Postings, balances, journal, trade state and successful command outcome commit together. Return success after commit. This guarantees internal atomicity, not real bank/custodian coordination or legal settlement finality.

## ADR-007: Deterministic PostgreSQL row locking

**Decision:**
Lock the trade, then all four affected account rows in ascending account-ID order. Validate balances after acquiring the locks.

**Why:**

* Serializes settlements competing for the same holdings and prevents overspending.
* Consistent lock ordering reduces deadlock risk while unrelated accounts can proceed independently.

**Rejected alternatives:**

* Java in-process locks: cannot coordinate independent application processes.
* Optimistic retries: complicate contention handling without improving the required demonstration.
* Global settlement lock: unnecessarily serializes independent trades.

**Implication:**
Every settlement path must follow the same locking protocol. Hold locks until transaction completion. Do not treat deterministic ordering as a guarantee that database deadlocks are impossible.

## ADR-008: Immutable journal with current balance projection

**Decision:**
Preserve committed journals and postings, and maintain current account balances alongside immutable opening balances.

**Why:**

* Journals explain financial changes; current balances support efficient settlement checks.
* Reconstruction provides an independent consistency check against stored balances.

**Rejected alternatives:**

* Balances alone: lose the evidence needed to explain and reconstruct movements.
* Full event sourcing: adds event evolution and command replay concerns beyond this ledger.

**Implication:**
Update the projection and postings atomically. Require `current balance = opening balance + committed postings`. Ordinary application operations cannot edit settlement history or arbitrarily change balances. Reconstruction checks state without replaying settlement commands.

## ADR-009: Command idempotency plus business trade identity

**Decision:**
Use idempotency keys for capture and settlement commands, plus a unique upstream trade identity.

**Why:**

* Command identity handles repeated requests and responses lost after commit.
* Business identity prevents duplicate financial effects when another command key targets the same identified trade.

**Rejected alternatives:**

* Idempotency keys alone: do not identify duplicates submitted under different keys.
* Trade identity alone: cannot reproduce the original command outcome.

**Implication:**
Persist the request identity and durable result. Same key and request returns the original outcome; changed request with the same key is rejected. Enforce at most one journal per trade. Retry ambiguous outcomes using the original key.

## ADR-010: Reconciliation against saved inputs

**Decision:**
Compare a saved, consistent internal state with a preserved simulated external CSV report.

**Why:**

* Fixed inputs make results reproducible while live settlement continues.
* Preserved evidence explains discrepancies without assuming either side is correct.

**Rejected alternatives:**

* Live-state comparison: results can change as settlement progresses.
* Automatic repair: introduces financial changes without an approved correction model.

**Implication:**
Save relevant trades, statuses, journals and balances. Match by exact business trade reference. Detect `MISSING_EXTERNAL`, `MISSING_INTERNAL`, `DUPLICATE_EXTERNAL` and `FIELD_MISMATCH`. Preserve duplicate rows rather than overwriting them. Reconciliation never mutates financial state or settlement history.

## ADR-011: No persistent resource reservations

**Decision:**
Check and consume available holdings within settlement; do not reserve resources between commands.

**Why:**

* Database locks protect the short settlement transaction.
* Reservations would require expiry, release and recovery rules that the synchronous flow does not need.

**Rejected alternatives:**

* Persistent reservations: introduce additional balances and lifecycle transitions.
* Partial resource allocation: conflicts with full trade settlement.

**Implication:**
Trade states remain `READY` and `SETTLED`. Resource or due-date rejection leaves the trade `READY`, with the committed decision recorded separately as a settlement attempt. A new attempt evaluates resources again; replaying an existing command returns its saved outcome.

## ADR-012: No Kafka in the core project

**Decision:**
Keep core settlement synchronous and broker-free.

**Why:**

* The core product has no independent event consumer requiring a streaming platform.
* A broker would add delivery, ordering and operational concerns without strengthening local DvP atomicity.

**Rejected alternatives:**

* Kafka as the settlement command bus: adds asynchronous processing and recovery semantics.
* Kafka as the audit log: duplicates a role already served by committed PostgreSQL journals.

**Implication:**
REST commands enter application services directly. PostgreSQL records outcomes and financial history. Core startup, tests and demonstrations must not depend on a messaging broker.

## ADR-013: Transactional outbox only as an optional extension

**Decision:**
Allow an outbox only after core acceptance criteria pass, paired with an independently committing local receiver.

**Why:**

* An actual receiver gives reliable notification delivery a concrete purpose.
* It demonstrates retries and duplicate handling across separate transaction boundaries.

**Rejected alternatives:**

* Outbox without a receiver: adds machinery without demonstrating delivery semantics.
* Direct notification alongside settlement: leaves a failure gap between database commit and external delivery.

**Implication:**
If implemented, write `SettlementCompleted` to the outbox in the settlement transaction. Deliver after commit with retries and durable receiver deduplication. Demonstrate acknowledgement loss and at-least-once delivery. Kafka remains unnecessary; outbox work is not required for core completion.

## ADR-014: Local scalability and contention experiment

**Decision:**
Include a small reproducible load experiment comparing transactions on independent accounts with transactions competing for a shared account.

**Why:**

* Demonstrates how concurrency and database contention affect a transaction-processing system.
* Adds measured throughput and latency evidence rather than only proving correctness for individual requests.
* Shows why logically contended financial resources can limit scalability even when more concurrent requests are available.
* Allows scalability to be explored without pretending that a local machine reproduces production financial-system scale.

**Rejected alternatives:**

* No performance evaluation: leaves an important systems-engineering question unexplored.
* Large-scale benchmarking infrastructure: adds unnecessary scope for a one-week project.
* Predetermined throughput or latency targets: local results are hardware-dependent and cannot credibly represent production financial infrastructure.

**Implication:**
Run a small workload at increasing client concurrency for:

1. trades using mostly independent accounts
2. trades competing for a shared account

Record:

* transactions per second
* p50 latency
* p95 latency
* successful settlements
* business rejections
* technical errors

Document the environment and explain the observed bottleneck. Do not claim bank-scale performance or define an arbitrary performance target in advance.

## ADR-015: Minimal security boundary

**Decision:**
Apply security controls directly relevant to protecting the local transaction system and its financial history, without adding a full authentication or identity platform.

**Why:**

* Financial history should not be arbitrarily mutable through normal application operations.
* Parameterized SQL and strict validation reduce avoidable input and database risks.
* Restricted database permissions create a meaningful boundary between normal application behaviour and privileged administration.
* Authentication and broader identity management would add significant scope without strengthening the core settlement, concurrency or reconciliation problems.

**Rejected alternatives:**

* Full authentication and authorization system: outside the project's post-trade correctness focus.
* Fully privileged application database user: unnecessarily weakens the audit-history boundary.
* Security-heavy infrastructure: would distract from the one-week transaction-processing scope.

**Implication:**
Use:

* parameterized SQL
* strict request validation
* environment-based credentials
* no committed secrets
* restricted application database permissions
* no arbitrary balance-editing endpoint
* no historical posting mutation through normal application operations
* bounded external report uploads
* errors that do not expose SQL, credentials or stack traces
* local-only operation by default

Security remains supporting engineering hygiene rather than a separate product feature.
