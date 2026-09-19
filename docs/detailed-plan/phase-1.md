# Phase 1: Java and PostgreSQL Foundation

## Goal

Set up the minimum Java, Spring Boot and PostgreSQL foundation required before Trade Capture begins.

Phase 1 establishes:

```text
Java 21
    |
    v
Maven
    |
    v
Spring Boot
    |
    v
PostgreSQL
    |
    v
Flyway schema
    |
    v
Demo account data
    |
    v
Spring JDBC
    |
    v
PostgreSQL integration tests
```

At the end of this phase:

* the Java application can build and start
* PostgreSQL can run locally
* Spring Boot can connect to PostgreSQL
* Flyway can create the initial database schema
* Alice/Bob demo accounts can be loaded
* Java can read those accounts using Spring JDBC
* automated tests can run against a real temporary PostgreSQL database
* `./mvnw verify` passes

Phase 1 does not implement:

* Trade Capture
* settlement
* journals
* idempotent financial commands
* settlement concurrency
* reconciliation
* load testing
* Kafka
* transactional outbox
* frontend

Do not begin Phase 2 automatically.

---

# 1.1 Repository setup

Purpose:

Prepare the repository so future work follows a clear structure and every implementation step can be tracked.

No application code should be written during this task.

## 1.1.1 Inspect the current repository

* [ ] Inspect the complete repository tree.

  * Understand what files and directories already exist.
  * Confirm which planning documents are present.
  * Confirm whether any Java/Maven project files already exist.
  * Do not assume the repository is empty.

* [ ] Run `git status`.

  * Identify modified files.
  * Identify untracked files.
  * Identify staged files.
  * Preserve existing work instead of overwriting it.

* [ ] Record the initial repository state.

  * This gives us a baseline before implementation begins.
  * Later we can distinguish existing files from files added during Phase 1.

Why:

* Codex must understand the actual repository before modifying it.
* Existing work should never be overwritten because the implementation plan assumed a blank repository.
* The repository baseline also makes the engineering log easier to understand later.

Verification:

* [ ] Current repository tree is known.
* [ ] Git status is known.
* [ ] Existing work has been identified and preserved.

Engineering log:

* Record the repository state.

  * Existing directories.
  * Existing important files.
  * Existing modified/untracked files.
* Record any unexpected repository state.

---

## 1.1.2 Read the authoritative project documents

* [ ] Read `docs/project-spec.md`.

  * This defines what the finished product must do.
  * It defines:

    * features
    * system behaviour
    * invariants
    * scope
    * non-goals
    * definition of done

* [ ] Read `docs/decisions.md`.

  * This explains the architecture decisions that have already been accepted.
  * Examples:

    * Java + Spring Boot
    * Spring JDBC
    * PostgreSQL
    * gross trade-by-trade settlement
    * deterministic row locking
    * no Kafka

* [ ] Read `docs/implementation-plan.md`.

  * This defines the overall order of implementation phases.
  * It should not be treated as detailed instructions for every file.

* [ ] Read `docs/detailed-plan/phase-1.md`.

  * This file defines the detailed tasks for the current phase.
  * Codex should follow its numbering while implementing and logging work.

Why:

* These documents answer different questions.

  * project spec = what
  * decisions = why
  * implementation plan = high-level order
  * detailed phase plan = exact current work
* Codex should not silently redesign the project while implementing it.

Verification:

* [ ] No conflict has been found between the documents.
* [ ] If a conflict exists, stop and report it before implementation.

Engineering log:

* Record that the authoritative documents were reviewed.
* Record any ambiguity or conflict discovered.

---

## 1.1.3 Create or update `AGENTS.md`

What is `AGENTS.md`?

* `AGENTS.md` contains stable instructions for coding agents working inside this repository.

* It tells future Codex sessions how the project should be worked on.

* It is not a project specification.

* It is not a chronological history.

* [ ] Create `AGENTS.md` if it does not exist.

* [ ] Preserve useful existing instructions if it already exists.

* [ ] Record the authoritative project documents.

* [ ] Record the approved stack:

  * Java 21
  * Spring Boot
  * Spring MVC
  * Spring JDBC
  * PostgreSQL
  * Flyway
  * JUnit
  * AssertJ
  * Testcontainers

* [ ] Record the project namespace:

  * Maven group ID: `com.jasonwidjaja`
  * Maven artifact ID: `dvp-settlement-engine`
  * Java base package: `com.jasonwidjaja.dvp`

