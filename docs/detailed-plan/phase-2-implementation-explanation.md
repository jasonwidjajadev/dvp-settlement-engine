# Phase 2 Implementation Explanation

## Goal

Phase 2 teaches the system how to safely record an already-agreed trade.

It does not move cash or securities yet.

Using the running example:

```text
Alice wants to buy 10 EQ1 from Bob for AUD 500.
```

Before Phase 2:

```text
Alice
AUD = 100000
EQ1 = 0

Bob
AUD = 0
EQ1 = 10
```

After Phase 2:

```text
Alice
AUD = 100000
EQ1 = 0

Bob
AUD = 0
EQ1 = 10

Trade T-001
buyer = Alice
seller = Bob
security = EQ1
quantity = 10
cash = 50000
status = READY
```

Nothing has settled yet.

The system has only recorded:

> This trade exists and is waiting to be settled.

---

# Phase 2 overview

```text
Client sends trade
       |
       v
POST /v1/trades
       |
       v
Validate request
       |
       v
Create CaptureCommand
       |
       v
CaptureTradeService
       |
       v
TransactionTemplate
       |
       +------------------------------+
       |                              |
       v                              v
check idempotency key          validate trade
       |                              |
       v                              v
command_result                  participant / asset
                                      |
                                      v
                                store trade
                                      |
                                      v
                              save command result
                                      |
                                      v
                                    COMMIT
                                      |
                                      v
                               send HTTP response
```

---

# 2.1 Confirm Phase 1 and approve the Trade Capture contract

## What this step is for

Before writing more code, Phase 2 first decides exactly what Trade Capture means.

This prevents the API, database and tests from each making slightly different assumptions.

---

## 2.1.1 Verify the Phase 1 baseline

Phase 1 already established:

```text
Java 21
Spring Boot
PostgreSQL
Flyway
Alice/Bob demo data
Spring JDBC
Testcontainers
```

The first job is simply to confirm all of that still works.

Run:

```bash
./mvnw verify
```

Why:

```text
Phase 1 foundation
        |
        v
must still work
        |
        v
before Phase 2 is added
```

This is like checking the foundation of a house before adding another floor.

---

## 2.1.2 Decide what a Trade is

A Phase 2 trade contains:

```text
external trade reference
buyer
seller
security
quantity
cash amount
settlement date
```

Example:

```text
externalTradeId = T-001
buyer = Alice
seller = Bob
security = EQ1
quantity = 10
cashAmount = 50000
settlementDate = 2026-09-20
```

Important:

```text
Trade Capture
!=
Settlement
```

Capture only records:

> Alice and Bob agreed to this trade.

It does not yet ask:

> Does Alice have enough money?

That belongs to Phase 3.

So a trade can be captured as `READY` even if it could not currently settle.

---

## Business identity

The external trade reference identifies the business trade.

Example:

```text
T-001
```

If this is submitted twice with the same terms, the system should not create two trades.

Conceptually:

```text
first request
T-001
Alice buys 10 EQ1 from Bob for 50000
        |
        v
trade created

second request
T-001
same terms
        |
        v
existing trade returned
```

But:

```text
T-001
quantity = 10
```

followed by:

```text
T-001
quantity = 20
```

must not silently modify the first trade.

That is a conflict.

---

# 2.1.3 Idempotency

Idempotency protects against duplicate network requests.

Imagine:

```text
client sends request
        |
        v
server stores trade
        |
        v
HTTP response is lost
        |
        v
client does not know if it worked
        |
        v
client retries
```

Without idempotency, the application could accidentally perform the same command twice.

So the client sends:

```text
Idempotency-Key: capture-T-001
```

The system remembers:

```text
key
+
request
+
result
```

Then a retry with the same key and same request returns the original result.

---

## Trade reference vs idempotency key

These are different identities:

```text
externalTradeId
→ identifies the business trade

Idempotency-Key
→ identifies one command/request
```

Example:

```text
Trade:
T-001

Command:
capture-request-abc
```

Another command key could refer to the same trade:

```text
Trade:
T-001

Command:
capture-request-xyz
```

The trade is still the same business trade.

---

## Durable result

A durable result is stored in PostgreSQL.

For example:

```text
command key
capture-T-001

HTTP status
201

response body
trade T-001, READY

Location
/v1/trades/...
```

Because it is stored in PostgreSQL, it survives:

```text
application restart
computer restart
process memory disappearing
```

That is why this is stronger than keeping idempotency information in a Java `HashMap`.

---

# 2.2 Trade domain and API types

Phase 2 now creates Java representations for the concepts just approved.

---

## 2.2.1 TradeTerms

`TradeTerms` describes the economic agreement.

Conceptually:

```text
TradeTerms
├── externalTradeId
├── buyerId
├── sellerId
├── securityId
├── quantity
├── cashAmount
└── settlementDate
```

