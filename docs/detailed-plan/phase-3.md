# Phase 3: Atomic Settlement and Financial Inspection

## Goal

Implement Atomic DvP Settlement and Financial Inspection on top of the completed Phase 2 Trade Capture system.

Phase 3 establishes:

```text
Phase 2 Trade Capture
    |
    v
Approved settlement contract
    |
    v
V3 journal + posting + attempt schema
    |
    v
Settlement domain types
    |
    v
Spring JDBC settlement persistence
    |
    v
Service-owned settlement transaction
    |
    v
Trade lock + deterministic account locks
    |
    v
Balance validation after locks
    |
    v
One journal + four postings + four balance updates
    |
    v
READY -> SETTLED + durable command outcome
    |
    v
Journal / attempt / command inspection API
    |
    v
PostgreSQL integration tests
```

At the end of this phase:

* a due `READY` trade can be settled through the API
* one successful settlement creates exactly one settlement journal
* one successful settlement creates exactly four postings
* four account balances change in the same transaction
* the trade becomes `SETTLED` and points at its journal
* the settlement command outcome is durable
* insufficient cash causes no financial movement
* insufficient securities causes no financial movement
* a trade that is not yet due causes no financial movement
* a second settlement of the same trade cannot create a second journal
* a technical failure before commit leaves no partial financial state
* balances can be reconstructed from opening balance plus committed postings
* journals, postings, settlement attempts and command outcomes can be inspected through the API
* the Alice/Bob settlement works end to end
* automated tests run against PostgreSQL
* `./mvnw verify` passes

Phase 3 does not implement:

* controlled concurrent settlement races
* cash-contention or securities-contention demonstrations
* same-trade concurrent settlement demonstrations
* competing idempotency-key races
* response-loss-after-commit demonstrations
* recovery across application restart
* broader concurrency or throughput stress testing
* reconciliation
* load experiment
* OpenAPI/Swagger documentation polish
* Docker packaging
* CI
* restricted database roles
* Kafka
* transactional outbox
* frontend

Phase 3 must still *implement* the locking protocol required by ADR-007, because Phase 4 cannot prove concurrency safety for code that does not lock. Phase 3 only avoids the concurrency *demonstrations*.

Do not begin Phase 4 automatically.

Task summary:

* 3.1: Confirm the Phase 2 baseline and get the remaining settlement contract decisions approved before they become schema constraints or API behaviour.
* 3.2: Add the Flyway V3 schema for settlement journals, postings, settlement attempts and the trade-to-journal relationship.
* 3.3: Create the Java settlement domain types.
* 3.4: Add the explicit SQL operations settlement needs, including row locking and balance updates.
* 3.5: Implement the settlement transaction: claim the command, lock the trade, resolve and lock the four accounts, then validate resources.
* 3.6: Write the journal, the four postings, the four balance updates, the `SETTLED` transition and the durable outcome, and prove rollback.
* 3.7: Expose settlement and financial inspection through REST.
* 3.8: Verify the financial invariants, including balance reconstruction and immutable history.
* 3.9: Verify the whole Phase 3 flow end to end, then stop before the concurrency and recovery work.

---

# 3.1 Confirm Phase 2 and approve the settlement contract

Purpose:

Start from the completed Phase 2 repository and resolve the remaining settlement decisions before they are frozen into database constraints, HTTP behaviour and durable command outcomes.

No settlement code should be written during this task.

---

## 3.1.1 Inspect the completed Phase 2 repository

What is the baseline?

* Phase 1 and Phase 2 already established:

  * Java 21, Maven Wrapper, Spring Boot, PostgreSQL 18.6
  * Flyway `V1__participants_assets_accounts.sql` and `V2__trades_and_command_results.sql`
  * `participant`, `asset`, `account`, `trade`, `command_result`
  * deterministic Alice/Bob demo data in `scripts/seed-demo.sql`
  * `AccountRepository`, `ParticipantRepository`, `AssetRepository`, `TradeRepository`, `CommandResultRepository`
  * `CaptureTradeService` owning the capture transaction through `TransactionTemplate`
  * `POST /v1/trades`, `GET /v1/trades/{id}`, `GET /v1/accounts`
  * `ApiExceptionHandler` with the `{code,message}` error body
  * shared Testcontainers PostgreSQL 18.6 support with per-test table cleanup
  * basic springdoc OpenAPI / Swagger infrastructure

* [x] Confirm the existing OpenAPI baseline before adding endpoints.

  * `springdoc-openapi-starter-webmvc-ui` is a dependency and is configured by convention only.
  * `/v3/api-docs` and `/swagger-ui/index.html` work.
  * The Phase 2 endpoints are discovered automatically from the existing controllers and DTOs.
  * There are no `@Operation`, `@ApiResponse` or `@Schema` annotations, no custom OpenAPI YAML, and no grouping or documentation polish.
  * Phase 3 controllers should be discovered the same way, with no springdoc-specific application code.

* [x] Inspect the repository tree.

* [x] Run `git status`.

  * Preserve existing work.
  * Confirm no settlement implementation already exists.
  * Confirm no V3 migration already exists.

* [x] Read:

  * `docs/project-spec.md`
  * `docs/decisions.md`
  * `docs/implementation-plan.md`
  * `docs/detailed-plan/phase-1.md`
  * `docs/detailed-plan/phase-2.md`
  * `docs/engineering-log.md`

* [x] Inspect the Phase 2 production code that Phase 3 must extend rather than replace.

  * `CaptureTradeService` — the transaction pattern settlement will reuse
  * `CommandResultRepository` — `claim` currently hardcodes `CAPTURE_TRADE`
  * `CaptureRequestIdentity` — the length-prefixed durable identity encoding
  * `TradeRepository` — no locking methods yet
  * `AccountRepository` — read-only, `findAll` / `findById`
  * `TradeStatus` — `READY` only
  * `ApiExceptionHandler` — the existing status/code mapping

* [x] Inspect the existing test support.

  * `AbstractPostgresIntegrationTest` truncates `command_result, trade, account, participant, asset`
  * `PostgresStartupIntegrationTest` asserts the exact table list and the applied migration list
  * `DemoSeed` exposes the deterministic Alice/Bob/AUD/EQ1 identifiers

Why:

* Phase 3 is the first phase that mutates financial state, so it must build on the exact repository Phase 2 produced.
* Several Phase 2 files are deliberately incomplete for settlement (`TradeStatus`, `CommandResultRepository.claim`, the truncate list, the startup table assertion). Knowing this before implementation prevents accidental duplication of parallel settlement types.
* The plan must not assume files or packages that do not exist.

Verification:

* [x] Run:

```bash
./mvnw verify
```

* [x] Confirm the existing suite passes before Phase 3 code is written.
* [x] Record the actual test count and result.

Engineering log:

* Record the Phase 3 starting repository state.
* Record the baseline verification command and result.
* Record any unexpected difference from the approved documents before proceeding.

Ready for 3.1.2 when:

* the Phase 2 baseline is understood
* existing tests pass
* the Phase 2 files that Phase 3 must extend are identified

---

## 3.1.2 Approve the settlement due-date rule (C2)

What is being decided?

* `docs/implementation-plan.md` lists C2 as unresolved and requires it to be resolved before Phase 3.
* The project specification requires settlement to check that the trade "is due", and lists `NOT_DUE` as a recorded settlement decision.
* Trade Capture deliberately accepts any calendar settlement date, so Phase 3 owns the entire definition of "due".

Proposed rule:

```text
business date = current date in Australia/Sydney

due            settlementDate <= business date
not due        settlementDate >  business date
```

* [x] Approve the business timezone.

  * `Australia/Sydney`
  * The simulated market is Australian and the only asset currency is AUD.
  * Using a fixed business timezone keeps the rule reproducible regardless of the machine's default zone.

* [x] Approve date granularity only.

  * No intraday settlement cutoff time.
  * No settlement windows or batch cycles (batching is a non-goal).

* [x] Approve overdue behaviour.

  * A trade with `settlementDate < business date` is still settleable.
  * No ageing, penalty, cancellation or expiry state is introduced.
  * `READY` and `SETTLED` remain the only trade states (ADR-011).

* [x] Approve the clock source.

  * The application exposes one `java.time.Clock` bean fixed to the business timezone.
  * A small `BusinessCalendar` component derives `businessDate()` from that clock.
  * Tests can substitute a fixed clock, or derive expected dates from the same component.
  * Settlement code must never call `LocalDate.now()` directly.

* [x] Approve recording the evaluated business date on each settlement attempt.

  * A recorded `NOT_DUE` decision is only explainable if the date it was judged against is stored.

Why:

* Without an approved rule, `NOT_DUE` becomes an accidental side effect of the server's local timezone.
* The decision changes the schema (`settlement_attempt.business_date`), the service, the HTTP contract and the tests, so it must be approved first.
* Keeping the rule to whole dates matches the deliberately simplified domain and avoids inventing market-hours behaviour that the specification excludes.

Verification:

* [x] Human approval resolves:

  * business timezone
  * due comparison
  * overdue behaviour
  * clock injection
  * business-date recording

* [x] Any requested change is reflected in later Phase 3 steps before implementation.

Engineering log:

* Record the approved C2 decision.
* Record any change from the proposal above.

Ready for 3.1.3 when:

* the exact definition of "due" is approved
* the clock source is approved

---

## 3.1.3 Approve the settlement command contract

What is being decided?

* C3 already fixed the idempotency-key transport and one global command-key namespace.
* C4 already fixed the durable command-result policy for `CAPTURE_TRADE`.
* Phase 3 introduces a second operation, so the same policy must be extended to `SETTLE_TRADE` rather than reinvented.

* [x] Approve the settlement request shape.

```text
POST /v1/trades/{id}/settle
Idempotency-Key: settle-T-001
no request body
```

* The trade is addressed by its internal UUID, the identifier Phase 2 already returns in `Location`.
* A non-empty request body is rejected with `400`, so no unread field can appear to influence the command.
* `Content-Type` is not required because there is no body.

* [x] Approve the settlement request identity.

```text
operation = SETTLE_TRADE
trade id
```

* Nothing else is client-supplied, so nothing else can belong to the identity.
* The same key reused against a different trade is a changed request and is rejected.
* The identity uses the same length-prefixed encoding as `CaptureRequestIdentity`, so both operations share one canonical durable-identity format.
* A capture key reused for settlement is a changed request, because the operation is part of the identity. One global namespace therefore stays unambiguous.