* [ ] Record important restrictions:

  * do not introduce JPA/Hibernate
  * do not introduce Kafka
  * do not introduce microservices
  * do not introduce a frontend
  * do not silently change project scope
  * do not reopen architecture decisions without flagging them

* [ ] Record the phase workflow:

  * inspect repository
  * read project documents
  * review detailed phase plan
  * implement current phase
  * verify
  * update engineering log
  * stop before next phase

Why:

* New Codex sessions may not have the context of previous conversations.
* The repository itself should explain how agents are expected to work.
* This prevents the workflow from depending on repeated prompts from the user.

Verification:

* [ ] `AGENTS.md` exists.
* [ ] Instructions match the approved project documents.
* [ ] It contains stable rules only.

Engineering log:

* Record whether `AGENTS.md` was created or updated.

  * What instructions were added.
  * Why they belong there.

---

## 1.1.4 Create the engineering log

What is the engineering log?

* `docs/engineering-log.md` records what actually happened during implementation.

* It is different from the detailed plan:

  * detailed plan = expected work
  * engineering log = actual work

* [ ] Create `docs/engineering-log.md` if it does not exist.

* [ ] Start with:

  * Phase 1
  * Step 1.1

* [ ] Use the same numbering as this detailed plan.

* [ ] Record:

  * actions performed
  * files added or modified
  * important choices
  * commands run
  * test results
  * failures
  * fixes
  * deviations from the plan

* [ ] Never erase an earlier failure simply because it was later fixed.

  * Record the problem.
  * Record the reason.
  * Record the fix.
  * Record the successful verification afterward.

Why:

* A teammate can understand how the repository reached its current state.
* The user can learn how the Java project was assembled.
* Failures and corrections are useful engineering history.

Example:

```markdown
### 1.2.2 Configure pom.xml

- Added `pom.xml`.
  - Defines the Maven project.
  - Set group ID to `com.jasonwidjaja`.
  - Set artifact ID to `dvp-settlement-engine`.
  - Configured Java 21.

- Added Spring JDBC dependency.
  - Provides explicit SQL-based database access.
  - Required because the architecture deliberately avoids JPA/Hibernate.

- Ran `./mvnw validate`.
  - Purpose: verify Maven accepts the project configuration.
  - Result: passed.
```

Verification:

* [ ] Engineering log exists.
* [ ] It is ready to mirror Phase 1 numbering.

---

## 1.1.5 Review `.gitignore`

What is `.gitignore`?

* `.gitignore` tells Git which local/generated files should not be committed.

* Java builds and development tools produce files that do not belong in source control.

* [ ] Inspect the existing `.gitignore`.

* [ ] Preserve useful existing rules.

* [ ] Ensure Maven output is ignored.

  * `target/`

* [ ] Ensure local environment files are ignored.

  * `.env`

* [ ] Ensure operating-system and IDE files are handled appropriately.

  * Only add rules that are actually useful.
  * Do not blindly replace the existing file with a template.

* [ ] Ensure secrets cannot accidentally be committed.

Why:

* Generated build files create noise.
* Local credentials must never enter Git history.
* A clean repository should contain source/configuration, not local machine state.

Verification:

* [ ] `target/` will not be tracked.
* [ ] `.env` will not be tracked.
* [ ] Existing useful ignore rules remain.

Engineering log:

* Record any `.gitignore` changes.

  * Rule added.
  * What it protects.
  * Why it was required.

---

## 1.1.6 Verify repository preparation

* [ ] Inspect the repository again.

* [ ] Confirm:

  * project documents exist
  * `AGENTS.md` exists
  * `engineering-log.md` exists
  * `.gitignore` is suitable
  * no Java application has been created yet
  * no existing work was lost

* [ ] Update the engineering log with the resulting repository state.

Ready for 1.2 when:

* repository state is understood
* working instructions exist
* logging exists
* Git exclusions are correct
* existing work is preserved

---

# 1.2 Java 21 and Maven foundation

Purpose:

Create the build system that will compile, test and package every Java component in this project.

---

## 1.2.1 Verify Java 21

What is Java 21?

* Java is the programming language used for the backend.

* The JDK contains:

  * Java compiler
  * Java runtime
  * development tools

* This project targets Java 21.

* [ ] Run:

```bash
java -version
```

* [ ] Confirm Java 21 is active.
* [ ] Record the exact installed version.

Why:

* Maven will compile the project using the available JDK.
* A different Java version can produce build or dependency incompatibilities.

Verification:

* [ ] `java -version` reports Java 21.

Engineering log:

* Record:

  * Java vendor
  * Java version
  * verification result

---

## 1.2.2 Create and configure `pom.xml`

