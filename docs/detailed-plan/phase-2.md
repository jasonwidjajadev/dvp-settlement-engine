# Phase 2: Trade Capture and Read-Back

## Goal

Implement Trade Capture on top of the completed Phase 1 foundation.

Phase 2 establishes:

```text
Phase 1 foundation
    |
    v
Trade domain model
    |
    v
V2 trade + command-result schema
    |
    v
Spring JDBC persistence
    |
    v
Atomic capture transaction
    |
    v
REST capture + read endpoints
    |
    v
PostgreSQL integration tests
```

At the end of this phase:

* a valid matched trade can be captured as `READY`
* captured trade terms are immutable
* invalid trades are rejected
* duplicate business identity cannot create a second trade
* repeated requests using the same idempotency key are safe
* conflicting reuse of an idempotency key is rejected
* captured trades can be read back
* account balances can be inspected
* Trade Capture does not change account balances
* capture and its durable command outcome commit or roll back together
* automated tests run against PostgreSQL
* `./mvnw verify` passes

Phase 2 does not implement:

* settlement
* account locking
* cash or security movement
* journals
* postings
* settlement attempts
* settlement concurrency demonstrations
* response-loss/restart recovery demonstrations
* reconciliation
* load testing
* `GET /v1/commands/{key}`
* frontend
* Kafka
* transactional outbox

Do not begin Phase 3 automatically.


* 2.1: Decide the exact Trade Capture rules before writing code, including valid trade values, duplicate-trade behaviour, idempotency, and durable error/result behaviour.
* 2.2: Create the Java domain objects and API request/response types that represent a captured trade and validate incoming data.
* 2.3: Add the PostgreSQL schema for storing trades and durable idempotency command results using Flyway V2.
* 2.4: Add the Spring JDBC repositories that let Java read participants/assets and store/read trades and command results.
* 2.5: Combine those repositories into one atomic Trade Capture operation so the trade and its durable result commit or roll back together.
* 2.6: Expose Trade Capture and read-back through REST endpoints: create trade, get trade, and inspect accounts.
* 2.7: Verify the whole Phase 2 flow end to end, confirm retries are safe and balances stay unchanged, then stop before settlement.

---

# 2.1 Confirm Phase 1 and approve the Trade Capture contract

Purpose:

Start from the completed Phase 1 repository and resolve the remaining public/domain decisions before they become database constraints or API behaviour.

---

## 2.1.1 Inspect the completed Phase 1 repository

What is the baseline?

* Phase 1 already established:

  * Java 21
  * Maven Wrapper
  * Spring Boot
  * PostgreSQL 18.6
  * Flyway V1
  * participant, asset and account tables
  * deterministic Alice/Bob demo data
  * Spring JDBC account reads
  * Testcontainers PostgreSQL integration tests

* Phase 2 must extend that foundation rather than rebuild it.

* [ ] Inspect the repository tree.

* [ ] Run `git status`.

  * Preserve existing work.
  * Confirm no Trade Capture implementation already exists.
  * Confirm no V2 migration already exists.

* [ ] Read:

  * `docs/project-spec.md`
  * `docs/decisions.md`
  * `docs/implementation-plan.md`
  * `docs/detailed-plan/phase-1.md`
  * `docs/engineering-log.md`

* [ ] Inspect the existing Phase 1 production code.

  * `DvpApplication`
  * domain records
  * `AccountRepository`
  * `application.yml`
  * Flyway V1
  * seed script

* [ ] Inspect the existing PostgreSQL test support.

  * shared PostgreSQL 18.6 Testcontainer
  * Flyway setup
  * per-test application-table cleanup
  * deterministic seed helper

Why:

* Phase 2 depends on the exact repository that Phase 1 produced.
* Existing UUIDs, account structures and test support should be reused instead of duplicated.
* The detailed plan must not assume files or packages that do not exist.

Verification:

* [ ] Run:

```bash
./mvnw verify
```

* [ ] Confirm all existing Phase 1 tests pass before writing Phase 2 code.
* [ ] Record the actual test count and result.

Engineering log:

* Record the Phase 2 starting repository state.
* Record the baseline verification command and result.
* Record any unexpected difference from the approved documents before proceeding.

Ready for 2.1.2 when:

* the Phase 1 baseline is understood
* existing tests pass
* no existing work will be overwritten

---

## 2.1.2 Approve the trade terms and business identity

What is Trade Capture?

* Trade Capture records a trade that has already been agreed outside this system.
* It does not match buyers and sellers.
* It does not move cash or securities.
* It records the immutable economic terms that settlement will use later.

The Phase 2 trade contains:

```text
external trade reference
buyer
seller
security
quantity
cash amount
settlement date
```

* [ ] Reuse the Phase 1 UUID identifiers for:

  * buyer
  * seller
  * security asset

* [ ] Require buyer and seller to:

  * exist
  * be different participants

* [ ] Require the security to:

  * exist
  * have asset type `SECURITY`

* [ ] Continue using:

  * AUD integer minor units for cash
  * whole integer units for securities
  * Java `long`
  * PostgreSQL `BIGINT`