* [x] Approve the Phase 3 extension of C4.

| Situation | Result | Durable command outcome | Settlement attempt |
| --- | --- | --- | --- |
| New valid settlement | `201 Created` + `Location: /v1/journals/{journalId}` | yes | `SETTLED` |
| Same key + same request | replay original result | already stored | none |
| Same key + different trade | `409 Conflict` `IDEMPOTENCY_KEY_CONFLICT` | original result unchanged | none |
| New key + already settled trade | `409 Conflict` `ALREADY_SETTLED` | yes | `ALREADY_SETTLED` |
| Trade not due | `422 Unprocessable Content` `NOT_DUE` | yes | `NOT_DUE` |
| Buyer cash insufficient | `422` `INSUFFICIENT_CASH` | yes | `INSUFFICIENT_CASH` |
| Seller securities insufficient | `422` `INSUFFICIENT_SECURITIES` | yes | `INSUFFICIENT_SECURITIES` |
| A required account row does not exist | `500` `INTERNAL_ERROR` | no — the transaction rolls back | none |
| Unknown trade UUID | `404 Not Found` `UNKNOWN_TRADE` | no — the transaction rolls back | none |
| Malformed trade UUID, missing/invalid key, or non-empty body | `400 Bad Request` | no | none |
| Unexpected technical failure before commit | `500 Internal Server Error` | no committed settlement result | none |
| Unknown journal / command / trade on a GET | `404 Not Found` | read only | none |

Approved change from the original proposal: a missing required settlement account is an internal data-integrity failure, not a client business rejection. It is not `MISSING_ACCOUNT`, not a settlement attempt, and not a durable `422`. The transaction fails, the command claim and every settlement write roll back, and HTTP exposes only the existing safe `500 INTERNAL_ERROR`.

* [x] Approve `201 Created` for a successful settlement.

  * A successful settlement creates a new journal resource, so `Location` can address it.
  * This mirrors the Phase 2 capture contract and keeps the replayed body byte-identical.
  * Alternative considered: `200 OK` with the journal id only in the body. Rejected because the command genuinely creates a resource and `Location` makes the inspection chain self-describing.

* [x] Approve `409 ALREADY_SETTLED` for a second settlement under a new key.

  * Business trade identity, not the command key, is what prevents the second settlement (ADR-009).
  * A conflict states plainly that a duplicate command was submitted, while still guaranteeing one journal.
  * Alternative considered: `200 OK` returning the existing journal. Rejected because it hides a duplicate settlement command behind a success response.

* [x] Approve that an unknown trade leaves no durable command outcome.

  * The claim happens inside the settlement transaction, so an unknown trade must roll the claim back.
  * The consequence is explicit: a `404` leaves the key unused and reusable.

* [x] Approve the recorded settlement attempt policy.

  * Every committed settlement decision records exactly one settlement attempt.
  * A replay records nothing, because no new decision was taken.
  * A rolled-back transaction records nothing.
  * A missing required account is not a committed settlement decision. It rolls back like any other unexpected failure.

* [x] Keep the existing `{code,message}` error body.

Why:

* Settlement is the first operation whose retry behaviour protects real financial effects, so its durable policy cannot be improvised during implementation.
* Recording a committed decision separately from the trade state is required by ADR-011: a rejection leaves the trade `READY`, and the decision must still be visible.
* The statuses feed the schema (`settlement_attempt.outcome`), the service and the HTTP tests simultaneously.

Verification:

* [x] Human approval resolves the settlement request shape, request identity, HTTP outcomes, durability and attempt policy.
* [x] No later Phase 3 step invents a conflicting retry policy.

Engineering log:

* Record the approved settlement command contract.
* Record the approved outcome table.
* Record any change from the proposals above.

Ready for 3.1.4 when:

* the settlement command contract is approved

---

## 3.1.4 Approve the settlement record model

What is being decided?

* The shape of the permanent financial history, which invariants the database enforces, and the one additive change to a Phase 2 response.

* [x] Approve the posting sign convention.

```text
DEBIT   decreases the holder's balance
CREDIT  increases the holder's balance
```

For a successful settlement:

```text
buyer  cash      DEBIT   cashAmount
seller cash      CREDIT  cashAmount
buyer  security  CREDIT  quantity
seller security  DEBIT   quantity
```

* This is the specification's own four-posting description.
* `amount` is always positive. The direction carries the sign.
* A generated stored column holds the signed effect, so the sign rule exists in exactly one place and balance reconstruction is a plain sum.
* `DEBIT` and `CREDIT` are project-local balance-movement directions. They are not general-ledger / GAAP debit-credit semantics. A GAAP asset-account debit would increase the balance; this project uses `DEBIT` to decrease it so the specification's posting labels produce the correct Alice/Bob money movement.

* [x] Approve that a posting does not store its own asset.

  * A posting references an account, and the account already references exactly one asset.
  * Storing the asset twice would create a field that can disagree with itself.
  * Inspection joins the account to report the asset code.

* [x] Approve the trade-to-journal relationship.

```text
settlement_journal.trade_id  UNIQUE NOT NULL   -> at most one journal per trade
trade.journal_id             UNIQUE NULL       -> the trade's settlement, when settled
CHECK (status = 'SETTLED') = (journal_id IS NOT NULL)
```

* The unique `trade_id` is what actually enforces I2 "no duplicate settlement".
* `trade.journal_id` is in the specification's data model and makes the trade row self-describing.
* The equality check makes `READY` without a journal and `SETTLED` with exactly one journal a database guarantee rather than a convention.

* [x] Approve the cash asset resolution rule.

  * The settlement cash asset is the asset with code `AUD`.
  * Multiple currencies and FX are non-goals, so a single supported settlement currency is sufficient.
  * The rule is explicit in one place, not spread through the settlement path.

* [x] Approve the settlement attempt vocabulary.

```text
SETTLED
ALREADY_SETTLED
NOT_DUE
INSUFFICIENT_CASH
INSUFFICIENT_SECURITIES
```

Approved change from the original proposal: `MISSING_ACCOUNT` is not a settlement outcome.

* [x] Approve the database-enforced settlement guarantees.

  * exactly four postings per journal, checked at commit
  * each account appears at most once per journal
  * per-asset net movement within a journal is zero
  * posting amounts equal the trade's `cash_amount` and `quantity`
  * the four accounts belong to the trade's buyer and seller
  * `trade.journal_id` must reference the `settlement_journal` whose `trade_id` is that same trade. Trade A cannot point at Trade B's journal.
  * the two cash postings must use the buyer and seller AUD accounts
  * the two security postings must use the buyer and seller accounts for exactly `trade.security_id`, not merely any asset whose type is `SECURITY`
  * committed journals and postings reject `UPDATE` and `DELETE`
  * a `SETTLED` trade cannot be modified further, and captured terms cannot change

* [x] Approve the one additive Phase 2 response change.

  * `GET /v1/trades/{id}` gains `journalId`, `null` while `READY`.
  * This completes the inspection chain required by F5.
  * Consequence to accept explicitly: capture responses stored in `command_result` before this change replay byte-identically without the new field, because a durable result is never rewritten.

Why:

* The immutable journal is the evidence that explains every balance change (ADR-008), so its shape must be decided before any settlement row is written.
* Enforcing journal shape and conservation in PostgreSQL follows the pattern already used in this repository, where the database is the second line of defence behind Java validation.
* Deciding these together prevents a later migration from having to restate the financial history.

Verification:

* [x] Human approval resolves sign convention, posting shape, trade-to-journal relationship, cash asset rule, attempt vocabulary, database guarantees and the trade response change.
* [x] Any requested change is reflected in 3.2 and 3.7 before implementation.

Engineering log:

* Record the approved settlement record model.
* Record the approved database-enforced guarantees.
* Record any change from the proposals above.

Ready for 3.2 when:

* the Phase 2 baseline passes
* C2 is resolved
* the settlement command contract is approved
* the settlement record model is approved

Section 3.1 is complete. Do not begin Section 3.2 until implementation of the approved contract is requested.

---

# 3.2 Flyway V3 settlement schema

Purpose:

Add the durable PostgreSQL structures that hold settlement history, and extend the two Phase 2 tables that settlement changes.

Do not modify V1 or V2.

---

## 3.2.1 Create Flyway V3

* [x] Create:

```text
src/main/resources/db/migration/
└── V3__settlement_journal_postings_attempts.sql
```

* [x] Keep V1 and V2 unchanged.

* [x] Write V3 so it works on both a clean database and a database already at V2.

Why:

* Flyway migration history is append-only once applied. A local Phase 2 database and the demo database must both upgrade in place.
* Settlement structures belong to their own version so the Phase 2 capture schema stays reviewable on its own.

Verification:

* [x] Flyway discovers V3.
* [x] `git log` shows V1 and V2 unchanged in this phase.

Engineering log:

* Record the migration filename and the purpose of V3.
* Record confirmation that V1 and V2 were not edited.

Ready for 3.2.2 when:

* V3 exists and earlier migrations are untouched

---

## 3.2.2 Create the settlement journal table

What is a settlement journal?

* A journal is the permanent record of one completed settlement.
* It is the anchor for the four postings and the reason the trade's balances changed.

* [x] Create `settlement_journal`.

Store:

```text
id
trade_id
settled_at
```

* [x] Use a UUID primary key, consistent with Phase 1 and Phase 2.

* [x] Add constraints:

  * `trade_id` references `trade`
  * `trade_id` is unique
  * `settled_at` is `TIMESTAMPTZ NOT NULL`

Why:

* The unique `trade_id` is the database-level implementation of I2: one trade can produce at most one settlement journal, even if application logic is bypassed.
* `settled_at` records when the settlement committed, which the inspection API and later reconciliation evidence both need.

Verification:

* [x] A journal can be inserted for an existing trade.
* [x] A second journal for the same trade fails.
* [x] A journal for an unknown trade fails.

Engineering log:

* Record the table, columns and constraints.
* Record which invariant each constraint protects.

Ready for 3.2.3 when:

* the journal table exists and enforces one journal per trade

---

## 3.2.3 Create the posting table

What is a posting?

* A posting is one balance movement inside one journal.
* A successful settlement creates exactly four.