What is Maven?

* Maven is Java's build and dependency-management tool.
* It handles:

  * downloading libraries
  * compiling source code
  * running tests
  * packaging the application

What is `pom.xml`?

* `pom.xml` is Maven's main project configuration file.
* POM means Project Object Model.
* It lives in the repository root.

It defines:

* project identity

* Java version

* dependencies

* build plugins

* [ ] Create `pom.xml`.

* [ ] Configure project identity:

  * group ID: `com.jasonwidjaja`

    * identifies the organisation/namespace that owns the project
  * artifact ID: `dvp-settlement-engine`

    * identifies this specific application
  * Java version: 21

* [ ] Configure Spring Boot.

* [ ] Add only the dependencies required for the Phase 1 foundation.

Application dependencies:

* Spring Boot

  * provides application configuration and runtime conventions

* Spring MVC

  * provides the HTTP/REST framework used in later phases

* Spring JDBC

  * provides database access while keeping SQL explicit

* PostgreSQL JDBC driver

  * allows Java to communicate with PostgreSQL

* Flyway

  * manages versioned database migrations

* validation

  * provides standard request/domain validation infrastructure

Test dependencies:

* JUnit

  * test framework

* AssertJ

  * readable test assertions

* Testcontainers

  * starts real PostgreSQL instances during automated tests

* [ ] Do not add:

  * JPA
  * Hibernate ORM
  * H2
  * Kafka
  * unrelated future dependencies

Why:

* Every later source file depends on having a valid build system.
* Dependencies should be introduced because current work needs them, not because they might be useful eventually.

Verification:

* [ ] Maven accepts the POM.
* [ ] Dependencies resolve successfully.

Engineering log:

* Record `pom.xml`.

  * project coordinates
  * Java version
  * each important dependency

    * what it provides
    * why Phase 1 needs it

---

## 1.2.3 Add Maven Wrapper

What is Maven Wrapper?

* Maven itself is normally installed as a command called `mvn`.
* Maven Wrapper lets the repository provide its own Maven entry point.

It normally adds:

```text
mvnw
mvnw.cmd
.mvn/
```

* `mvnw`

  * macOS/Linux command

* `mvnw.cmd`

  * Windows command

* `.mvn/`

  * stores Maven Wrapper configuration

* [ ] Add Maven Wrapper.

* [ ] Ensure `mvnw` is executable.

Why:

* A new developer can clone the repository and run Maven without manually installing the exact Maven version.
* Build instructions become reproducible.

Verification:

* [ ] Run:

```bash
./mvnw -v
```

* [ ] Confirm:

  * wrapper works
  * Maven starts
  * Java 21 is being used

Engineering log:

* Record:

  * wrapper files added
  * Maven version
  * why the wrapper is committed to the repository
  * verification command/result

---

## 1.2.4 Verify the Maven foundation

* [ ] Validate the project configuration.
* [ ] Confirm dependencies download.
* [ ] Confirm no prohibited dependencies were introduced.
* [ ] Confirm Maven is using Java 21.

Ready for 1.3 when:

* Java 21 works
* `pom.xml` is valid
* Maven Wrapper works
* Phase 1 dependencies resolve

---

# 1.3 Spring Boot application skeleton

Purpose:

Create the smallest executable Spring Boot application and establish the standard Java project structure.

---

## 1.3.1 Create the Java source structure

Java/Maven projects conventionally use:

```text
src/
├── main/
│   ├── java/
│   └── resources/
└── test/
    └── java/
```

* `src/main/java`

  * production Java source code

* `src/main/resources`

  * configuration
  * SQL migrations
  * other application resources

* `src/test/java`

  * automated Java tests

* [ ] Create these directories if they do not already exist.

Why:

* Maven and Spring follow these conventions automatically.
* Keeping the standard structure reduces custom configuration and makes the repository familiar to Java developers.

Engineering log:

* Record each directory introduced.

  * What belongs there.
  * Why it exists.

---

## 1.3.2 Create the base Java package

What is a Java package?

* Packages organise Java classes and provide a unique namespace.
* Package names map directly to directories.

The approved package is:

```text
com.jasonwidjaja.dvp
```

which maps to:

```text
src/main/java/com/jasonwidjaja/dvp/
```

* [ ] Create the base package directory.
* [ ] Mirror the package in tests when required.
* [ ] Do not create empty packages for future features.

Why:

* Spring will use the root package as the base for discovering application components.
* `com.jasonwidjaja` follows the reverse-domain convention for the owned `jasonwidjaja.com` domain.

Verification:

* [ ] Package directory matches the configured Java namespace.

Engineering log:

* Record:

  * package chosen
  * directory created
  * why package structure matters

---

## 1.3.3 Create `DvpApplication.java`

What is it?

* `DvpApplication.java` is the application entry point.
* It contains the `main` method used to start Spring Boot.

What is `@SpringBootApplication`?

* It marks the root Spring Boot application.

* At a practical level it tells Spring Boot to:

  * apply automatic configuration
  * discover Spring components beneath the package
  * start the application context

* [ ] Create:

```text
src/main/java/com/jasonwidjaja/dvp/DvpApplication.java
```

* [ ] Keep it minimal.
* [ ] Do not add business logic to it.

Why:

* The project needs a single executable entry point.
* Business logic should live in domain/application classes later, not in the bootstrap file.

Verification:

* [ ] Java source compiles.
* [ ] Spring application context can start.

Engineering log:

* Record:

  * file added
  * purpose of `main`
  * purpose of `@SpringBootApplication`
  * startup result

---

## 1.3.4 Verify the Spring Boot skeleton

* [ ] Compile the project.
* [ ] Start or test the Spring Boot context.
* [ ] Confirm there are no business endpoints yet.
* [ ] Confirm no speculative feature code exists.

Ready for 1.4 when:

* Java structure exists
* base package exists
* Spring Boot entry point exists
* application compiles and starts

---

# 1.4 PostgreSQL and application configuration

Purpose:

Connect the Spring application to the database that will eventually hold all financial state.

---

## 1.4.1 Establish PostgreSQL for local development

What is PostgreSQL?

* PostgreSQL is a relational database.
* In this project it becomes the durable source of truth for:

  * participants
  * assets
  * balances
  * trades
  * settlement history
  * idempotency records
  * reconciliation data

Later, PostgreSQL also provides:

* transactions

* row-level locking

* constraints

* rollback

* [ ] Determine whether an existing local PostgreSQL instance is suitable.

* [ ] If not, use a PostgreSQL-only Docker Compose service.

* [ ] Do not Dockerize the Spring Boot application in Phase 1.

Why:

* The core engineering problems depend on real transactional database behaviour.
* PostgreSQL is therefore part of the application architecture, not just development tooling.

Verification:

* [ ] PostgreSQL starts.
* [ ] PostgreSQL accepts connections.

Engineering log:

* Record:

  * how PostgreSQL is being run
  * configuration chosen
  * version used
  * any Docker/connection problem
  * result

---

## 1.4.2 Create Spring application configuration

What is `application.yml`?

* `application.yml` is one of Spring Boot's standard configuration files.

* It can define:

  * application settings
  * database connection settings
  * logging settings
  * Spring behaviour

* [ ] Create:

```text
src/main/resources/application.yml
```

* [ ] Configure only settings currently required.
* [ ] Keep database secrets out of the file.

Why:

* Application behaviour should be configuration-driven rather than hard-coded into Java source.

Engineering log:

* Record:

  * configuration file
  * important properties added
  * what each controls

---

## 1.4.3 Configure environment variables

What are environment variables?

* Environment variables provide values to the application outside the source code.

* They are useful for configuration that changes between machines or contains secrets.

* [ ] Define variables needed for local PostgreSQL.

  * database URL/host
  * database name
  * username
  * password
  * port where necessary

* [ ] Create `.env.example`.

  * Document required variable names.
  * Use placeholders instead of real credentials.

* [ ] Ensure `.env` remains ignored by Git.

Why:

* Credentials should never be committed.
* Different environments should be able to provide different values without modifying source code.

Verification:

* [ ] Application configuration resolves the environment variables.
* [ ] No real password is tracked in Git.

Engineering log:

* Record:

  * variable names
  * what each controls
  * files changed
  * do not record actual secrets

---

## 1.4.4 Configure Spring JDBC

What is JDBC?

* JDBC is Java's standard interface for communicating with relational databases.

What is Spring JDBC?

* Spring JDBC sits on top of JDBC.
* It reduces repetitive connection/result handling while allowing the project to write explicit SQL.

What is a `DataSource`?

* A `DataSource` provides database connections to the application.

* Spring Boot normally configures it from the database properties.

* [ ] Configure Spring Boot to connect to PostgreSQL through JDBC.

* [ ] Let Spring manage the normal `DataSource`.

* [ ] Do not introduce an ORM.

Why:

* Later settlement correctness depends on explicit PostgreSQL transactions and row locks.
* Keeping SQL visible makes those behaviours easier to inspect and explain.