* [ ] Resolve C1 for captured trade values.

Proposed Phase 2 rule:

```text
quantity > 0
cashAmount > 0
both must fit Java long / PostgreSQL BIGINT
```

Zero-value trades are rejected.

* [ ] Define the external trade reference.

Proposed rule:

```text
non-blank
maximum 128 characters
case-sensitive
stored exactly as supplied
```

Do not trim or case-fold a valid reference after acceptance.

What is business trade identity?

* Business identity answers:

> Has this identified upstream trade already been captured?

For Phase 2, the proposed business identity is:

```text
external trade reference
```

* [ ] Resolve C5.

Proposed rule:

* same reference + identical immutable terms

  * return the existing trade
  * do not create another trade

* same reference + different immutable terms

  * reject the request as a conflict
  * never overwrite the original trade

* [ ] Accept settlement date as a calendar date.

  * Capture does not decide whether the trade is due.
  * Settlement-date eligibility belongs to Phase 3.

Why:

* Trade terms become immutable once accepted.
* Command identity and business trade identity solve different duplicate-request problems.
* These rules affect the API, schema, persistence and tests and therefore must be approved before implementation.

Verification:

* [ ] Human approval resolves:

  * positive quantity rule
  * positive cash rule
  * external-reference rule
  * repeated-trade behaviour

* [ ] Any requested change is reflected in later Phase 2 steps before implementation.

Engineering log:

* Record the approved C1 and C5 decisions.
* Record any change from the proposals above.

Ready for 2.1.3 when:

* the immutable trade terms are clear
* business identity behaviour is approved

---

## 2.1.3 Approve idempotency and durable command outcomes

What is an idempotency key?

* An idempotency key identifies one client command.
* It protects the client when the same request is sent more than once.

Example:

```text
POST trade
    |
    v
trade commits
    |
response is lost
    |
client retries same command
    |
same saved result is returned
```

This is different from the external trade reference:

```text
Idempotency key
→ identifies one request/command

External trade reference
→ identifies the business trade
```

* [ ] Resolve C3.

Proposed transport:

```text
Idempotency-Key: capture-T-001
```

Proposed rule:

* exactly one header
* non-blank
* maximum 128 characters
* case-sensitive
* exact value is preserved
* one global command-key namespace for this project

* [ ] Define request identity for Trade Capture.

A capture request is considered the same request only when the stored operation and all parsed trade terms are equal:

```text
operation = CAPTURE_TRADE
external trade reference
buyer
seller
security
quantity
cash amount
settlement date
```

JSON whitespace or property order does not create a different request.

* [ ] Resolve C4.

Proposed durable-result policy:

| Situation | Result | Durable command outcome |
| --- | --- | --- |
| New valid trade | `201 Created` | yes |
| Same key + same request | replay original result | already stored |
| Same key + changed request | `409 Conflict` | original result remains unchanged |
| New key + same trade reference + same terms | `200 OK` existing trade | yes |
| New key + same reference + different terms | `409 Conflict` | yes |
| Unknown participant/security or self-trade | `422 Unprocessable Content` | yes |
| Malformed JSON, invalid UUID/date/type/range or missing/invalid key | `400 Bad Request` | no |
| Unsupported media type | `415 Unsupported Media Type` | no |
| Unexpected technical failure before commit | `500 Internal Server Error` | no committed capture result |
| Unknown trade on GET | `404 Not Found` | read only |

* [ ] Approve the distinction between:

```text
invalid request shape
→ rejected before command persistence

validly formed command with business rejection
→ durable command outcome
```

* [ ] Use a small API error body:

```json
{
  "code": "UNKNOWN_PARTICIPANT",
  "message": "Buyer does not exist"
}
```

Why:

* Idempotency must survive process memory and application restarts.
* Saving business outcomes makes retries stable even if reference data changes later.
* Malformed input should not claim an idempotency key before the application has a valid command to identify.

Verification:

* [ ] Human approval resolves C3 and C4.
* [ ] HTTP statuses and durable-result behaviour are understood before schema/API implementation.
* [ ] No later step invents a conflicting retry policy.

Engineering log:

* Record the approved idempotency-key rule.
* Record the approved durable-result policy.
* Record the approved HTTP outcomes.

Ready for 2.2 when:

* Phase 1 baseline passes
* C1, C3, C4 and C5 are resolved
* the Phase 2 plan is approved

---

# 2.2 Trade domain and API types

Purpose:

Create the Java types used to represent accepted trade terms, capture commands and HTTP requests/responses.

---

## 2.2.1 Create the Trade domain records

What is a Trade domain object?

* A Trade represents the already-matched business trade stored by this system.
* Its economic terms are immutable after capture.

* [ ] Create `TradeTerms`.

It should represent:

```text
externalTradeId
buyerId
sellerId
securityId
quantity
cashAmount
settlementDate
```

* [ ] Create `TradeStatus`.

Phase 2 only requires:

```text
READY
```

`SETTLED` belongs to Phase 3.