* [x] Create `posting`.

Store:

```text
id
journal_id
account_id
direction
amount
signed_amount   (generated)
```

* [x] Add constraints:

  * `journal_id` references `settlement_journal`
  * `account_id` references `account`
  * `direction` is restricted to `DEBIT` or `CREDIT`
  * `amount > 0`
  * `(journal_id, account_id)` is unique

* [x] Generate the signed effect from the direction and amount.

Conceptually:

```sql
signed_amount BIGINT GENERATED ALWAYS AS (
    CASE WHEN direction = 'DEBIT' THEN -amount ELSE amount END
) STORED
```

* [x] Do not store an asset column on the posting.

Why:

* `amount > 0` with an explicit direction keeps the recorded movement unambiguous and makes an accidental sign inversion visible.
* The generated column means balance reconstruction and conservation checks are plain sums, and the sign rule cannot drift between Java and SQL.
* Unique `(journal_id, account_id)` means the four postings of one settlement must touch four different accounts, which is half of the correct journal shape.

Verification:

* [x] A valid posting can be inserted.
* [x] `amount = 0` and negative amounts fail.
* [x] An unsupported direction fails.
* [x] A duplicate account within one journal fails.
* [x] `signed_amount` is negative for `DEBIT` and positive for `CREDIT`.
* [x] An `UPDATE` of `signed_amount` is not possible.

Engineering log:

* Record the table, constraints and the generated column.
* Record the approved sign convention as implemented.

Ready for 3.2.4 when:

* postings can only be recorded in the approved shape

---

## 3.2.4 Create the settlement attempt table

What is a settlement attempt?

* A settlement attempt is a committed settlement *decision*.
* It exists because a rejected settlement leaves the trade `READY` with no journal, and that decision must still be visible afterwards (ADR-011).

* [x] Create `settlement_attempt`.

Store:

```text
id
trade_id
command_key
outcome
journal_id
business_date
decided_at
```

* [x] Add constraints:

  * `trade_id` references `trade`
  * `command_key` references `command_result`
  * `command_key` is unique
  * `outcome` is restricted to the approved vocabulary
  * `journal_id` references `settlement_journal`
  * `journal_id` is present exactly when the outcome is `SETTLED` or `ALREADY_SETTLED`
  * at most one `SETTLED` attempt per trade
  * `business_date` and `decided_at` are `NOT NULL`

Conceptually:

```sql
CONSTRAINT settlement_attempt_journal_consistency CHECK (
    (outcome IN ('SETTLED', 'ALREADY_SETTLED')) = (journal_id IS NOT NULL)
)

CREATE UNIQUE INDEX settlement_attempt_one_settled_per_trade
    ON settlement_attempt (trade_id)
    WHERE outcome = 'SETTLED';
```

Why:

* Unique `command_key` makes the attempt policy a database guarantee: one committed decision per command, so a replay cannot add a second attempt.
* The partial unique index is a second independent guard on I2, beside the unique journal `trade_id`.
* Linking an `ALREADY_SETTLED` attempt to the existing journal explains the rejection instead of only naming it.
* `business_date` makes a `NOT_DUE` decision reproducible under the approved C2 rule.
* The `command_result` foreign key is satisfiable because the claim row is inserted earlier in the same transaction.

Verification:

* [x] Each approved outcome can be inserted.
* [x] An unsupported outcome fails.
* [x] `SETTLED` without a journal fails.
* [x] A rejection with a journal fails.
* [x] A second attempt for the same command key fails.
* [x] A second `SETTLED` attempt for the same trade fails.
* [x] An attempt for an unknown command key fails.

Engineering log:

* Record the table and why each field and constraint exists.

Ready for 3.2.5 when:

* committed settlement decisions can be recorded and cannot duplicate

---

## 3.2.5 Extend the trade and command-result tables

What is changing?

* V2 deliberately allowed only `READY` trades and only the `CAPTURE_TRADE` operation. Both now need one more value.

* [x] Extend the trade status constraint.

```sql
ALTER TABLE trade DROP CONSTRAINT trade_status_supported;
ALTER TABLE trade ADD CONSTRAINT trade_status_supported
    CHECK (status IN ('READY', 'SETTLED'));
```

* [x] Add the trade-to-journal relationship.

  * add nullable `journal_id`
  * foreign key to `settlement_journal`
  * unique `journal_id`
  * `CHECK ((status = 'SETTLED') = (journal_id IS NOT NULL))`
  * `trade.journal_id` must reference the journal whose `trade_id` is that same trade, so Trade A cannot point at Trade B's journal

* [x] Extend the command-result operation constraint.

```sql
ALTER TABLE command_result DROP CONSTRAINT command_result_operation_supported;
ALTER TABLE command_result ADD CONSTRAINT command_result_operation_supported
    CHECK (operation IN ('CAPTURE_TRADE', 'SETTLE_TRADE'));
```

* [x] Leave every existing V2 column, constraint, trigger and row untouched.

  * existing trades stay `READY` with `journal_id IS NULL`
  * existing capture command results stay exactly as stored

Why:

* Replacing a named check constraint is the correct append-only way to widen an enumerated column without editing an applied migration.
* The circular reference between `trade` and `settlement_journal` is safe because the settlement transaction inserts the journal first and then updates the trade, so neither insert sees a missing row.
* `UNIQUE (journal_id)` allows many `NULL` values but only one trade per journal, which is the intended relationship.

Verification:

* [x] `SETTLED` is accepted after V3 and was rejected before V3.
* [x] `SETTLED` with `journal_id IS NULL` fails.
* [x] `READY` with a journal fails.
* [x] Two trades cannot share one journal.
* [x] Trade A cannot point `journal_id` at Trade B's journal.
* [x] `SETTLE_TRADE` is accepted as a command operation.
* [x] An unsupported operation still fails.

Engineering log:

* Record each `ALTER TABLE` and why it is required.
* Record that existing rows were not rewritten.

Ready for 3.2.6 when:

* the trade can hold the settled state and its journal
* command results can hold the settlement operation

---

## 3.2.6 Protect settlement history from modification

What is being protected?

* I9 requires that committed journals and postings cannot be silently modified through normal application operations.
* Phase 2 already used this pattern: `command_result` has a trigger that refuses to overwrite a completed result.

* [x] Add a trigger function that rejects `UPDATE` and `DELETE`.

* [x] Attach it to:

  * `settlement_journal`
  * `posting`
  * `settlement_attempt`

* [x] Add a trade update guard.

  * reject any change to the captured economic terms
  * reject any update to a row that is already `SETTLED`
  * allow exactly the `READY -> SETTLED` transition that also sets `journal_id`

* [x] Add a deferred constraint trigger that validates the journal shape at commit.

Conceptually:

```sql
CREATE CONSTRAINT TRIGGER settlement_journal_shape
    AFTER INSERT ON settlement_journal
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION settlement_journal_assert_shape();
```

The function asserts, for the journal's trade:

```text
exactly 4 postings
2 distinct assets, one CASH and one SECURITY
per-asset net signed amount = 0
cash postings amount = trade.cash_amount
security postings amount = trade.quantity
buyer cash DEBIT, seller cash CREDIT
buyer security CREDIT, seller security DEBIT
the two cash postings use the buyer and seller AUD accounts
the two security postings use the buyer and seller accounts for exactly trade.security_id
```

* [x] Do not make the immutability triggers block test cleanup.

  * `TRUNCATE` does not fire row-level `UPDATE`/`DELETE` triggers, so the existing cleanup mechanism still works.

Why:

* A deferred constraint trigger can check a multi-row shape that no single-row constraint can express, because it runs when the whole settlement is complete.
* Because it fires at commit, a shape violation surfaces as a failed commit and a rolled-back settlement. That is exactly the required behaviour: the response is only produced after the commit succeeds.
* Immutable history is what makes the journal usable as evidence and later as reconciliation input.

Verification:

* [x] `UPDATE` and `DELETE` on a journal, posting and attempt all fail.
* [x] A journal committed with three postings fails at commit.
* [x] A journal whose cash legs do not net to zero fails at commit.
* [x] A journal whose amounts disagree with the trade terms fails at commit.
* [x] Reversing a posting direction fails at commit.
* [x] Cash postings to a non-AUD account fail at commit.
* [x] Security postings to an account whose asset is not `trade.security_id` fail at commit.
* [x] `READY -> SETTLED` with a journal succeeds.
* [x] Editing a settled trade fails.
* [x] Editing captured terms fails.
* [x] Test cleanup still truncates cleanly.

Engineering log:

* Record each trigger, what it rejects and which invariant it protects.
* Record that the shape check is deferred and therefore reported at commit.

Ready for 3.2.7 when:

* settlement history cannot be edited through ordinary SQL
* an incorrectly shaped journal cannot commit

---

## 3.2.7 Verify V3 and preserve Phase 1 and Phase 2 state

* [x] Update `AbstractPostgresIntegrationTest` cleanup.

```text
TRUNCATE TABLE settlement_attempt, posting, settlement_journal,
               command_result, trade, account, participant, asset
```

* All referencing tables are listed in one statement, so the foreign keys allow the truncate.

* [x] Update `PostgresStartupIntegrationTest`.

  * applied migrations are V1, V2, V3
  * current version is 3
  * tables are `account`, `asset`, `command_result`, `flyway_schema_history`, `participant`, `posting`, `settlement_attempt`, `settlement_journal`, `trade`
  * `reconciliation` tables still do not exist

* [x] Add a V3 schema constraint integration test covering 3.2.2 to 3.2.6.

* [x] Add a V2 -> V3 upgrade integration test.

  * start with V1 and V2 only
  * load the Phase 1 seed data
  * capture a trade and a command result
  * apply V3
  * confirm participants, assets, accounts, the trade and the command result are unchanged
  * confirm the trade is still `READY` with `journal_id IS NULL`
  * confirm `settlement_journal`, `posting` and `settlement_attempt` start empty

Why:

* A migration must work on the database that actually exists, not only on a clean one.
* Phase 3 must not reset balances, rewrite captured trades or invalidate stored capture outcomes.
* The startup test is the repository's guard against a migration silently adding or losing a table.

Verification:

* [x] V1 + V2 + V3 apply to a clean PostgreSQL database.
* [x] A V2 database upgrades to V3 without changing existing rows.
* [x] V3 constraints reject invalid settlement state.
* [x] All existing Phase 1 and Phase 2 tests still pass.
* [x] `./mvnw verify` passes.

Engineering log:

* Record migration tests, failures and corrections.
* Record the updated cleanup and startup assertions.

Ready for 3.3 when:

* V3 is reproducible
* existing Phase 1 and Phase 2 data survives the upgrade
* database-level settlement guarantees work

Section 3.2 is complete. Do not begin Section 3.3 until requested.

---

# 3.3 Settlement domain types

Purpose:

Create the Java types that represent settlement history, settlement decisions and the settlement command.

---

## 3.3.1 Extend the trade domain for the settled state

* [x] Add `SETTLED` to `TradeStatus`.

* [x] Add a nullable `journalId` to `Trade`.

* [x] Leave `TradeTerms` unchanged.

  * Settlement never alters captured economic terms.

* [x] Update `TradeRepository` row mapping and every existing construction site.

Why:

* The trade row is the authoritative current state, and after Phase 3 that state includes whether it settled and which journal explains it.
* Keeping `TradeTerms` untouched preserves the Phase 2 guarantee that accepted terms are immutable, and keeps `CaptureRequestIdentity` producing exactly the same strings.

Verification:

* [x] Domain types compile.
* [x] A `READY` trade maps with a `null` journal id.
* [x] Existing Phase 2 tests still pass after the record change.

Engineering log:

* Record the domain change and the files it touched.

Ready for 3.3.2 when:

* the trade type can represent a settled trade

---

## 3.3.2 Create the journal and posting domain types

* [x] Create `SettlementJournal`.

```text
id
tradeId
settledAt
```

* [x] Create `Posting`.

```text
id
journalId
accountId
direction
amount
signedAmount
```

* [x] Create `PostingDirection`.

```text
DEBIT
CREDIT
```

* `DEBIT` decreases the account balance. `CREDIT` increases it. These are project-local balance-movement directions, not general-ledger / GAAP debit-credit semantics.

* [x] Keep the types immutable records with no persistence annotations and no behaviour that mutates balances.

Why:

* Java needs typed representations of the committed financial history for inspection and for test assertions.
* Reading `signedAmount` from the database rather than recomputing it in Java keeps one authority for the sign rule.

Verification:

* [x] Types compile.
* [x] Every approved column is represented.

Engineering log:

* Record each type, what it represents and its fields.

Ready for 3.3.3 when:

* journals and postings can be represented in Java

---

## 3.3.3 Create the settlement attempt and outcome types

* [x] Create `SettlementOutcome`.

```text
SETTLED
ALREADY_SETTLED
NOT_DUE
INSUFFICIENT_CASH
INSUFFICIENT_SECURITIES
```

* [x] Create `SettlementAttempt`.

```text
id
tradeId
commandKey
outcome
journalId
businessDate
decidedAt
```

* [x] Keep the enum aligned with the database check constraint.

Why:

* A typed outcome makes the committed decision explicit in the service, the attempt row and the inspection response, instead of a loose string.
* Alignment with the constraint means an unhandled outcome fails fast rather than being written as unexpected data.

Verification:

* [x] Types compile.
* [x] A small test asserts the enum values match the values accepted by the database.

Engineering log:

* Record the outcome vocabulary and the attempt type.

Ready for 3.3.4 when:

* settlement decisions can be represented in Java

---

## 3.3.4 Create the settlement command and its request identity

* [x] Create `SettleCommand`.

```text
idempotencyKey
tradeId
```

* [x] Create `SettleRequestIdentity`.

  * operation `SETTLE_TRADE`
  * the trade id

* [x] Extract the length-prefixed encoding shared with `CaptureRequestIdentity` into one internal helper.

  * Both identities must use one canonical encoding.
  * The produced capture identity string must not change.

Why:

* Command identity and business identity stay independent, exactly as in capture: the key identifies the request, the trade id identifies the business object.
* One shared encoder means the reasoning behind I8 lives in one place.
* Capture identities are already stored in `command_result`, so a changed encoding would silently break replay for existing databases. That risk is why the regression test below is required.

Verification:

* [x] Types compile.
* [x] A regression test asserts the capture identity string for a fixed set of terms is byte-identical to the Phase 2 encoding.
* [x] Two settle commands for the same trade share an identity.
* [x] The same key against a different trade does not.
* [x] A capture identity and a settle identity never collide.

Engineering log:

* Record the settlement command types.
* Record the shared encoding refactor and the regression test protecting stored capture identities.

Ready for 3.4 when:

* the settlement domain types exist
* the settlement command identity is defined and proven not to change capture behaviour

Section 3.3 is complete. Do not begin Section 3.4 until requested.

---

# 3.4 Settlement JDBC persistence

Purpose:

Create the explicit SQL operations the settlement service needs, including row locking and balance updates.

Repositories provide SQL operations. They do not own the settlement transaction.

---

## 3.4.1 Add trade locking and the settled transition

What is `SELECT ... FOR UPDATE`?

* It reads a row and holds a row-level lock on it until the transaction ends.
* A second transaction trying to lock the same row waits.
* This is the PostgreSQL mechanism ADR-007 relies on. Java locks are deliberately not used.

* [x] Add `lockById(UUID)` to `TradeRepository`.

```sql
SELECT ... FROM trade WHERE id = :tradeId FOR UPDATE
```

* [x] Add `markSettled(UUID tradeId, UUID journalId)`.

```sql
UPDATE trade
SET status = 'SETTLED', journal_id = :journalId
WHERE id = :tradeId AND status = 'READY'
```

* [x] Require exactly one updated row, and fail loudly otherwise.

* [x] Do not add any method that changes captured terms.

Why:

* The trade lock is the first step of the settlement protocol. It serializes settlement commands for the same trade before any account is touched.
* Every state decision must be made from the locked read, not from an earlier unlocked read, because an earlier read can already be stale.
* The `AND status = 'READY'` guard makes the transition itself a conditional write, so the code cannot settle a trade twice even if a check above it were removed.
* Requiring one affected row follows the Phase 2 correction that made `finalize` exact rather than best-effort.

Verification:

* [x] A locked read returns the current trade.
* [x] An unknown id returns empty.
* [x] `markSettled` sets the status and the journal id.
* [x] `markSettled` on an already settled trade affects no rows and fails.

Engineering log:

* Record the SQL introduced and why the lock and the conditional update exist.

Ready for 3.4.2 when:

* a trade can be locked and settled through explicit SQL

---

## 3.4.2 Add account resolution, locking and balance updates

* [x] Add `findIdByParticipantAndAsset(UUID participantId, UUID assetId)` to `AccountRepository`.

  * resolves one of the four affected accounts without locking

* [x] Add `findIdByAssetCode(String code)` or an `AssetRepository.findByCode(String)` lookup for the AUD cash asset.

* [x] Add `lockBalance(UUID accountId)`.

```sql
SELECT id, asset_id, current_balance
FROM account
WHERE id = :accountId
FOR UPDATE
```

* [x] Add `applyDelta(UUID accountId, long delta)`.

```sql
UPDATE account
SET current_balance = current_balance + :delta
WHERE id = :accountId
```

* [x] Require exactly one updated row.

* [x] Do not add a method that sets a balance to an arbitrary value.

Why:

* Locking must use a single-table query. The existing read query joins `participant` and `asset`, and `FOR UPDATE` on a join would also lock those reference rows, which would serialize unrelated trades for no reason.
* A relative `+ delta` update applies the movement to whatever the locked row currently holds, so a settlement can never overwrite a concurrent committed change.
* There is deliberately no absolute balance setter, because that would be the arbitrary financial-state mutation path the specification forbids.
* The non-negative `CHECK` constraints from V1 remain the last line of defence if application validation is ever bypassed.

Verification:

* [x] Account resolution finds the Alice/Bob AUD and EQ1 accounts.
* [x] An unknown participant/asset pair returns empty.
* [x] The AUD asset resolves by code.
* [x] A locked read returns the current balance.
* [x] `applyDelta` increases and decreases a balance.
* [x] `applyDelta` for an unknown account fails.
* [x] A delta that would make a balance negative is rejected by the database constraint.

Engineering log:

* Record the SQL introduced.
* Record why locking avoids the joined read query.
* Record that no absolute balance setter exists.

Ready for 3.4.3 when:

* the four accounts can be resolved, locked and adjusted through explicit SQL

---

## 3.4.3 Create the settlement journal repository

* [x] Create `SettlementJournalRepository`.

Required operations:

```text
insertJournal(tradeId)
insertPostings(journalId, four postings)
findJournalById(id)
findJournalByTradeId(tradeId)
findPostingsByJournalId(journalId)
```

* [x] Generate the journal UUID in Java and return the inserted row.

* [x] Insert the postings as one batch and require exactly four affected rows.

* [x] Return postings in a deterministic order.

  * order by asset code, then direction, then account id
  * cash legs and security legs read as pairs during inspection

* [x] Do not add any update or delete operation.

Why:

* A repository with no mutation path for committed history makes I9 structural rather than a coding convention.
* Requiring four affected rows means a mis-sized posting batch fails inside the transaction rather than at the deferred commit check.
* Deterministic ordering keeps inspection responses and test assertions stable.

Verification:

* [x] A journal can be inserted and read back by id and by trade id.
* [x] Four postings insert and read back in the expected order.
* [x] A second journal for the same trade fails.
* [x] `signedAmount` values are negative for debits and positive for credits.
* [x] The repository exposes no way to change or remove a committed journal or posting.

Engineering log:

* Record the SQL introduced and the posting ordering rule.

Ready for 3.4.4 when:

* journals and postings can be written once and read back

---

## 3.4.4 Create the settlement attempt repository

* [x] Create `SettlementAttemptRepository`.

Required operations:

```text
insert(tradeId, commandKey, outcome, journalId, businessDate)
findByTradeId(tradeId)
```

* [x] Generate the attempt UUID in Java.

* [x] Order attempts by `decided_at`, then id.

* [x] Do not add an update or delete operation.

Why:

* Recorded decisions are history too. They explain why a trade is still `READY`.
* Ordering makes `GET /v1/trades/{id}/attempts` a readable timeline.

Verification:

* [x] Each approved outcome can be recorded.
* [x] A rejection is recorded without a journal.
* [x] A settled attempt is recorded with its journal.
* [x] A second attempt for the same command key fails.
* [x] Attempts read back in decision order.

Engineering log:

* Record the SQL introduced and the ordering rule.

Ready for 3.4.5 when:

* committed settlement decisions can be recorded and listed

---

## 3.4.5 Extend the command-result repository for settlement

* [x] Change `claim` to take the operation explicitly.

  * it currently hardcodes `CAPTURE_TRADE`
  * capture passes `CAPTURE_TRADE`, settlement passes `SETTLE_TRADE`

* [x] Keep `findByCommandKey` and the exact `finalize` behaviour unchanged.

* [x] Keep identity comparison as string equality against the stored `request_identity`.

Why:

* One command-result table serves both operations under the approved single global key namespace.
* The stored operation makes an inspected command outcome self-describing, and keeps the database check constraint meaningful.
* The request identity already encodes the operation, so identity equality implies operation equality. Storing both is cheap and makes the intent explicit.

Verification:

* [x] A settlement key can be claimed with the settlement operation.
* [x] A duplicate key cannot create a second row.
* [x] A capture key reused for settlement is detected as a changed request.
* [x] Existing Phase 2 capture tests still pass unchanged in behaviour.

Engineering log:

* Record the signature change and every call site updated.

Ready for 3.4.6 when:

* settlement commands can claim and finalize durable outcomes

---

## 3.4.6 Verify the settlement persistence layer

* [x] Add PostgreSQL integration tests for:

  * trade locking and the settled transition
  * account resolution, locking and balance deltas
  * journal and posting insert/read
  * attempt insert/read
  * command claim with the settlement operation

* [x] Use the existing Testcontainers PostgreSQL 18.6 support and the shared cleanup.

* [x] Keep each test independent.

* [x] Do not add concurrency tests here.

  * Two competing transactions belong to Phase 4.
  * Phase 3 only proves that the locking SQL exists, runs and returns the locked row.

Why:

* Each SQL operation should be proven on its own before the service composes them into a financial transaction.
* Separating "the lock statement works" from "the lock protects against a competing transaction" keeps the Phase 3/Phase 4 boundary honest.

Verification:

* [x] All new persistence tests pass.
* [x] `./mvnw verify` passes.

Engineering log:

* Record the tests added and their results.

Ready for 3.5 when:

* every SQL operation settlement needs is implemented and independently tested

Section 3.4 is complete. Do not begin Section 3.5 until requested.

---

# 3.5 Settlement transaction, locking and validation

Purpose:

Create the settlement service, own its PostgreSQL transaction, and implement the protocol up to the point where the decision is known.

This is the first operation in the project that may change financial state.

---

## 3.5.1 Create the service-owned settlement transaction boundary

What must commit together?

```text
command key claim
        +
journal + four postings
        +
four balance updates
        +
trade READY -> SETTLED
        +
settlement attempt
        +
durable command outcome
```

If a technical failure happens before commit:

```text
none of it remains
```

* [ ] Create `SettleTradeService` in the application package.

* [ ] Give the service ownership of the settlement transaction.

* [ ] Use `TransactionTemplate` over the same `PlatformTransactionManager` and `DataSource` the repositories use.

* [ ] Keep the controller outside the transaction.

* [ ] Return the outcome only after the transaction completes successfully.

Conceptually:

```text
SettleTradeService
        |
        v
TransactionTemplate
        |
        +--> CommandResultRepository
        +--> TradeRepository
        +--> AccountRepository
        +--> SettlementJournalRepository
        +--> SettlementAttemptRepository
        |
        v
COMMIT / ROLLBACK
```

* [ ] Use the default isolation level.

  * The correctness argument is explicit row locking, not a higher isolation level (ADR-007).

* [ ] Inject the `BusinessCalendar` from 3.1.2 rather than reading the system clock inline.

Why:

* ADR-006 requires both asset legs and their supporting records to commit in one PostgreSQL transaction. That is what makes DvP atomic here.
* Reusing the Phase 2 transaction pattern keeps one transaction mechanism in the project instead of two.
* The success response must not exist before the commit is known to have succeeded, including the deferred journal-shape check that runs at commit.

Verification:

* [ ] The service uses the expected transaction manager and data source.
* [ ] Repository operations participate in the service transaction.
* [ ] A technical exception rolls the transaction back.

Engineering log:

* Record the transaction mechanism and why the service owns it.
* Record the injected clock/business-calendar wiring.

Ready for 3.5.2 when:

* the settlement transaction boundary exists and is owned by the service

---

## 3.5.2 Claim the settlement command and replay stored outcomes

* [ ] As the first step inside the transaction, build the settle request identity and claim the key.

* [ ] If the claim succeeded, continue with a new decision.

* [ ] If the key already exists:

  * same request identity and a completed result

    * return the stored status, body and location unchanged
    * write nothing

  * same request identity and an unfinished result

    * fail loudly; an unfinished row cannot be visible after commit

  * different request identity

    * return `409 IDEMPOTENCY_KEY_CONFLICT`
    * do not overwrite the stored result
    * record no attempt

* [ ] Do not read or lock the trade before the claim.

Why:

* Claiming first means a duplicate command is answered from durable storage rather than by repeating financial work.
* The replay path performs no financial reads or writes at all, which is what makes retry provably free of new financial effects (I8).
* This mirrors the Phase 2 capture sequence, so both operations behave the same way under retry.

Verification:

* [ ] A new key is claimed once.
* [ ] A replay with the same key and trade returns the original outcome and writes nothing.
* [ ] The same key against a different trade returns the approved conflict.
* [ ] No settlement attempt is recorded on either replay path.

Engineering log:

* Record the claim/replay order as implemented.

Ready for 3.5.3 when:

* settlement commands are idempotent before any financial work happens

---

## 3.5.3 Lock the trade and check state and due date

* [ ] Lock the trade with `lockById`.

* [ ] If the trade does not exist:

  * throw so the transaction rolls back
  * the API returns `404 UNKNOWN_TRADE`
  * no command claim is committed

* [ ] If the locked trade is already `SETTLED`:

  * record an `ALREADY_SETTLED` attempt linked to the existing journal
  * finalize `409 ALREADY_SETTLED`
  * commit the decision
  * change no balances

* [ ] Compute the business date from the injected calendar.

* [ ] If `settlementDate > businessDate`:

  * record a `NOT_DUE` attempt with the evaluated business date
  * finalize `422 NOT_DUE`
  * commit the decision
  * leave the trade `READY`

* [ ] Make every state decision from the locked row.

Why:

* Locking the trade before evaluating it is what makes the evaluation trustworthy. An unlocked read can be stale by the time the decision is applied.
* Locking the trade first, before any account, is the fixed lock order from ADR-007.
* Rejections commit a decision without financial movement, which is exactly ADR-011: the trade stays `READY` and the decision is recorded separately.
* An unknown trade cannot record an attempt, because the attempt references the trade. Rolling back is therefore the only consistent answer, and it also leaves the key reusable.

Verification:

* [ ] A settled trade produces the approved conflict, an `ALREADY_SETTLED` attempt and no balance change.
* [ ] A future-dated trade produces `NOT_DUE`, stays `READY` and changes no balance.
* [ ] A trade dated today settles.
* [ ] An overdue trade settles.
* [ ] An unknown trade returns `404` and leaves no `command_result` row.

Engineering log:

* Record the locked-read decision order.
* Record the C2 rule as implemented.

Ready for 3.5.4 when:

* trade state and due date are decided under the trade lock

---

## 3.5.4 Resolve the four affected accounts

What are the four accounts?

```text
buyer  cash      = buyer  + AUD
seller cash      = seller + AUD
buyer  security  = buyer  + trade.security
seller security  = seller + trade.security
```

* [ ] Resolve the AUD cash asset using the approved rule.

* [ ] Resolve the four account ids without locking them.

* [ ] Assert the four resolved ids are distinct.

* [ ] If any account does not exist:

  * do not record a settlement attempt
  * do not finalize a durable command result
  * fail the transaction
  * roll back the command claim and every settlement write
  * expose only the existing safe `500 INTERNAL_ERROR` at the HTTP boundary

Why:

* Deterministic locking needs the account ids before any lock is taken, so resolution is a separate step from locking.
* The four accounts are distinct by construction, because capture already rejects a self-trade and requires a `SECURITY` asset. Asserting it anyway means a future reference-data mistake cannot collapse two legs into one account and appear to balance.
* Phase 1 guarantees an account per participant/asset pair. A missing required account is therefore an internal financial-state / data-integrity failure, not a client business rejection.

Verification:

* [ ] The Alice/Bob trade resolves to the four seeded accounts.
* [ ] A trade whose buyer has no AUD account rolls back with a safe `500 INTERNAL_ERROR`, no settlement attempt, and no durable command result.
* [ ] The resolution step performs no locking and no writes.

Engineering log:

* Record the resolution rule and the cash asset lookup.
* Record the distinctness assertion.

Ready for 3.5.5 when:

* the four affected accounts are identified before any account lock

---

## 3.5.5 Lock the four accounts in deterministic account-ID order

* [ ] Sort the four account ids ascending.

* [ ] Lock them one at a time, in that order, with `lockBalance`.

* [ ] Hold the locks until the transaction ends.

  * There is no unlock step. Transaction completion releases them.

* [ ] Use the balances returned by the locking reads.

* [ ] Do not use the single-statement `ORDER BY ... FOR UPDATE` form.

  * Four explicit statements make the lock order visible in the code and independent of query planning.

* [ ] Do not add any Java-level lock, synchronized block or in-process mutex.

Why:

* Consistent global lock ordering is what keeps two settlements that share accounts from each holding what the other needs (ADR-007).
* Unrelated trades touch different account rows, so they are not serialized against each other. A global settlement lock is explicitly rejected.
* Java locks cannot coordinate multiple application processes, and PostgreSQL is the intended source of concurrency correctness.
* Ordering does not make deadlock impossible, which is why Phase 4 still has to test contention rather than assume it.

Verification:

* [ ] The service locks the trade first, then the four accounts ascending by id.
* [ ] A test asserts the recorded lock order for a known trade.
* [ ] No Java locking primitive appears in the settlement path.
* [ ] Concurrency behaviour under competing transactions is deliberately left to Phase 4.