Verification:

* [ ] Spring Boot can establish a real PostgreSQL connection.
* [ ] A simple database query succeeds.

Engineering log:

* Record:

  * JDBC configuration
  * how Spring receives connections
  * connection test/result

---

## 1.4.5 Verify PostgreSQL integration

* [ ] Start PostgreSQL.
* [ ] Start Spring Boot.
* [ ] Confirm database connection succeeds.
* [ ] Confirm no schema is being created manually yet.

Ready for 1.5 when:

* PostgreSQL runs
* environment configuration works
* Spring Boot connects successfully through JDBC

---

# 1.5 Flyway and V1 database schema

Purpose:

Create the first reproducible, version-controlled database structure.

---

## 1.5.1 Set up Flyway migrations

What is a database schema?

* The schema defines how data is structured in PostgreSQL.
* It includes:

  * tables
  * columns
  * relationships
  * constraints

What is a migration?

* A migration is a versioned change to that schema.

What is Flyway?

* Flyway finds migration files and applies them in version order.

* It records which migrations have already executed.

* [ ] Create:

```text
src/main/resources/db/migration/
```

* [ ] Create:

```text
V1__participants_assets_accounts.sql
```

Why the filename?

* `V1`

  * version 1
* `__`

  * Flyway naming separator
* `participants_assets_accounts`

  * human-readable description

Why:

* Anyone should be able to create the same database structure from the repository.
* Schema changes should be reviewable in Git.

Engineering log:

* Record:

  * Flyway directory
  * migration filename
  * what V1 is responsible for

---

## 1.5.2 Define the Phase 1 numeric representation

For this project:

* [ ] Represent AUD in integer minor units.

  * `100000` = AUD 1,000.
  * `50000` = AUD 500.

* [ ] Represent securities as whole units.

  * `10` = 10 EQ1 shares.

* [ ] Do not use floating-point numbers for balances.

Why:

* Floating-point types cannot exactly represent all decimal values.
* Integer minor units make the simplified monetary model exact and easy to reason about.
* Fractional securities are deliberately outside the current project scope.

Engineering log:

* Record the representation used.
* Record corresponding Java/PostgreSQL types.
* Record any technical limitation discovered.

---

## 1.5.3 Create the participant table

What is a participant?

* A participant is an entity that can own cash or securities.

* In the initial demo:

  * Alice
  * Bob

* [ ] Create participant storage.

* [ ] Give every participant a stable identifier.

* [ ] Store a display name.

* [ ] Add required constraints.

Why:

* Accounts and trades need stable owners.
* Names alone should not be treated as permanent database identity.

Verification:

* [ ] Valid participant can be inserted.
* [ ] Invalid required values are rejected.

Engineering log:

* Record:

  * table
  * important columns
  * constraints
  * why each exists

---

## 1.5.4 Create the asset table

What is an asset?

* An asset represents something held in an account.

Initial assets:

```text
AUD
EQ1
```

* AUD represents cash.

* EQ1 represents a fictional security.

* [ ] Create asset storage.

* [ ] Store:

  * stable ID
  * code
  * asset type

* [ ] Ensure asset codes are unique.

* [ ] Restrict asset types to supported values.

Why:

* The same account model can represent both cash and securities.
* Later the DvP transaction will move two different assets using the same general account concept.

Verification:

* [ ] AUD can be inserted.
* [ ] EQ1 can be inserted.
* [ ] duplicate code fails.
* [ ] invalid type fails.

Engineering log:

* Record:

  * table
  * fields
  * asset-type rule
  * constraints

---

## 1.5.5 Create the account table

What is an account?

* An account represents one participant's holdings of one asset.

Examples:

```text
Alice / AUD
Alice / EQ1
Bob / AUD
Bob / EQ1
```

* [ ] Create account storage.

* [ ] Each account references:

  * one participant
  * one asset

* [ ] Store:

  * opening balance
  * current balance

* [ ] Enforce:

  * valid participant
  * valid asset
  * one account for each participant/asset pair
  * non-negative opening balance
  * non-negative current balance

Why two balances?

* opening balance records the initial state
* current balance records the latest state
* later we will verify:

```text
current balance
=
opening balance
+
committed settlement postings
```

Verification:

* [ ] valid account inserts.
* [ ] duplicate participant/asset account fails.
* [ ] negative opening balance fails.
* [ ] negative current balance fails.
* [ ] unknown participant fails.
* [ ] unknown asset fails.

Engineering log:

* Record:

  * table
  * relationships
  * constraints
  * reason for opening/current balances

---