* [ ] Create `Trade`.

It should contain:

```text
id
TradeTerms
status
```

* [ ] Use:

  * `UUID` for identifiers
  * `long` for accepted quantity/cash values
  * `LocalDate` for settlement date

* [ ] Keep the types immutable.

Do not add:

* setters
* settlement methods
* journal fields
* balance mutation
* locking behaviour

Why:

* Captured terms should represent the accepted business fact, not mutable application state.
* Keeping the domain model small prevents Phase 2 from implementing settlement prematurely.

Verification:

* [ ] Domain classes compile.
* [ ] Every accepted economic term is represented.
* [ ] No Phase 3 behaviour exists.

Engineering log:

* Record each new domain type.
* Record what it represents and its important fields.

### 2.1.2 Approve the trade terms and business identity

- Inspected C1 and C5 against the current repository and documents.
- First presented the Phase 2 proposals for human review. They were not encoded in Java or migrations at that point.
- Human approval recorded the following final rules.

C1 — captured trade values:

- `quantity > 0`
- `cashAmount > 0`
- Zero-value trades are rejected.
- Both values must fit Java `long` / PostgreSQL `BIGINT`.
- AUD remains integer minor units. Securities remain whole units. This continues the Phase 1 numeric representation.
- `externalTradeId` is 1–128 characters, non-blank, and case-sensitive.
- Leading or trailing whitespace is rejected. The value is not silently trimmed.
- The accepted reference is stored exactly as supplied.

C5 — repeated trade identity:

- A new command key with the same external trade reference and identical immutable terms returns the existing trade. A second trade row is not created.
- That path returns `200 OK`.
- The same reference with different immutable terms returns `409 Conflict` and never overwrites the original trade.

- Change from the original Phase 2 C1 proposal: whitespace on `externalTradeId` is an error, not a silent trim.

### 2.1.3 Approve idempotency and durable command outcomes

- Inspected C3 and C4 against the spec, ADR-009, and `docs/detailed-plan/phase-2.md`.
- First presented the Phase 2 proposals for human review. They were not encoded in Java or migrations at that point.
- Human approval recorded the following final rules.

C3 — idempotency-key scope:

- Exactly one `Idempotency-Key` HTTP header.
- The key is 1–128 characters, non-blank, and case-sensitive.
- Leading or trailing whitespace is rejected. The key is not silently trimmed.
- One global command-key namespace for this project.
- Request identity is operation `CAPTURE_TRADE` plus all parsed trade terms: external trade reference, buyer, seller, security, quantity, cash amount, settlement date.
- JSON whitespace and property order do not change request identity.

C4 — durable command-result policy:

| Situation | Result | Durable |
| --- | --- | --- |
| New valid capture | `201 Created` | yes |
| Same key + same request | replay original outcome | already stored |
| Same key + changed request | `409 Conflict` | original unchanged |
| New key + same trade + same terms | `200 OK` | yes |
| New key + same reference + different terms | `409 Conflict` | yes |
| Business validation failure (unknown participant/security or self-trade) | `422 Unprocessable Content` | yes |
| Malformed or structurally invalid request, or invalid key | `400 Bad Request` | no |
| Unsupported media type | `415 Unsupported Media Type` | no |
| Unexpected technical failure before commit | `500 Internal Server Error` | no committed capture result |
| Unknown trade on GET | `404 Not Found` | read only |

- Error bodies use `code` and `message`.
- Change from the original Phase 2 C3 proposal: whitespace on `Idempotency-Key` is an error, not a silent trim.

- Section 2.1 is complete.
- C1, C3, C4 and C5 are approved.
- No application code or Flyway migration was added.
- Stopped here. Section 2.2 was not started.


---

## 2.2.2 Create `CaptureCommand`

What is a command?

* A command represents one requested application action.
* For Phase 2 the operation is Trade Capture.

The command combines:

```text
idempotency key
        +
trade terms
```

* [ ] Create `CaptureCommand`.

* [ ] Keep the command key separate from `TradeTerms`.

Why:

* Two different command keys can refer to the same business trade.
* The command identity and trade identity must remain independent.

Verification:

* [ ] Command compiles.
* [ ] The idempotency key is not treated as part of the trade's economic identity.

Engineering log:

* Record the command type and its relationship to `TradeTerms`.

---

## 2.2.3 Create the HTTP DTOs

What is a DTO?

* DTO means Data Transfer Object.
* It describes the JSON accepted or returned by the REST API.
* DTOs keep HTTP representation separate from database and domain implementation details.

* [ ] Create a capture request DTO.

Approved Phase 2 shape:

```json
{
  "externalTradeId": "T-001",
  "buyerId": "00000000-0000-0000-0000-000000000001",
  "sellerId": "00000000-0000-0000-0000-000000000002",
  "securityId": "00000000-0000-0000-0000-0000000000e1",
  "quantity": 10,
  "cashAmount": 50000,
  "settlementDate": "2026-09-20"
}
```

* [ ] Create a trade response DTO.

It should expose:

```text
id
externalTradeId
buyerId
sellerId
securityId
quantity
cashAmount
settlementDate
status
```

* [ ] Create an account response DTO using the existing Phase 1 account model.

It should preserve:

```text
account ID
participant ID + name
asset ID + code + type
opening balance
current balance
```

* [ ] Create a small error response DTO.

Why:

* The HTTP contract should be explicit rather than exposing Java persistence objects directly.
* Phase 3 can evolve internal settlement structures without silently changing Phase 2 JSON.

Verification:

* [ ] DTOs compile.
* [ ] JSON field names match the approved Phase 2 contract.

Engineering log:

* Record each DTO and the endpoint that uses it.

---

## 2.2.4 Configure request validation

* [ ] Validate required request fields.

* [ ] Validate:

  * non-blank external reference
  * approved maximum reference length
  * buyer UUID
  * seller UUID
  * security UUID
  * positive quantity
  * positive cash amount
  * valid settlement date

* [ ] Use nullable request-number types where needed so:

```text
missing
!=
zero
```

* [ ] Reject malformed JSON and invalid field types.

* [ ] Reject fractional or string values where integer values are required.

* [ ] Keep reference-data validation out of the DTO.

For example:

```text
UUID parses correctly
→ request validation

buyer actually exists
→ application service
```

Why:

* HTTP structure should be rejected before a command key is claimed.
* Business validation must occur inside the capture flow so its result can be durably recorded.

Verification:

* [ ] Add focused request-validation tests.
* [ ] Verify valid request decoding.
* [ ] Verify missing/null/invalid values fail as expected.

Engineering log:

* Record the validation rules and tests added.

Ready for 2.3 when:

* the domain model exists
* the capture command exists
* the API representations and structural validation are defined

---

# 2.3 Flyway V2 Trade Capture schema

Purpose:

Add the durable PostgreSQL structures required for trades and idempotent capture outcomes.

Do not modify V1.

---

## 2.3.1 Create Flyway V2

What is V2?

* V1 established participants, assets and accounts.
* V2 adds the database structures needed by Trade Capture.

Create:

```text
src/main/resources/db/migration/
└── V2__trades_and_command_results.sql
```

* [ ] Create V2.
* [ ] Keep V1 unchanged.

Why:

* Existing databases should upgrade from V1 to V2.
* Flyway migrations are append-only history once applied.

Engineering log:

* Record:

  * migration filename
  * purpose of V2
  * confirmation that V1 was not edited

---

## 2.3.2 Create the trade table

* [ ] Create `trade`.

Store:

```text
id
external_trade_id
buyer_id
seller_id
security_id
quantity
cash_amount
settlement_date
status
```

* [ ] Use UUID primary/foreign keys consistent with Phase 1.

* [ ] Add constraints for:

  * unique external trade reference
  * buyer exists
  * seller exists
  * security asset exists
  * buyer != seller
  * positive quantity
  * positive cash amount
  * valid `READY` status for Phase 2

* [ ] Keep captured terms immutable through normal application behaviour.

* [ ] Do not add:

  * journal
  * journal ID
  * postings
  * settlement attempt
  * `SETTLED` transition

Why:

* PostgreSQL should provide a second line of defence behind Java validation.
* Unique business identity prevents another command key from creating a second copy of the same trade.

Verification:

* [ ] Valid trade row can be inserted.
* [ ] duplicate external reference fails.
* [ ] unknown buyer/seller/security fails.
* [ ] self-trade fails.
* [ ] zero/negative quantity fails.
* [ ] zero/negative cash amount fails.
* [ ] unsupported status fails.

Engineering log:

* Record the table, columns, relationships and constraints.

---

## 2.3.3 Create the command-result table

What is a command result?

* A command result is the durable record associated with an idempotency key.

It needs to remember:

```text
which command was requested
what request identity it had
what result was returned
```

* [ ] Create `command_result`.

Store:

```text
command_key
operation
request_identity
http_status
response_body
location
```

* [ ] Use `CAPTURE_TRADE` as the Phase 2 operation identity.

* [ ] Store the parsed trade request identity.

  * Do not use raw incoming JSON bytes.
  * Property order or whitespace should not change command identity.

* [ ] Allow the response fields to be temporarily empty only while a new command is being completed inside the same transaction.

* [ ] Ensure a completed result cannot be silently overwritten.

* [ ] Do not require every command result to reference a trade.

  * business rejection can exist without a trade

Why:

* Idempotency must be durable.
* The command key alone is not enough because the same key with a different request must be rejected.
* A business rejection must be replayable even when no trade was created.

Verification:

* [ ] duplicate command key is rejected/claimed safely.
* [ ] valid completed result can be stored.
* [ ] invalid partial result is rejected.
* [ ] rejection can exist without a trade.

Engineering log:

* Record the table and why each field exists.

---

## 2.3.4 Verify V2 and preserve Phase 1 state

* [ ] Update the PostgreSQL startup integration test.

Confirm:

```text
V1 applied
V2 applied
```

and tables exist:

```text
participant
asset
account
trade
command_result
flyway_schema_history
```

* [ ] Extend test cleanup so the new application tables cannot leak data between tests.

* [ ] Add database constraint tests for V2.

* [ ] Test a V1 -> V2 upgrade.

  * start with V1
  * load Phase 1 seed data
  * apply V2
  * confirm participants/assets/accounts are unchanged
  * confirm trade and command-result tables start empty

Why:

* A migration must work both on a clean database and on the completed Phase 1 database.
* Phase 2 must not reset balances or replace existing reference data.

Verification:

* [ ] V1 + V2 apply to a clean PostgreSQL database.
* [ ] V1 database upgrades to V2.
* [ ] Phase 1 seed IDs and balances remain unchanged.
* [ ] V2 constraints reject invalid state.
* [ ] existing Phase 1 tests still pass.

Engineering log:

* Record migration tests, failures and corrections.

Ready for 2.4 when:

* V2 is reproducible
* existing Phase 1 data survives the upgrade
* database constraints work

---

# 2.4 Spring JDBC Trade Capture persistence

Purpose:

Create the explicit SQL operations needed by the capture service.

Repositories provide SQL operations.

They do not own the overall Trade Capture transaction.

---

## 2.4.1 Create participant and asset lookup repositories

Phase 1 can already read accounts, but Trade Capture also needs to validate buyer, seller and security directly.

* [ ] Create `ParticipantRepository`.

Required operation:

```text
existsById(UUID)
```

* [ ] Create `AssetRepository`.

Required operation:

```text
findById(UUID)
```

* [ ] Reuse the existing `Participant`, `Asset` and `AssetType` domain records.

* [ ] Use:

  * `NamedParameterJdbcTemplate`
  * parameterized SQL
  * explicit selected columns

Why:

* A foreign-key failure cannot produce the intended business validation result.
* The application must distinguish:

```text
participant missing
security missing
asset exists but is CASH rather than SECURITY
```

Verification:

* [ ] Known participant lookup succeeds.
* [ ] unknown participant lookup fails cleanly.
* [ ] AUD reads as `CASH`.
* [ ] EQ1 reads as `SECURITY`.

Engineering log:

* Record repositories and SQL introduced.

---

## 2.4.2 Create `TradeRepository`

What does `TradeRepository` do?

* It stores and reads captured trades using explicit SQL.

Required operations:

```text
findById(UUID)
findByExternalTradeId(String)
insertIfAbsent(...)
```

* [ ] Create `TradeRepository`.

* [ ] Implement reading by internal ID.

* [ ] Implement reading by external trade reference.

* [ ] Implement insert-if-absent using the database uniqueness constraint.

Conceptually:

```sql
INSERT ...
ON CONFLICT (external_trade_id)
DO NOTHING
RETURNING ...
```

* [ ] Generate a UUID for a new trade.

* [ ] Never use an upsert that changes an already accepted trade.

Why:

* Two commands can race to capture the same business trade.
* PostgreSQL uniqueness is the final authority on whether a new trade row can be created.
* Existing accepted terms must not be overwritten.

Verification:

* [ ] Trade can be inserted and read back.
* [ ] every term round-trips correctly.
* [ ] unknown ID returns no trade.
* [ ] duplicate external reference does not overwrite the original trade.

Engineering log:

* Record SQL and duplicate-reference behaviour.

---

## 2.4.3 Create `CommandResultRepository`

Required operations:

```text
claim command key
read command result
finalize command result
```

* [ ] Create `CommandResultRepository`.

* [ ] Claim a key using PostgreSQL uniqueness.

Conceptually:

```sql
INSERT ...
ON CONFLICT (command_key)
DO NOTHING
```

* [ ] If the key already exists:

  * read its stored request identity
  * compare it with the incoming command
  * replay only when it represents the same request

* [ ] Finalize only the unfinished row owned by the current capture.

* [ ] Never overwrite a completed command outcome.

Why:

* The database decides which request owns a command key.
* Process-local maps are not durable and disappear on restart.

Verification:

* [ ] new key can be claimed.
* [ ] duplicate key cannot create a second command row.
* [ ] stored request identity can be read.
* [ ] completed result can be read after commit.
* [ ] completed result cannot be replaced.

Engineering log:

* Record command-result SQL and identity comparison behaviour.

---

## 2.4.4 Verify the persistence layer

* [ ] Add PostgreSQL integration tests for:

  * participant reads
  * asset reads
  * trade insert/read
  * duplicate trade reference
  * command claim
  * command finalization
  * command replay data

* [ ] Use the existing Testcontainers PostgreSQL 18.6 support.

* [ ] Keep every test independent using the shared cleanup mechanism.

Ready for 2.5 when:

* reference data can be validated
* trades can be inserted/read safely
* command keys and outcomes can be stored/read safely

---

# 2.5 Atomic Trade Capture service

Purpose:

Coordinate validation, business identity and idempotency inside one PostgreSQL transaction.

This is the first Phase 2 business operation.

---

## 2.5.1 Create the service-owned transaction boundary