For Alice and Bob:

```text
T-001
Alice
Bob
EQ1
10
50000
2026-09-20
```

These terms are immutable.

Once accepted, they should not be silently changed later.

---

## 2.2.2 Trade

`Trade` adds an internal ID and state:

```text
Trade
├── id
├── TradeTerms
└── status
```

Phase 2 only needs:

```text
READY
```

`READY` means:

> Captured successfully and waiting for settlement.

`SETTLED` belongs to Phase 3.

---

## 2.2.3 CaptureCommand

A `CaptureCommand` combines:

```text
Idempotency-Key
+
TradeTerms
```

Example:

```text
CaptureCommand

key:
capture-T-001

terms:
Alice buys 10 EQ1 from Bob for AUD 500
```

The key does not belong inside the trade itself.

One business trade and one client command are different concepts.

---

## 2.2.4 DTOs

DTO means:

```text
Data Transfer Object
```

A DTO represents the JSON accepted or returned by the API.

Example request:

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

Flow:

```text
HTTP JSON
    |
    v
request DTO
    |
    v
TradeTerms
    |
    v
CaptureCommand
```

The DTO belongs to the HTTP/API layer.

The domain objects belong to the application/business model.

---

## Validation

Some errors can be rejected immediately.

Example:

```json
{
  "quantity": -10
}
```

or:

```json
{
  "buyerId": "banana"
}
```

These are structural/input errors.

But:

```text
buyerId = valid UUID
```

does not prove that buyer exists.

So:

```text
"Is this request shaped correctly?"
→ API validation

"Does this participant actually exist?"
→ application/business validation
```

This distinction matters because business rejections can be stored as durable command outcomes.

---

# 2.3 Flyway V2 Trade Capture schema

Phase 1 database:

```text
participant
asset
account
```

Phase 2 adds:

```text
trade
command_result
```

So PostgreSQL becomes:

```text
PostgreSQL
│
├── participant
├── asset
├── account
├── trade
└── command_result
```

---

## 2.3.1 V2 migration

Create:

```text
V2__trades_and_command_results.sql
```

Do not edit V1.

Why:

```text
V1
→ Phase 1 history

V2
→ Phase 2 schema change
```

Flyway should be able to upgrade an existing Phase 1 database without destroying its data.

---

## 2.3.2 Trade table

The trade table stores accepted trades.

Conceptually:

```text
trade

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

Example row:

```text
id                8724...
external_trade_id T-001
buyer             Alice UUID
seller            Bob UUID
security          EQ1 UUID
quantity          10
cash_amount       50000
settlement_date   2026-09-20
status            READY
```

PostgreSQL should reject invalid state such as:

```text
buyer = seller
negative quantity
negative cash amount
unknown buyer
unknown seller
unknown security
duplicate external trade reference
unsupported status
```

This gives two layers of protection:

```text
Java validation
        +
PostgreSQL constraints
```

---

## 2.3.3 command_result table

`command_result` is the durable memory for idempotent commands.

Conceptually:

```text
command_result

command_key
operation
request_identity
http_status
response_body
location
```

Example:

```text
command_key
capture-T-001

operation
CAPTURE_TRADE

request_identity
the parsed trade terms

http_status
201

response_body
{ ... }

location
/v1/trades/8724...
```

On retry:

```text
same key
    |
    v
read command_result
    |
    v
same request?
    |
    +--> yes → replay saved result
    |
    +--> no  → conflict
```

---

# 2.4 Spring JDBC persistence

Repositories contain explicit SQL used by the application.

```text
application logic
      |
      v
repositories
      |
      v
Spring JDBC
      |
      v
PostgreSQL
```

---

## 2.4.1 ParticipantRepository

Needed to answer:

```text
Does Alice exist?
Does Bob exist?
```

Example conceptual method:

```java
participantRepository.existsById(id)
```

which becomes SQL roughly like:

```sql
SELECT ...
FROM participant
WHERE id = :participantId
```

---

## 2.4.2 AssetRepository

Needed to answer:

```text
Does EQ1 exist?
Is it actually a SECURITY?
```

Example:

```text
EQ1
type = SECURITY