## 1.5.6 Verify Flyway V1

* [ ] Start from a fresh PostgreSQL database.
* [ ] Run the application/Flyway.
* [ ] Confirm V1 is applied.
* [ ] Confirm Flyway records V1 in its migration history.
* [ ] Confirm:

  * participant table exists
  * asset table exists
  * account table exists
* [ ] Run migration again.

  * Confirm V1 is not applied twice.
  * Confirm existing data is not reset.

Ready for 1.6 when:

* V1 creates the full Phase 1 schema
* constraints work
* migration is repeatable

---

# 1.6 Deterministic demo data

Purpose:

Create a known starting state that can be used consistently in development, demos and tests.

---

## 1.6.1 Create the seed mechanism

What is seed data?

* Seed data creates known example records inside an already-created schema.

It is different from a migration:

```text
Migration
→ creates the structures

Seed
→ creates example records
```

* [ ] Create one canonical seed mechanism.
* [ ] Keep it separate from V1.
* [ ] Require it to be invoked explicitly.
* [ ] Do not run it automatically every time the application starts.

Why:

* Not every database using the schema should automatically contain Alice and Bob.
* Separating schema from data makes responsibilities clear.

Engineering log:

* Record:

  * seed file/mechanism
  * where it lives
  * how it is executed

---

## 1.6.2 Seed participants

* [ ] Add Alice.
* [ ] Add Bob.
* [ ] Use deterministic identifiers.

Why deterministic IDs?

* Tests and demo scripts can refer to known records repeatedly.
* Results become reproducible.

Engineering log:

* Record:

  * Alice identifier
  * Bob identifier
  * why deterministic values are used

---

## 1.6.3 Seed assets

* [ ] Add AUD.

  * cash asset

* [ ] Add EQ1.

  * fictional security

Engineering log:

* Record:

  * asset records
  * types
  * purpose in the demo

---

## 1.6.4 Seed accounts

* [ ] Create:

```text
Alice AUD
opening = 100000
current = 100000

Alice EQ1
opening = 0
current = 0

Bob AUD
opening = 0
current = 0

Bob EQ1
opening = 10
current = 10
```

Why these values?

They create the exact starting point required for the later example:

```text
Alice buys 10 EQ1 from Bob for AUD 500
```

Before settlement:

```text
Alice: AUD 1000, EQ1 0
Bob:   AUD 0,    EQ1 10
```

Engineering log:

* Record all four accounts and values.
* Record why these values were chosen.

---

## 1.6.5 Make seeding safe to repeat

* [ ] Run the seed once.
* [ ] Run it again.
* [ ] Confirm no duplicates appear.
* [ ] Confirm opening balances are not silently replaced.
* [ ] Modify a current balance in a disposable test.
* [ ] Rerun the seed.
* [ ] Confirm the seed does not reset that current balance.

Why:

Later, once settlements exist, running a seed script must not erase the financial history by resetting balances.

Verification:

* [ ] initial seed works
* [ ] second seed is safe
* [ ] conflicting/partial data does not get silently repaired
* [ ] existing financial state is preserved

Ready for 1.7 when:

* Alice/Bob/AUD/EQ1 data exists reliably
* seed operation is repeatable and non-destructive

---

# 1.7 Spring JDBC account read

Purpose:

Make the Java application read its first real domain data from PostgreSQL.

---

## 1.7.1 Create the Phase 1 domain records

What is a domain object?

* A domain object represents something meaningful in the problem the software is modelling.

Phase 1 needs:

```text
Participant
Asset
Account
```

* [ ] Create Java representations for:

  * Participant
  * Asset
  * AssetType
  * Account

* [ ] Keep them small.

* [ ] Do not add persistence annotations.

* [ ] Do not add future settlement behaviour.

Why:

* Java needs typed representations of rows returned from PostgreSQL.
* These classes should model the financial concepts, not database framework behaviour.

Engineering log:

* Record each domain type.

  * What it represents.
  * Important fields.
  * Why it exists.

---

## 1.7.2 Create `AccountRepository`

What is a repository?

* A repository contains the SQL/data-access operations for a domain area.
* It separates database querying from higher-level business logic.

The flow becomes:

```text
Java application
      |
      v
AccountRepository
      |
      v
NamedParameterJdbcTemplate
      |
      v
PostgreSQL
```

* [ ] Create `AccountRepository`.
* [ ] Inject `NamedParameterJdbcTemplate`.

What is `NamedParameterJdbcTemplate`?

* It is a Spring JDBC helper.
* It allows SQL parameters to be named rather than concatenated into SQL strings.