Engineering log:

* Record the lock order as implemented and how it was observed.
* Record why four separate statements were used.

Ready for 3.5.6 when:

* all four account locks are acquired in deterministic order

---

## 3.5.6 Validate cash and securities only after the locks are held

* [ ] Validate after locking, never before.

* [ ] Check buyer cash first.

```text
buyer cash current_balance >= trade.cash_amount
```

* [ ] Then check seller securities.

```text
seller security current_balance >= trade.quantity
```

* [ ] On insufficient cash:

  * record an `INSUFFICIENT_CASH` attempt
  * finalize `422 INSUFFICIENT_CASH`
  * commit the decision
  * leave the trade `READY` and every balance unchanged

* [ ] On insufficient securities:

  * record an `INSUFFICIENT_SECURITIES` attempt
  * finalize `422 INSUFFICIENT_SECURITIES`
  * commit the decision
  * leave the trade `READY` and every balance unchanged

* [ ] If both are insufficient, report `INSUFFICIENT_CASH`.

  * A fixed evaluation order makes the recorded decision deterministic.

* [ ] Do not reserve, hold or partially apply anything.

Why:

* A balance read before the lock can be stale, so a check performed before locking proves nothing. Validating under the lock is the whole point of the protocol.
* Rejecting the entire settlement rather than moving one leg is gross trade-by-trade settlement (ADR-005) and the direct implementation of DvP.
* No reservations exist between commands (ADR-011), so a rejected attempt leaves the resources available to any other trade.

Verification:

* [ ] A buyer with insufficient cash produces `INSUFFICIENT_CASH`, no postings, no journal, unchanged balances and a `READY` trade.
* [ ] A seller with insufficient securities produces `INSUFFICIENT_SECURITIES` with the same guarantees.
* [ ] Both insufficient produces `INSUFFICIENT_CASH`.
* [ ] An exactly sufficient balance settles.
* [ ] Validation is proven to read the post-lock balance.

Engineering log:

* Record the validation order and the recorded outcomes.
* Record that validation happens after locking.

Ready for 3.6 when:

* every rejection path commits a decision with no financial movement
* a valid settlement is ready to be written

---

# 3.6 Settlement writes and durable outcome

Purpose:

Write the financial effect of an approved settlement, then prove the whole write set rolls back if anything fails before commit.

---

## 3.6.1 Create exactly one settlement journal

* [ ] Insert one journal for the trade.

* [ ] Let the unique `trade_id` constraint be the final authority.

* [ ] Do not create a journal on any rejection path.

Why:

* The journal is the record that explains the balance changes, so it is created before the postings that belong to it.
* Relying on the unique constraint means a duplicate settlement fails in the database, not only in the check above it.

Verification:

* [ ] A successful settlement produces exactly one journal for the trade.
* [ ] `settled_at` is populated.
* [ ] No rejection path creates a journal.

Engineering log:

* Record the journal write and its guarantee.

Ready for 3.6.2 when:

* one journal exists for the settlement being written

---

## 3.6.2 Create exactly four postings

* [ ] Build the four postings from the trade terms and the resolved accounts.

```text
buyer  cash      DEBIT   cash_amount
seller cash      CREDIT  cash_amount
buyer  security  CREDIT  quantity
seller security  DEBIT   quantity
```

* [ ] Insert them as one batch.

* [ ] Require exactly four affected rows.

* [ ] Do not insert a fifth posting, a netted posting or a zero-amount posting.

Why:

* I6 requires exactly four postings to the correct participant and asset accounts. Writing them as one batch keeps the shape a single decision.
* The postings are the evidence for the balance changes that follow, and the only thing balance reconstruction can be derived from.
* The deferred shape trigger re-checks the same facts at commit, so an incorrect set cannot survive even if this code is later changed.

Verification:

* [ ] Exactly four postings exist after a successful settlement.
* [ ] Each posting has the approved account, direction and amount.
* [ ] Cash postings net to zero and security postings net to zero.
* [ ] A deliberately malformed posting set fails at commit.

Engineering log:

* Record the four postings written and their direction/amount mapping.

Ready for 3.6.3 when:

* the journal has its four correct postings

---

## 3.6.3 Update the four account balances

* [ ] Apply the four deltas.

```text
buyer  cash      -cash_amount
seller cash      +cash_amount
buyer  security  +quantity
seller security  -quantity
```

* [ ] Apply them in ascending account-ID order, matching the lock order.

* [ ] Require exactly one affected row per update.

* [ ] Let the non-negative `CHECK` constraints stand as the final guard.

Why:

* The current balance is a projection of the postings (ADR-008), so the postings and the projection must be written in the same transaction or the two can disagree.
* Relative updates against locked rows mean the arithmetic is applied to the value that was actually validated.
* Keeping the update order equal to the lock order makes the code read in one consistent order, which matters when this path is reviewed against ADR-007.

Verification:

* [ ] The Alice/Bob settlement produces Alice AUD 50000, Alice EQ1 10, Bob AUD 50000, Bob EQ1 0.
* [ ] Opening balances are unchanged.
* [ ] Total AUD across accounts is unchanged.
* [ ] Total EQ1 across accounts is unchanged.
* [ ] Each update affects exactly one row.

Engineering log:

* Record the four balance updates and the observed balances.

Ready for 3.6.4 when:

* the four balances reflect the four postings

---

## 3.6.4 Transition the trade from READY to SETTLED

* [ ] Call `markSettled` with the journal id.

* [ ] Require exactly one affected row.

* [ ] Do not change any captured term.

Why:

* The conditional `WHERE ... AND status = 'READY'` update is the narrowest possible write for the transition, and it cannot settle a trade twice.
* The status/journal check constraint means a settled trade always has exactly one journal and a `READY` trade never does.
* The trade row is the state a reviewer reads first, so it must point at the evidence.

Verification:

* [ ] The trade becomes `SETTLED` with its journal id.
* [ ] The captured terms are unchanged.
* [ ] A further update attempt on the settled trade fails.

Engineering log:

* Record the transition and its guard.

Ready for 3.6.5 when:

* the trade state matches its committed financial effect

---

## 3.6.5 Record the durable settlement outcome and the attempt

* [ ] Record a `SETTLED` settlement attempt.

  * linked to the journal
  * with the evaluated business date

* [ ] Serialize the settlement response body.

```text
tradeId
status
outcome
journalId
settledAt
```

* [ ] Finalize the command result with `201`, the body and `Location: /v1/journals/{journalId}`.

* [ ] Do everything above before the transaction ends.

* [ ] Return the outcome to the caller only after the transaction completes.

Why:

* The durable outcome is what makes a lost response safe to retry. If it committed separately from the financial effect, retry safety would be a race.
* The attempt records that this command was the one that settled the trade, which links the command history and the financial history.
* Finalizing inside the same transaction is also why the deferred journal-shape check is meaningful: an inconsistent settlement cannot leave a stored success behind.

Verification:

* [ ] One completed command result exists with the approved status, body and location.
* [ ] One `SETTLED` attempt exists, linked to the journal.
* [ ] A replay returns the identical status, body and location.
* [ ] A replay adds no attempt, journal, posting or balance change.

Engineering log:

* Record the durable outcome and the attempt written.

Ready for 3.6.6 when:

* a successful settlement is fully recorded and replayable

---

## 3.6.6 Verify a complete successful settlement

Using real PostgreSQL and the deterministic seed:

```text
Before
Alice AUD 100000   Alice EQ1 0
Bob   AUD 0        Bob   EQ1 10

Settle T-001: Alice buys 10 EQ1 from Bob for 50000

After
Alice AUD 50000    Alice EQ1 10
Bob   AUD 50000    Bob   EQ1 0
```

* [ ] Assert exactly one journal.

* [ ] Assert exactly four postings with the correct accounts, directions and amounts.

* [ ] Assert the four balances.

* [ ] Assert the trade is `SETTLED` and points at the journal.

* [ ] Assert one completed command result.

* [ ] Assert one `SETTLED` attempt.

* [ ] Assert opening balances were not touched.

* [ ] Replay the same command and assert nothing changed.

Why:

* This is the first project milestone from `docs/implementation-plan.md` and the core F2 acceptance case.
* Asserting the whole state set together is what proves atomicity, rather than checking the balances alone.

Verification:

* [ ] The settlement service integration test passes against Testcontainers PostgreSQL 18.6.
* [ ] `./mvnw verify` passes.

Engineering log:

* Record the observed journal, postings, balances, trade state, command result and attempt.

Ready for 3.6.7 when:

* the successful settlement produces every required state change

---

## 3.6.7 Prove rollback before commit

* [ ] Inject a test-only technical failure after the balance updates and before the trade transition and finalize.

  * Use a test-only `@Primary` repository decorator, as Phase 2 did.
  * Keep the decorator package-visible and non-final so Spring can proxy it. A `private final` nested class failed in Phase 2.
  * Do not add a failure hook to production code.

* [ ] Let the transaction fail normally.

* [ ] Query PostgreSQL after the service call ends, from outside any test transaction.

Confirm:

```text
no journal
no postings
no settlement attempt
no command_result row
trade still READY with no journal
all four balances unchanged
opening balances unchanged
```

* [ ] Remove the failure and retry the same command.

Confirm:

```text
settles exactly once
```

Why:

* F7 requires a demonstrated failure before commit with no partial settlement, and this is the Phase 3 half of it.
* Failing after the balance updates is the strongest available proof, because the financial writes already happened inside the transaction before the rollback.
* The test must observe the rollback from a new query, not from inside a transaction the test itself controls, or the result would prove nothing.

Verification:

* [ ] The rollback integration test passes.
* [ ] Balance reconstruction still holds after the rolled-back attempt.
* [ ] The retry succeeds exactly once.

Engineering log:

* Record the failure injected, where it was injected, the observed database state and the successful retry.

Ready for 3.7 when:

* settlement commits completely or leaves nothing behind

---

# 3.7 Settlement and financial inspection API

Purpose:

Expose the tested settlement behaviour and the financial history through the Phase 3 REST endpoints.

Phase 3 endpoints:

```text
POST /v1/trades/{id}/settle
GET  /v1/journals/{id}
GET  /v1/trades/{id}/attempts
GET  /v1/commands/{key}
```

---

## 3.7.1 Create `POST /v1/trades/{id}/settle`