AUD
type = CASH
```

So this request must fail:

```text
securityId = AUD
```

because AUD exists, but it is not a security.

---

## 2.4.3 TradeRepository

Handles SQL for the `trade` table.

Typical operations:

```text
findById(...)
findByExternalTradeId(...)
insertIfAbsent(...)
```

Why `insertIfAbsent`?

Two commands might try to capture the same external trade reference.

Instead of relying only on:

```text
check
then insert
```

PostgreSQL uniqueness becomes the final authority.

Conceptually:

```sql
INSERT ...
ON CONFLICT (external_trade_id)
DO NOTHING
RETURNING ...
```

The repository must never overwrite previously accepted terms.

---

## 2.4.4 CommandResultRepository

Handles idempotency persistence.

Typical operations:

```text
claim command key
read command result
finalize command result
```

"Claim" means:

> This database transaction currently owns processing for this command key.

PostgreSQL uniqueness prevents two claims for the same key.

A completed result must never be overwritten.

---

# 2.5 Atomic Trade Capture service

This is the first real Phase 2 business operation.

The service coordinates:

```text
idempotency
+
business validation
+
trade creation
+
durable result
+
transaction
```

---

## What is a database transaction?

Suppose capture needs to do:

```text
1. create trade
2. save command result
```

If step 1 commits and step 2 fails:

```text
trade exists
command result missing
```

That makes safe retry difficult.

A transaction gives:

```text
Either:

trade exists
AND
command result exists

OR:

neither exists
```

That is atomicity.

---

# What is TransactionTemplate?

`TransactionTemplate` is a Spring tool used to explicitly wrap work inside one database transaction.

Conceptually:

```java
transactionTemplate.execute(status -> {

    claimCommand();

    validateTrade();

    insertTrade();

    saveCommandResult();

    return result;
});
```

Spring and PostgreSQL roughly perform:

```text
BEGIN

claim command
validate
insert trade
save command result

COMMIT
```

If something fails:

```text
BEGIN

claim command
insert trade

FAILURE

ROLLBACK
```

The database behaves as if the uncommitted writes never happened.

---

## Why use TransactionTemplate here?

This project is specifically about transaction correctness.

`TransactionTemplate` makes the boundary visually explicit:

```text
everything inside this callback
=
one database transaction
```

That makes it easier to learn, inspect and discuss.

---

## TransactionTemplate vs @Transactional

Both are Spring transaction mechanisms.

With `@Transactional`:

```text
annotation
    |
    v
Spring starts transaction around method
    |
    v
method executes
    |
    v
commit
```

With `TransactionTemplate`:

```text
code explicitly calls
transactionTemplate.execute(...)
    |
    v
transaction starts
    |
    v
callback executes
    |
    v
commit
```

For this project, `TransactionTemplate` is chosen because the boundary is especially easy to see.

---

## What is JdbcTransactionManager?

`TransactionTemplate` needs something that knows how to perform:

```text
BEGIN
COMMIT
ROLLBACK
```

for JDBC connections.

That is the transaction manager.

Conceptually:

```text
CaptureTradeService
        |
        v
TransactionTemplate
        |
        v
JdbcTransactionManager
        |
        v
DataSource / HikariCP
        |
        v
PostgreSQL
```

Simple distinction:

```text
TransactionTemplate
→ says "do all of this together"

JdbcTransactionManager
→ performs JDBC transaction commit/rollback
```

---

# 2.5 Capture sequence

Suppose the client sends:

```text
Idempotency-Key = capture-T-001

T-001
Alice buys 10 EQ1 from Bob
for 50000
```

---

## Step 1: claim/check idempotency key

```text
capture-T-001 already exists?
```

If no:

```text
continue
```

If yes:

```text
same request?
    |
    +--> yes → return original saved result
    |
    +--> no  → conflict
```

---

## Step 2: validate business references

Check:

```text
Alice != Bob
Alice exists
Bob exists
EQ1 exists
EQ1 is SECURITY
```

If business validation fails:

```text
save durable rejection
commit
return business error
```

---

## Step 3: create or resolve the trade

Try to insert:

```text
T-001
```

If it is new:

```text
create trade
status = READY
```

If it already exists:

```text
same terms?
    |
    +--> yes → return existing trade
    |
    +--> no  → conflict
```

---

## Step 4: save command result

Example:

```text
command key:
capture-T-001

HTTP result:
201 Created

body:
Trade T-001 / READY

Location:
/v1/trades/...
```

---

## Step 5: commit

Only after everything is ready:

```text
COMMIT
```

Then the service returns the result to the controller.

Important ordering:

```text
database commit
    |
    v
HTTP response
```

not:

```text
send success
    |
    v
hope the database commits
```

---

# 2.5 Business cases to test

## First capture

```text
key A
T-001
```

Expected:

```text
1 trade
1 command result
status = READY
```

---

## Same key, same request

```text
key A
T-001
same terms
```

Expected:

```text
original saved response
no second trade
no second command row
```

---

## Same key, different request

```text
key A
T-001
quantity changed
```

Expected:

```text
conflict
original result unchanged
```

---

## New key, same trade

```text
key B
T-001
same terms
```

Expected:

```text
existing trade returned
one trade total
key B gets its own durable outcome
```

---

## New key, same reference, changed terms

```text
key C
T-001
different quantity
```

Expected:

```text
conflict
original trade unchanged
```

---

# 2.5 Rollback test

Intentionally fail after inserting the trade but before finalizing the command result.

```text
claim command
    |
    v