Example concept:

```sql
WHERE id = :accountId
```

instead of manually inserting the value into the SQL text.

Why:

* Explicit SQL remains visible.
* Parameter binding is safer than string concatenation.
* Spring handles repetitive JDBC plumbing.

Engineering log:

* Record:

  * repository created
  * dependency injected
  * why `NamedParameterJdbcTemplate` is used

---

## 1.7.3 Implement account reads

* [ ] Implement reading all accounts.
* [ ] Implement reading one account by ID.
* [ ] Use explicit selected columns.
* [ ] Map PostgreSQL rows into Java domain objects.
* [ ] Return absence cleanly for unknown IDs.

Do not add:

* account creation API
* account update API
* arbitrary balance modification
* locking methods
* settlement methods

Those belong to later phases.

Why:

* Phase 1 only needs to prove that application code can correctly read the foundational data.

Engineering log:

* Record:

  * SQL queries introduced
  * what each query returns
  * mapping from database columns to Java values

---

## 1.7.4 Verify account reads

Using the deterministic dataset:

* [ ] Read Alice AUD.

  * opening = 100000
  * current = 100000

* [ ] Read Alice EQ1.

  * opening = 0
  * current = 0

* [ ] Read Bob AUD.

  * opening = 0
  * current = 0

* [ ] Read Bob EQ1.

  * opening = 10
  * current = 10

* [ ] Verify participant information.

* [ ] Verify asset information.

* [ ] Verify unknown account ID returns no result.

* [ ] Verify reads do not modify any database state.

Ready for 1.8 when:

* Spring JDBC reads real PostgreSQL data correctly
* Java mappings are correct
* no financial mutation path exists

---

# 1.8 Testcontainers PostgreSQL integration testing

Purpose:

Automatically verify the database behaviour against real PostgreSQL rather than relying only on the developer's local database.

---

## 1.8.1 Configure PostgreSQL Testcontainers

What is Testcontainers?

* Testcontainers is a testing library that starts temporary Docker containers during tests.

For this project:

```text
test starts
    |
    v
temporary PostgreSQL starts
    |
    v
Spring connects
    |
    v
Flyway runs
    |
    v
test executes
    |
    v
temporary PostgreSQL is removed
```

* [ ] Add reusable PostgreSQL Testcontainers setup.
* [ ] Ensure the test database is separate from local development data.
* [ ] Do not silently skip tests when Docker is unavailable.

Why:

* Later features depend on real PostgreSQL:

  * transactions
  * row locks
  * rollback
  * database constraints
* H2 or mocks cannot reliably prove PostgreSQL-specific behaviour.

Engineering log:

* Record:

  * Testcontainers configuration
  * PostgreSQL image/version
  * container lifecycle
  * how Spring receives the connection information

---

## 1.8.2 Test Spring Boot startup against PostgreSQL

* [ ] Start PostgreSQL through Testcontainers.
* [ ] Start Spring Boot using that database.
* [ ] Confirm Spring JDBC can execute a query.

Why:

This verifies the complete basic integration:

```text
Spring Boot
    |
    v
Spring JDBC
    |
    v
PostgreSQL
```

Verification:

* [ ] Spring context starts.
* [ ] PostgreSQL query succeeds.

---

## 1.8.3 Test Flyway migration

* [ ] Start with a clean test database.
* [ ] Confirm Flyway applies V1.
* [ ] Confirm expected tables exist.
* [ ] Confirm migration validation passes.

Why:

* The repository must be able to recreate its own database automatically.

---

## 1.8.4 Test deterministic seed data

* [ ] Apply the seed to the test database.
* [ ] Verify the expected participants.
* [ ] Verify the expected assets.
* [ ] Verify the four accounts.
* [ ] Rerun the seed.
* [ ] Verify no reset or duplication occurs.

---

## 1.8.5 Test Spring JDBC account reads

* [ ] Read all four seeded accounts using `AccountRepository`.
* [ ] Verify fields and balances.
* [ ] Verify unknown account lookup.

This proves:

```text
Migration
    +
Seed
    +
Spring JDBC
    +
Java mapping
```

work together.

---

## 1.8.6 Test database constraints

* [ ] Attempt a duplicate participant/asset account.

  * Must fail.

* [ ] Attempt negative opening balance.

  * Must fail.

* [ ] Attempt negative current balance.

  * Must fail.

* [ ] Attempt account with unknown participant.

  * Must fail.

* [ ] Attempt account with unknown asset.

  * Must fail.

* [ ] Attempt duplicate asset code.

  * Must fail.