What is the capture transaction?

A new valid Trade Capture needs:

```text
command key claim
        +
trade decision
        +
durable result
```

to behave as one committed unit.

If a technical failure happens before commit:

```text
nothing from that capture remains
```

* [ ] Create `CaptureTradeService`.

* [ ] Give the service ownership of the Trade Capture transaction.

* [ ] Use one Spring JDBC transaction manager for the same datasource used by the repositories.

* [ ] Use `TransactionTemplate` for the Phase 2 capture path.

Conceptually:

```text
CaptureTradeService
        |
        v
TransactionTemplate
        |
        +--> CommandResultRepository
        +--> ParticipantRepository
        +--> AssetRepository
        +--> TradeRepository
        |
        v
COMMIT / ROLLBACK
```

`TransactionTemplate` is a Phase 2 implementation choice that makes the transaction boundary explicit. It does not change the project architecture.

* [ ] Keep the controller outside this transaction.

* [ ] Return the capture outcome only after the transaction completes successfully.

Why:

* A successful HTTP response must not be produced before the database commit is known to have succeeded.
* Trade and durable command outcome must not commit independently.

Verification:

* [ ] Service uses the expected datasource/transaction manager.
* [ ] Repository operations participate in the service transaction.
* [ ] technical exception rolls the transaction back.

Engineering log:

* Record the transaction mechanism and why the service owns it.

---

## 2.5.2 Implement the capture sequence

Inside the service transaction:

```text
validated capture command
        |
        v
claim idempotency key
        |
        +--> existing same request
        |       |
        |       v
        |   replay saved result
        |
        +--> existing different request
        |       |
        |       v
        |     conflict
        |
        v
validate business references
        |
        v
insert or resolve trade reference
        |
        v
save command outcome
        |
        v
commit
```

* [ ] Claim the command key.

* [ ] If the key already exists:

  * same request identity

    * return the saved outcome

  * different request identity

    * reject with the approved idempotency conflict
    * do not overwrite the stored result

* [ ] For a new key, validate:

  * buyer != seller
  * buyer exists
  * seller exists
  * security exists
  * asset type is `SECURITY`

* [ ] If business validation fails:

  * do not create a trade
  * save the approved durable rejection
  * commit the command outcome

* [ ] Attempt to insert the trade.

* [ ] If the external reference already exists:

  * same immutable terms

    * return the existing trade using the approved outcome

  * different immutable terms

    * save and return a conflict
    * keep original trade unchanged

* [ ] Serialize and finalize the command result before the transaction ends.

* [ ] Never:

  * check account sufficiency
  * reserve balances
  * update balances
  * lock accounts for settlement
  * create journals
  * change trade to `SETTLED`

Why:

* Capture records an agreed trade.
* Settlement decides later whether resources are available and whether the trade is due.

Engineering log:

* Record the implemented capture order and any important failure/fix.

---

## 2.5.3 Verify Trade Capture behaviour

Using real PostgreSQL:

* [ ] Capture a valid Alice/Bob/EQ1 trade.

Expected:

```text
one trade
status = READY
one completed command result
accounts unchanged
```

* [ ] Retry using the same key and same request.

Expected:

```text
same saved outcome
no second trade
no second command row
```

* [ ] Reuse the same key with changed terms.

Expected:

```text
conflict
original command result unchanged
original trade unchanged
```

* [ ] Use a new key with the same trade reference and identical terms.

Expected:

```text
existing trade returned
one trade total
new command outcome recorded
```

* [ ] Use a new key with the same reference and different terms.

Expected:

```text
conflict
original trade unchanged
```

* [ ] Test:

  * unknown buyer
  * unknown seller
  * unknown security
  * cash asset used as security
  * self-trade

* [ ] Confirm approved business rejections are durable.

* [ ] Confirm all four Phase 1 account balances remain unchanged.

Ready for 2.5.4 when:

* normal capture
* replay
* conflict
* business rejection

all behave correctly.

---

## 2.5.4 Prove capture rollback

* [ ] Inject a test-only technical failure after trade insertion but before command-result finalization.

* [ ] Allow the service transaction to fail normally.

* [ ] Query PostgreSQL afterward.

Confirm:

```text
trade not committed
unfinished command claim not committed
accounts unchanged
```

* [ ] Remove the failure.

* [ ] Retry the same capture.

Confirm:

```text
capture succeeds once
```

Why:

* Trade Capture already needs atomic persistence even though financial settlement has not started yet.
* A trade must not be committed without the command result required to make retry safe.

Verification:

* [ ] Rollback is observed from a new database query after the service call ends.
* [ ] The test itself does not wrap the service in an outer rollback transaction.

Engineering log:

* Record:

  * failure injected
  * expected rollback
  * observed database state
  * successful retry afterward

Ready for 2.6 when:

* capture transaction commits and rolls back correctly
* idempotency behaviour is durable
* balances remain unchanged

---

# 2.6 Trade Capture REST API

Purpose:

Expose the tested Trade Capture behaviour through the Phase 2 REST endpoints.

Phase 2 endpoints:

```text
POST /v1/trades
GET  /v1/trades/{id}
GET  /v1/accounts
```

---

## 2.6.1 Create `POST /v1/trades`

* [ ] Create `TradeController`.

* [ ] Add:

```text
POST /v1/trades
```

* [ ] Require:

```text
Content-Type: application/json
Idempotency-Key: ...
```

* [ ] Validate the request DTO.

* [ ] Convert the request into:

```text
TradeTerms
        +
CaptureCommand
```

* [ ] Call `CaptureTradeService`.

* [ ] Return the service's approved:

  * HTTP status
  * JSON response body
  * `Location` header where present

* [ ] Keep out of the controller:

  * SQL
  * transaction management
  * participant/security business validation
  * idempotency persistence

Why:

* The controller translates HTTP into application commands.
* Business decisions remain in the application service.

Verification:

* [ ] Valid HTTP capture reaches PostgreSQL.
* [ ] response contains the stored trade.
* [ ] `READY` is returned.
* [ ] `Location` points to the created/existing trade where appropriate.

Engineering log:

* Record endpoint, request shape and response behaviour.

---

## 2.6.2 Create `GET /v1/trades/{id}`

* [ ] Add:

```text
GET /v1/trades/{id}
```

* [ ] Read the trade through `TradeRepository`.

* [ ] Map it to the trade response DTO.

* [ ] Return:

  * trade when found
  * `404` for a valid unknown UUID
  * `400` for malformed UUID input

Why:

* Capture should be independently observable after the original POST completes.
* The endpoint reads current stored trade state, not the historical command response.

Verification:

* [ ] Capture a trade.
* [ ] Follow its `Location`.
* [ ] Compare every stored term.
* [ ] Verify unknown/malformed IDs.

Engineering log:

* Record endpoint and read-back verification.

---

## 2.6.3 Create `GET /v1/accounts`

* [ ] Create `AccountController`.

* [ ] Add:

```text
GET /v1/accounts
```

* [ ] Reuse:

```text
AccountRepository.findAll()
```

* [ ] Return account DTOs containing:

  * participant
  * asset
  * opening balance
  * current balance

* [ ] Do not:

  * seed data automatically
  * create accounts
  * update balances

Why:

* A reviewer should be able to prove that Trade Capture does not move money or securities.

Verification:

* [ ] GET accounts before capture.
* [ ] capture a trade.
* [ ] GET accounts again.
* [ ] confirm every opening/current balance is unchanged.

Engineering log:

* Record endpoint and unchanged-balance verification.

---

## 2.6.4 Add API error handling

* [ ] Add one MVC exception handler/controller advice for request-level errors.

* [ ] Return the approved error shape.

* [ ] Handle:

  * malformed JSON
  * invalid UUID/date/value
  * missing/invalid idempotency key
  * unsupported media type
  * unknown trade
  * unexpected internal failure

* [ ] Keep saved business outcomes from `CaptureTradeService` unchanged.

* [ ] Never expose:

  * SQL
  * credentials
  * stack traces

Why:

* Clients need predictable errors.
* Internal database/application details should not leak through HTTP responses.

Verification:

* [ ] Verify approved `400`, `404`, `409`, `415`, `422` and safe `500` responses.
* [ ] Verify no error contains SQL or credentials.

Engineering log:

* Record error mapping and any unexpected framework behaviour.

---

## 2.6.5 Verify the HTTP workflow

Using the real Spring Boot application and Testcontainers PostgreSQL:

* [ ] POST a valid trade.

* [ ] GET the returned trade.

* [ ] GET accounts.

* [ ] Retry the same POST.

* [ ] Verify:

```text
one trade
durable command replay
READY state
account balances unchanged
```

* [ ] Test the approved invalid/conflict cases through HTTP.

Ready for 2.7 when:

* all three Phase 2 endpoints work end to end
* HTTP behaviour matches the approved capture contract

---

# 2.7 Phase 2 verification

Purpose:

Confirm Trade Capture is complete and reliable before settlement logic is introduced.

---

## 2.7.1 Run the complete build and test suite

* [ ] Run:

```bash
./mvnw clean verify
```

* [ ] Confirm:

```text
Phase 1 tests                    PASS / FAIL
Flyway V1 + V2                   PASS / FAIL
Trade schema constraints         PASS / FAIL
Command-result constraints       PASS / FAIL
Reference-data reads             PASS / FAIL
Trade persistence                PASS / FAIL
Command persistence              PASS / FAIL
Trade Capture transaction        PASS / FAIL
Idempotent replay                PASS / FAIL
Capture rollback                 PASS / FAIL
HTTP capture                     PASS / FAIL
Trade read-back                  PASS / FAIL
Account inspection               PASS / FAIL
Account balances unchanged       PASS / FAIL
Maven verify                     PASS / FAIL
```

* [ ] Confirm required Testcontainers tests actually executed.
* [ ] Confirm no required test was skipped.

Engineering log:

* Record:

  * command
  * test count
  * failures/errors/skips
  * PostgreSQL version
  * final result