insert trade
    |
    v
FORCED FAILURE
    |
    v
ROLLBACK
```

Then PostgreSQL should show:

```text
trade             absent
command result    absent
balances          unchanged
```

After removing the failure, retrying should succeed once.

This proves the application is actually using PostgreSQL transactions correctly.

---

# 2.6 Trade Capture REST API

Phase 2 exposes three endpoints:

```text
POST /v1/trades
GET  /v1/trades/{id}
GET  /v1/accounts
```

---

## POST /v1/trades

Example:

```text
POST /v1/trades
Idempotency-Key: capture-T-001
Content-Type: application/json
```

Body:

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

Flow:

```text
HTTP request
    |
    v
request DTO
    |
    v
validation
    |
    v
CaptureCommand
    |
    v
CaptureTradeService
    |
    v
TransactionTemplate
    |
    v
PostgreSQL
```

The controller should not contain SQL or business validation.

---

## GET /v1/trades/{id}

After capture, the API may return:

```text
Location: /v1/trades/8724...
```

Calling that endpoint returns the stored trade:

```text
T-001
Alice
Bob
EQ1
10
50000
READY
```

This proves capture really persisted.

---

## GET /v1/accounts

This reuses the Phase 1 account repository.

Before capture:

```text
Alice AUD = 100000
Alice EQ1 = 0
Bob AUD = 0
Bob EQ1 = 10
```

After capture:

```text
Alice AUD = 100000
Alice EQ1 = 0
Bob AUD = 0
Bob EQ1 = 10
```

Nothing moves in Phase 2.

---

# API error handling

Instead of exposing internal errors such as:

```text
org.postgresql.util.PSQLException...
```

the API returns small safe errors:

```json
{
  "code": "UNKNOWN_SECURITY",
  "message": "Security does not exist"
}
```

The API should not expose:

```text
SQL
database credentials
stack traces
internal implementation details
```

---

# 2.7 Final Phase 2 verification

The complete runtime path is:

```text
HTTP
 |
 v
Spring MVC
 |
 v
DTO validation
 |
 v
CaptureCommand
 |
 v
CaptureTradeService
 |
 v
TransactionTemplate
 |
 v
Spring JDBC repositories
 |
 v
PostgreSQL
 |
 v
COMMIT
 |
 v
HTTP response
```

Automated tests use:

```text
JUnit
 |
 v
Testcontainers
 |
 v
real PostgreSQL 18.6
```

---

# Final Phase 2 walkthrough

Starting state:

```text
Alice
AUD = 100000
EQ1 = 0

Bob
AUD = 0
EQ1 = 10
```

Capture:

```text
Alice buys 10 EQ1 from Bob
for AUD 500
```

Result:

```text
POST /v1/trades
        |
        v
201 Created
        |
        v
Trade status = READY
```

Read it:

```text
GET /v1/trades/{id}
```

Inspect balances:

```text
GET /v1/accounts
```

Balances remain:

```text
Alice
AUD = 100000
EQ1 = 0

Bob
AUD = 0
EQ1 = 10
```

Retry the POST:

```text
same Idempotency-Key
        |
        v
same recorded result
        |
        v
no duplicate trade
```

---

# What Phase 2 has not done

At the end:

```text
Trade T-001
status = READY
```

But:

```text
Alice has not paid Bob
Bob has not transferred EQ1
no journal exists
no postings exist
```

The system has only safely recorded the agreement.

Phase 3 then asks:

```text
Can this READY trade actually settle?
```

That later flow will involve:

```text
lock trade
    |
    v
lock four accounts
    |
    v
check money and securities
    |
    v
move both asset legs
    |
    v
create journal + four postings
    |
    v
mark SETTLED
```

---

# Mental model

```text
Phase 1
"Can Java reliably store and read financial state?"

        |
        v

Phase 2
"Can I reliably record an agreed trade and safely recognize retries?"

        |
        v

Phase 3
"Can I safely move the money and securities?"
```

---

# Phase 2 concepts to remember

```text
DTO
→ HTTP request/response representation

TradeTerms
→ immutable economic agreement

Trade
→ accepted trade + internal ID + READY state

CaptureCommand
→ idempotency key + TradeTerms

Repository
→ explicit SQL access

Business identity
→ prevents duplicate trade

Idempotency key
→ prevents duplicate command execution

command_result
→ durable memory of what happened

TransactionTemplate
→ explicitly groups database work into one transaction

JdbcTransactionManager
→ performs JDBC commit/rollback

CaptureTradeService
→ orchestrates the whole capture operation

Controller
→ translates HTTP into application commands

PostgreSQL
→ durable source of truth