* [ ] Add the endpoint to `TradeController`.

* [ ] Require exactly one `Idempotency-Key`, reusing the existing `IdempotencyKey` check.

* [ ] Reject a non-empty request body with `400`.

* [ ] Build a `SettleCommand` from the path id and the key.

* [ ] Call `SettleTradeService`.

* [ ] Return the service's status, stored JSON body and `Location` where present.

* [ ] Keep out of the controller:

  * SQL
  * transaction management
  * locking
  * due-date evaluation
  * balance validation
  * idempotency persistence

Why:

* The controller translates HTTP into an application command. Every settlement decision stays inside the service transaction where it can be recorded.
* Reusing the Phase 2 key validation keeps one C3 implementation in the project.

Verification:

* [ ] A valid settle request settles the trade and returns `201` with the journal `Location`.
* [ ] A replay returns the identical response.
* [ ] A missing, duplicated or malformed key returns `400`.
* [ ] A non-empty body returns `400`.
* [ ] A malformed trade UUID returns `400`.

Engineering log:

* Record the endpoint, request rules and response behaviour.

Ready for 3.7.2 when:

* settlement can be requested over HTTP

---

## 3.7.2 Create `GET /v1/journals/{id}`

* [ ] Add a journal controller.

* [ ] Return the journal with its postings.

```text
id
tradeId
settledAt
postings[]
    id
    accountId
    participant { id, name }
    asset { id, code, type }
    direction
    amount
```

* [ ] Return the postings in the repository's deterministic order.

* [ ] Return `404 UNKNOWN_JOURNAL` for a valid unknown UUID.

* [ ] Return `400` for a malformed UUID.

* [ ] Do not expose any write operation on journals or postings.

Why:

* F5 requires a reviewer to trace the complete financial effect of a trade through the API without inspecting PostgreSQL.
* Including the participant and asset makes the four postings readable as "Alice cash debit 50000" rather than four opaque UUIDs.

Verification:

* [ ] The journal returned by a settlement's `Location` contains exactly four postings.
* [ ] Directions and amounts match the approved convention.
* [ ] Unknown and malformed ids behave as approved.

Engineering log:

* Record the endpoint and the response shape.

Ready for 3.7.3 when:

* a journal and its four postings can be inspected

---

## 3.7.3 Create `GET /v1/trades/{id}/attempts`

* [ ] Add the endpoint to `TradeController`.

* [ ] Return the recorded attempts in decision order.

```text
id
outcome
journalId
businessDate
decidedAt
commandKey
```

* [ ] Return an empty list for a known trade with no attempts.

* [ ] Return `404 UNKNOWN_TRADE` for a valid unknown trade id.

* [ ] Add `journalId` to the trade response from `GET /v1/trades/{id}`.

Why:

* The recorded attempt is the only way to see why a trade is still `READY` after a rejected settlement, because the trade row itself is unchanged.
* Together with the trade's `journalId`, this completes the inspection chain: trade, decision, journal, postings, balances.

Verification:

* [ ] A rejected settlement is visible as an attempt with the expected outcome and business date.
* [ ] A settled trade shows a `SETTLED` attempt linked to its journal.
* [ ] A captured but never-settled trade returns an empty list.
* [ ] An unknown trade returns `404`.
* [ ] `GET /v1/trades/{id}` returns `null` for `journalId` while `READY` and the journal id once `SETTLED`.

Engineering log:

* Record the endpoint, the trade response change and the verification.

Ready for 3.7.4 when:

* settlement decisions can be inspected per trade

---

## 3.7.4 Create `GET /v1/commands/{key}`

* [ ] Add a command controller.

* [ ] Return the durable command outcome.

```text
commandKey
operation
httpStatus
location
response   (the stored body as JSON)
```

* [ ] Embed the stored body as parsed JSON rather than as an escaped string.

* [ ] Return `404 UNKNOWN_COMMAND` for an unknown key.

* [ ] Apply the same key format rules the commands use.

* [ ] Do not expose `request_identity`.

Why:

* F5 lists the command outcome as part of what a reviewer must be able to inspect, and this endpoint is what makes the durable idempotency record visible without opening the database.
* Only completed results can be read, because an unfinished claim exists only inside an uncommitted transaction.
* The stored request identity is an internal comparison value, not part of the public contract.

Verification:

* [ ] A capture key returns its stored `201` outcome.
* [ ] A settlement key returns its stored `201` outcome and journal location.
* [ ] A durable business rejection returns its stored `422` or `409` outcome.
* [ ] An unknown key returns `404`.
* [ ] The response never contains the request identity.

Engineering log:

* Record the endpoint and what it exposes.

Ready for 3.7.5 when:

* durable command outcomes can be inspected

---

## 3.7.5 Extend API error handling for settlement

* [ ] Add handling for the new read failures.

  * `404 UNKNOWN_JOURNAL`
  * `404 UNKNOWN_COMMAND`

* [ ] Keep the existing Phase 2 mappings unchanged.

* [ ] Keep service-produced settlement outcomes exactly as the service recorded them.

  * `409 IDEMPOTENCY_KEY_CONFLICT`
  * `409 ALREADY_SETTLED`
  * `422 NOT_DUE`
  * `422 INSUFFICIENT_CASH`
  * `422 INSUFFICIENT_SECURITIES`

* [ ] Keep the single `{code,message}` error shape.

* [ ] Never expose SQL, constraint names, trigger names, credentials or stack traces.

  * A deferred constraint failure surfaces at commit and must map to a safe `500`.

Why:

* Clients need predictable errors, and durable business outcomes must not be rewritten by request-level error handling.
* Database-level guards produce database-shaped messages, which are exactly the kind of internal detail the specification says must not leak.

Verification:

* [ ] Each new `404` returns the approved shape.
* [ ] Every settlement business outcome is returned as recorded.
* [ ] A forced commit-time constraint failure returns a safe `500`.
* [ ] No error body contains SQL, credentials or a constraint or trigger name.

Engineering log:

* Record the error mapping added and any unexpected framework behaviour.

Ready for 3.7.6 when:

* settlement and inspection errors are predictable and safe

---

## 3.7.6 Verify the HTTP settlement workflow

Using the real Spring Boot application and Testcontainers PostgreSQL:

* [ ] `GET /v1/accounts`.

* [ ] `POST /v1/trades` to capture the Alice/Bob trade.

* [ ] `POST /v1/trades/{id}/settle`.

* [ ] `GET` the returned journal `Location`.

* [ ] `GET /v1/trades/{id}`.

* [ ] `GET /v1/trades/{id}/attempts`.

* [ ] `GET /v1/commands/{settlement key}`.

* [ ] `GET /v1/accounts` again.

* [ ] Retry the same settle request.

Verify:

```text
one journal
four postings
balances moved exactly once
trade SETTLED with journal id
one SETTLED attempt
durable command replay
```

* [ ] Test the approved rejection cases over HTTP.

  * already settled under a new key
  * not due
  * insufficient cash
  * insufficient securities
  * unknown trade
  * reused key against a different trade

* [ ] Confirm each rejection leaves balances, journals and postings untouched.

* [ ] Confirm the generated OpenAPI document picked up the new endpoints automatically.

  * `/v3/api-docs` contains:

```text
POST /v1/trades/{id}/settle
GET  /v1/journals/{id}
GET  /v1/trades/{id}/attempts
GET  /v1/commands/{key}
```

* [ ] Do not add:

  * `@Operation`, `@ApiResponse` or `@Schema` annotations
  * examples or descriptions
  * custom schemas
  * custom OpenAPI YAML
  * API grouping
  * Swagger-specific application code

Why:

* The API is the product, so the guarantees must hold through HTTP and not only at the service layer.
* Driving the whole chain in one test is what proves the inspection workflow in F5 actually connects.
* The springdoc infrastructure already exists and discovers controllers by convention, so the only Phase 3 question is whether the new endpoints appear. Documentation polish belongs to the final project-polish phase.

Verification:

* [ ] The HTTP settlement integration test passes.
* [ ] The four Phase 3 endpoints appear in `/v3/api-docs` with no annotation or configuration work.
* [ ] `./mvnw verify` passes.

Engineering log:

* Record the HTTP workflow, the observed responses and the rejection cases.
* Record that the new endpoints were discovered automatically and that no OpenAPI polish was added.

Ready for 3.8 when:

* the four Phase 3 endpoints work end to end

---

# 3.8 Financial invariant verification

Purpose:

Prove the recorded financial history and the stored balances agree, independently of the code that wrote them.

---

## 3.8.1 Reconstruct balances from opening balance plus committed postings

What is balance reconstruction?

* I7 requires:

```text
current balance = opening balance + all committed postings
```

* [ ] Add a test-support reconstruction check.

Conceptually:

```sql
SELECT account.id
FROM account
LEFT JOIN posting ON posting.account_id = account.id
GROUP BY account.id, account.opening_balance, account.current_balance
HAVING account.current_balance
     <> account.opening_balance + COALESCE(SUM(posting.signed_amount), 0)
```

* The check passes only when the query returns no rows.

* [ ] Keep the check in test support, not in the product API.

  * No endpoint for arbitrary balance work is in scope.
  * Reconciliation-facing balance evidence belongs to Phase 5.

* [ ] Run the check after:

  * a successful settlement
  * each rejection path
  * the rolled-back settlement
  * a replayed settlement

Why:

* Reconstruction is an independent consistency check on the projection described by ADR-008, and it does not replay settlement commands.
* Running it after rejections and rollbacks matters as much as after success, because a partial write would show up as a mismatch rather than as an obvious error.

Verification:

* [ ] Reconstruction holds for the seeded state before any settlement.
* [ ] Reconstruction holds after the Alice/Bob settlement.
* [ ] Reconstruction holds after every rejection and after the rollback test.
* [ ] A deliberately corrupted balance, applied directly in a disposable test, is detected.

Engineering log:

* Record the reconstruction SQL, where it runs and its results.

Ready for 3.8.2 when:

* stored balances provably match committed postings

---

## 3.8.2 Verify conservation and journal shape

* [ ] Assert cash conservation.

  * total AUD across all accounts is unchanged by settlement (I4)

* [ ] Assert security conservation.

  * total EQ1 across all accounts is unchanged by settlement (I5)