---

## 2.7.2 Run the minimal Trade Capture walkthrough

Use the deterministic Phase 1 demo data.

Starting state:

```text
Alice
AUD = 100000
EQ1 = 0

Bob
AUD = 0
EQ1 = 10
```

* [ ] Start PostgreSQL.

* [ ] Start the application.

* [ ] Apply the seed to a disposable/demo database.

* [ ] GET accounts.

* [ ] POST a trade such as:

```json
{
  "externalTradeId": "T-001",
  "buyerId": "00000000-0000-0000-0000-000000000001",
  "sellerId": "00000000-0000-0000-0000-000000000002",
  "securityId": "00000000-0000-0000-0000-0000000000e1",
  "quantity": 10,
  "cashAmount": 50000,
  "settlementDate": "2026-09-20"
}
```

with:

```text
Idempotency-Key: capture-T-001
```

* [ ] GET the returned trade location.

* [ ] Repeat the same POST.

* [ ] GET accounts again.

Expected:

```text
Trade
status = READY

Alice AUD = 100000
Alice EQ1 = 0
Bob AUD   = 0
Bob EQ1   = 10
```

Nothing settles in Phase 2.

Why:

* The walkthrough proves the backend can be used without inspecting PostgreSQL manually.
* It also makes the distinction between Trade Capture and Settlement visible.

Verification:

* [ ] Commands documented in `README.md` work as written.
* [ ] observed statuses and trade ID are recorded.
* [ ] account balances remain unchanged.

Engineering log:

* Record the walkthrough and observed result.
* Do not record local credentials.

---

## 2.7.3 Review the repository

* [ ] Inspect `git status`.

* [ ] Inspect new/modified files.

* [ ] Confirm:

  * V1 was not edited
  * V2 contains only Trade Capture structures
  * no settlement/journal schema exists
  * no account write API exists
  * no generated output is tracked
  * `.env` remains untracked
  * no secret was committed
  * no JPA/Hibernate was introduced
  * no Kafka was introduced
  * no speculative Phase 3 classes were added

---

## 2.7.4 Review the engineering log

* [ ] Confirm the log contains Phase 2 work using the same numbering:

  * 2.1 baseline/contract
  * 2.2 domain/API types
  * 2.3 V2 schema
  * 2.4 JDBC persistence
  * 2.5 capture service
  * 2.6 REST API

* [ ] Confirm meaningful work records:

  * what happened
  * why
  * files involved
  * SQL/configuration
  * commands/tests
  * result
  * failures
  * fixes
  * deviations

* [ ] Append the final Phase 2 verification result.

---

## 2.7.5 Review the final repository structure

Expected new Phase 2 shape is approximately:

```text
src/main/java/com/jasonwidjaja/dvp/
├── DvpApplication.java
├── api/
│   ├── TradeController.java
│   ├── AccountController.java
│   ├── ApiExceptionHandler.java
│   └── ...
├── application/
│   ├── CaptureCommand.java
│   ├── CaptureTradeService.java
│   ├── CommandOutcome.java
│   └── ...
├── domain/
│   ├── Participant.java
│   ├── Asset.java
│   ├── AssetType.java
│   ├── Account.java
│   ├── TradeTerms.java
│   ├── Trade.java
│   └── TradeStatus.java
└── persistence/
    ├── AccountRepository.java
    ├── ParticipantRepository.java
    ├── AssetRepository.java
    ├── TradeRepository.java
    └── CommandResultRepository.java

src/main/resources/db/migration/
├── V1__participants_assets_accounts.sql
└── V2__trades_and_command_results.sql
```

The exact structure may differ where technically justified.

Any meaningful deviation should be recorded in the engineering log.

---

## 2.7.6 Confirm Phase 2 exit criteria

Phase 2 is complete only when:

* [ ] Phase 1 still passes.
* [ ] V2 applies successfully to a clean database.
* [ ] V1 database upgrades to V2 without changing existing balances.
* [ ] valid matched trade can be captured.
* [ ] captured trade is stored as `READY`.
* [ ] captured economic terms cannot be silently overwritten.
* [ ] invalid trade is rejected.
* [ ] same external trade reference cannot create a second trade with different terms.
* [ ] same idempotency key + same request returns the original recorded outcome.
* [ ] same idempotency key + different request is rejected.
* [ ] business rejection replay follows the approved C4 policy.
* [ ] trade and command outcome commit together.
* [ ] technical failure before commit rolls both back.
* [ ] trade can be read through the API.
* [ ] accounts can be inspected through the API.
* [ ] Trade Capture leaves every account balance unchanged.
* [ ] PostgreSQL integration tests pass.
* [ ] `./mvnw clean verify` passes.
* [ ] no secrets or generated build output are committed.
* [ ] engineering log accurately describes Phase 2.

Then stop.

Do not implement settlement.

The next project action is:

```text
inspect completed Phase 2 repository
        |
        v
prepare docs/detailed-plan/phase-3.md
        |
        v
review Phase 3
        |
        v
implement Phase 3
```