Why:

* Financial correctness should not depend only on Java code.
* PostgreSQL should reject invalid state even if application validation is bypassed.

Engineering log:

* Record every meaningful integration test.

  * What scenario it tests.
  * What guarantee it provides.
  * Result.

---

## 1.8.7 Run Maven verification

* [ ] Run:

```bash
./mvnw verify
```

* [ ] Confirm:

  * unit tests execute where present
  * integration tests execute
  * Testcontainers actually starts PostgreSQL
  * no required test is skipped
  * build succeeds

Ready for 1.9 when:

* complete automated Phase 1 test suite passes against real PostgreSQL

---

# 1.9 Phase 1 verification

Purpose:

Confirm the repository has a reliable foundation before any trade logic is introduced.

---

## 1.9.1 Run the complete build and test suite

* [ ] Run:

```bash
./mvnw clean verify
```

* [ ] Confirm the build passes from a clean Maven state.

Record:

```text
Java 21                    PASS / FAIL
Maven Wrapper              PASS / FAIL
Spring Boot startup        PASS / FAIL
PostgreSQL connection      PASS / FAIL
Flyway V1                  PASS / FAIL
Participant table          PASS / FAIL
Asset table                PASS / FAIL
Account table              PASS / FAIL
Seed data                  PASS / FAIL
Spring JDBC account read   PASS / FAIL
Testcontainers             PASS / FAIL
Database constraints       PASS / FAIL
Maven verify               PASS / FAIL
```

---

## 1.9.2 Review the repository

* [ ] Inspect `git status`.
* [ ] Inspect new/modified files.
* [ ] Confirm no generated build output is tracked.
* [ ] Confirm no `.env` or credentials are tracked.
* [ ] Confirm no unnecessary dependencies were introduced.
* [ ] Confirm no speculative future packages/classes were created.
* [ ] Confirm no prohibited technology was introduced.

---

## 1.9.3 Review the engineering log

* [ ] Confirm the log contains:

  * 1.1 repository setup
  * 1.2 Java/Maven
  * 1.3 Spring Boot
  * 1.4 PostgreSQL/configuration
  * 1.5 Flyway/schema
  * 1.6 seed data
  * 1.7 Spring JDBC
  * 1.8 Testcontainers/testing

* [ ] Confirm each meaningful action records:

  * what happened
  * why
  * files involved
  * important configuration
  * command/test used
  * result
  * failures/fixes where relevant

* [ ] Append the Phase 1 final verification result.

---

## 1.9.4 Review the final repository structure

Expected shape is approximately:

```text
dvp-settlement-engine/
├── .mvn/
├── docs/
│   ├── project-spec.md
│   ├── decisions.md
│   ├── implementation-plan.md
│   ├── detailed-plan/
│   │   └── phase-1.md
│   └── engineering-log.md
├── scripts/
│   └── seed-demo.sql
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── jasonwidjaja/
│   │   │           └── dvp/
│   │   │               ├── DvpApplication.java
│   │   │               └── account/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   │               └── V1__participants_assets_accounts.sql
│   └── test/
│       └── java/
│           └── com/
│               └── jasonwidjaja/
│                   └── dvp/
├── .env.example
├── .gitignore
├── AGENTS.md
├── pom.xml
├── mvnw
└── mvnw.cmd
```

A PostgreSQL-only `compose.yaml` may also exist if local development requires it.

The exact structure may differ where technically justified.

Any meaningful deviation should be recorded in the engineering log.

---

## 1.9.5 Confirm Phase 1 exit criteria

Phase 1 is complete only when:

* [ ] Java 21 is configured and working.
* [ ] Maven Wrapper works.
* [ ] Spring Boot application starts.
* [ ] PostgreSQL runs and accepts connections.
* [ ] Spring Boot connects to PostgreSQL.
* [ ] Flyway creates V1 successfully.
* [ ] participant, asset and account tables exist.
* [ ] database constraints protect invalid account state.
* [ ] deterministic Alice/Bob seed data works.
* [ ] seed reruns do not reset financial state.
* [ ] Spring JDBC reads account data correctly.
* [ ] Testcontainers runs integration tests against real PostgreSQL.
* [ ] `./mvnw clean verify` passes.
* [ ] no secrets or generated build output are committed.
* [ ] engineering log accurately describes the implementation.

Then stop.

Do not implement Trade Capture.

The next project action is:

```text
inspect completed Phase 1 repository
        |
        v
prepare docs/detailed-plan/phase-2.md
        |
        v
review Phase 2
        |
        v
implement Phase 2
```