* [ ] Assert per-journal shape.

  * exactly four postings
  * two distinct assets
  * per-asset net movement of zero
  * amounts equal the trade's cash amount and quantity
  * accounts belong to the trade's buyer and seller (I6)

* [ ] Assert no negative balances exist (I3).

* [ ] Assert at most one journal per trade (I2).

Why:

* These are the specification's invariants, and Phase 3 is the first phase where they can actually be violated.
* Asserting them from the committed data, rather than from the service's own view, means a future refactor of the service cannot quietly break them.

Verification:

* [ ] Conservation holds after settlement.
* [ ] Journal shape holds for every committed journal.
* [ ] No account is negative.
* [ ] No trade has two journals.

Engineering log:

* Record each invariant test and its result.

Ready for 3.8.3 when:

* the financial invariants are asserted from committed data

---

## 3.8.3 Verify immutable settlement history

* [ ] Attempt, in disposable tests, to:

  * update a posting amount or direction
  * delete a posting
  * update or delete a journal
  * update or delete a settlement attempt
  * revert a settled trade to `READY`
  * change a captured trade term
  * overwrite a completed command result

* [ ] Confirm each attempt fails.

* [ ] Confirm the product exposes no endpoint or repository method that performs any of them.

Why:

* I9 is about what ordinary application operations can do, so the guarantee has to be demonstrated at both layers: no code path exists, and the database refuses anyway.
* This is also what later makes reconciliation evidence trustworthy.

Verification:

* [ ] Every mutation attempt fails.
* [ ] A repository and controller review confirms no mutation path exists.

Engineering log:

* Record the mutation attempts and their rejections.

Ready for 3.9 when:

* settlement history is provably immutable through normal operations

---

# 3.9 Phase 3 verification

Purpose:

Confirm the settlement engine is correct and complete before the concurrency, retry and recovery work begins.

---

## 3.9.1 Run the complete build and test suite

* [ ] Run:

```bash
./mvnw clean verify
```

* [ ] Confirm:

```text
Phase 1 tests                       PASS / FAIL
Phase 2 tests                       PASS / FAIL
Flyway V1 + V2 + V3                 PASS / FAIL
V2 -> V3 upgrade                    PASS / FAIL
Journal schema constraints          PASS / FAIL
Posting schema constraints          PASS / FAIL
Attempt schema constraints          PASS / FAIL
Trade status/journal constraints    PASS / FAIL
Deferred journal shape check        PASS / FAIL
History immutability triggers       PASS / FAIL
Settlement persistence              PASS / FAIL
Trade locking                       PASS / FAIL
Deterministic account locking       PASS / FAIL
Due-date rule                       PASS / FAIL
Insufficient cash                   PASS / FAIL
Insufficient securities             PASS / FAIL
Already settled                     PASS / FAIL
Successful settlement               PASS / FAIL
Durable settlement outcome          PASS / FAIL
Settlement replay                   PASS / FAIL
Settlement rollback                 PASS / FAIL
Journal inspection                  PASS / FAIL
Attempt inspection                  PASS / FAIL
Command inspection                  PASS / FAIL
Balance reconstruction              PASS / FAIL
Conservation invariants             PASS / FAIL
Maven verify                        PASS / FAIL
```

* [ ] Confirm the required Testcontainers tests actually executed.
* [ ] Confirm no required test was skipped.

Engineering log:

* Record the command, test count, failures, errors, skips, PostgreSQL version and final result.

Ready for 3.9.2 when:

* the full suite passes from a clean Maven state

---

## 3.9.2 Run the end-to-end Alice/Bob settlement walkthrough

Use the deterministic demo data.

Starting state:

```text
Alice AUD 100000   Alice EQ1 0
Bob   AUD 0        Bob   EQ1 10
```

* [ ] Start PostgreSQL.

* [ ] Start the application and let Flyway apply V3 to the existing database.

* [ ] Apply the seed to the demo database.

* [ ] `GET /v1/accounts`.

* [ ] `POST /v1/trades` with a due settlement date and `Idempotency-Key: capture-T-001`.

* [ ] `POST /v1/trades/{id}/settle` with `Idempotency-Key: settle-T-001`.

* [ ] `GET` the journal `Location`.

* [ ] `GET /v1/trades/{id}`.

* [ ] `GET /v1/trades/{id}/attempts`.

* [ ] `GET /v1/commands/settle-T-001`.

* [ ] `GET /v1/accounts`.

* [ ] Repeat the settle request.

Expected:

```text
Trade SETTLED with journal id

Alice AUD = 50000
Alice EQ1 = 10
Bob   AUD = 50000
Bob   EQ1 = 0

one journal
four postings
one SETTLED attempt
replay returns the original response
```

* [ ] Document the walkthrough commands in `README.md` after they have been observed to work.

* [ ] Do not record local credentials.

Why:

* This is the first project milestone from the implementation plan and the clearest demonstration that the engine works.
* Running it against a database that already held Phase 2 data also proves the V3 upgrade path outside the test suite.

Verification:

* [ ] The documented commands work as written.
* [ ] Observed balances, trade state, journal and attempt are recorded.
* [ ] Flyway upgraded the existing database to V3 in place.

Engineering log:

* Record the walkthrough and the observed result.

Ready for 3.9.3 when:

* the Alice/Bob settlement works end to end outside the test suite

---

## 3.9.3 Review the repository

* [ ] Inspect `git status`.

* [ ] Inspect new and modified files.

* [ ] Confirm:

  * V1 and V2 were not edited
  * V3 contains only settlement structures and the two approved extensions
  * no reconciliation schema exists
  * no concurrency, restart or load tooling was added
  * no absolute balance-setting repository method or endpoint exists
  * no journal or posting mutation path exists
  * no Java in-process locking was used for financial state
  * no generated build output is tracked
  * `.env` remains untracked and no secret was committed
  * no JPA, Hibernate ORM or Kafka was introduced
  * no speculative Phase 4 or Phase 5 classes were added

Ready for 3.9.4 when:

* the repository contains only approved Phase 3 work

---

## 3.9.4 Review the engineering log

* [ ] Confirm the log contains Phase 3 work using the same numbering:

  * 3.1 baseline and settlement contract
  * 3.2 V3 schema
  * 3.3 settlement domain types
  * 3.4 settlement persistence
  * 3.5 transaction, locking and validation
  * 3.6 settlement writes and durable outcome
  * 3.7 settlement and inspection API
  * 3.8 financial invariants

* [ ] Confirm each meaningful entry records:

  * what happened
  * why
  * files involved
  * SQL and configuration
  * commands and tests
  * result
  * failures
  * fixes
  * deviations and why

* [ ] Keep failed attempts in the history.

* [ ] Append the Phase 3 final verification result.

Ready for 3.9.5 when:

* the log accurately describes how settlement was built

---

## 3.9.5 Review the final repository structure

Expected new Phase 3 shape is approximately:

```text
src/main/java/com/jasonwidjaja/dvp/
├── api/
│   ├── TradeController.java            (settle + attempts added)
│   ├── JournalController.java
│   ├── CommandController.java
│   ├── SettlementResponse.java
│   ├── JournalResponse.java
│   ├── SettlementAttemptResponse.java
│   ├── CommandResultResponse.java
│   └── ApiExceptionHandler.java        (new 404s)
├── application/
│   ├── CaptureTradeService.java
│   ├── SettleTradeService.java
│   ├── BusinessCalendar.java
│   └── ClockConfiguration.java
├── domain/
│   ├── TradeStatus.java               (SETTLED added)
│   ├── Trade.java                     (journalId added)
│   ├── SettlementJournal.java
│   ├── Posting.java
│   ├── PostingDirection.java
│   ├── SettlementAttempt.java
│   ├── SettlementOutcome.java
│   ├── SettleCommand.java
│   └── SettleRequestIdentity.java
└── persistence/
    ├── TradeRepository.java           (lockById, markSettled)
    ├── AccountRepository.java         (resolve, lockBalance, applyDelta)
    ├── CommandResultRepository.java   (operation parameter)
    ├── SettlementJournalRepository.java
    └── SettlementAttemptRepository.java

src/main/resources/db/migration/
├── V1__participants_assets_accounts.sql
├── V2__trades_and_command_results.sql
└── V3__settlement_journal_postings_attempts.sql
```

The exact structure may differ where technically justified.

Any meaningful deviation should be recorded in the engineering log.

Ready for 3.9.6 when:

* the actual structure is reviewed and deviations are recorded

---

## 3.9.6 Confirm Phase 3 exit criteria

Phase 3 is complete only when:

* [ ] Phase 1 and Phase 2 still pass.
* [ ] C2 and the Phase 3 settlement contract were approved before implementation.
* [ ] V3 applies to a clean database.
* [ ] A V2 database upgrades to V3 without changing existing rows or balances.
* [ ] The Alice/Bob settlement succeeds end to end.
* [ ] Exactly one settlement journal is created.
* [ ] Exactly four correct postings are created.
* [ ] Four account balances are correct after settlement.
* [ ] The trade becomes `SETTLED` and points at its journal.
* [ ] The settlement command outcome is durable and replays identically.
* [ ] A settlement attempt is recorded for every committed decision, and none for a replay.
* [ ] Insufficient cash causes no financial movement and leaves the trade `READY`.
* [ ] Insufficient securities causes no financial movement and leaves the trade `READY`.
* [ ] A trade that is not due causes no financial movement.
* [ ] A second settlement of the same trade cannot create a second journal.
* [ ] The settlement path locks the trade, then the four accounts in ascending account-ID order.
* [ ] Balances are validated only after the locks are held.
* [ ] A technical failure before commit leaves no partial financial state.
* [ ] Balance reconstruction matches stored balances.
* [ ] Cash and securities are conserved.
* [ ] No account can become negative.
* [ ] Journals, postings and attempts cannot be modified through normal operations.
* [ ] Journal, attempt and command inspection work through the API.
* [ ] PostgreSQL integration tests pass.
* [ ] `./mvnw clean verify` passes.
* [ ] No secrets or generated build output are committed.
* [ ] The engineering log accurately describes Phase 3.

Then stop.

Do not implement the concurrency, retry-race or recovery demonstrations.

The next project action is:

```text
inspect completed Phase 3 repository
        |
        v
prepare docs/detailed-plan/phase-4.md
        |
        v
review Phase 4
        |
        v
implement Phase 4
```
