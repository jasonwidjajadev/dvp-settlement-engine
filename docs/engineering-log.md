# Engineering Log

This file records what actually happened during implementation. It uses the same numbering as `docs/detailed-plan/phase-N.md`.

# Phase 1: Java and PostgreSQL Foundation

## 1.1 Repository setup

### 1.1.1 Inspect the current repository

- Inspected the complete repository tree with `find . -not -path './.git/*' | sort`.
  - Existing directories:
    - `.git/`
    - `docs/`
    - `docs/detailed-plan/`
  - Existing important files:
    - `.gitignore`
    - `README.md`
    - `AGENTS.md`
    - `docs/project-spec.md`
    - `docs/decisions.md`
    - `docs/implementation-plan.md`
    - `docs/detailed-plan/phase-1.md`
  - No Java source tree existed.
  - No Maven project files existed (`pom.xml`, `mvnw`, `.mvn/` were absent).
  - No `src/` directory existed.

- Ran `git status` and `git ls-files`.
  - Branch: `main`, up to date with `origin/main`.
  - Tracked files: `.gitignore`, `README.md`.
  - Modified (unstaged):
    - `.gitignore` (added `.DS_Store`)
    - `README.md` (trailing newline removed; content otherwise unchanged)
  - Untracked:
    - `AGENTS.md`
    - `docs/decisions.md`
    - `docs/detailed-plan/phase-1.md`
    - `docs/implementation-plan.md`
    - `docs/project-spec.md`
  - Staged files: none.
  - Untracked local files also present: `.DS_Store`, `docs/.DS_Store`.

- Recorded this as the Phase 1 baseline.
  - The repository was not empty.
  - Planning documents and `AGENTS.md` already existed as untracked work.
  - Existing files were preserved.

### 1.1.2 Read the authoritative project documents

- Read `docs/project-spec.md`.
  - Product is a locally runnable Java/Spring Boot REST backend on PostgreSQL.
  - Core features are trade capture, atomic DvP settlement, journals, idempotency, concurrency, failure recovery, reconciliation and a small load experiment.
  - Stack includes Java 21, Spring Boot, Spring MVC, Spring JDBC, PostgreSQL, Flyway, JUnit, AssertJ and Testcontainers.
  - Persistence is explicit Spring JDBC, not JPA/Hibernate.
  - Non-goals include Kafka, Kubernetes, microservices, event sourcing, distributed database transactions and a large frontend.

- Read `docs/decisions.md`.
  - Confirmed accepted decisions: Java 21 + Spring Boot, Spring JDBC, PostgreSQL, one modular application, gross trade-by-trade settlement, one transaction for both asset legs, deterministic row locking, immutable journal plus current balances, command idempotency, reconciliation against saved inputs, no Kafka in the core project, optional outbox only after core completion.

- Read `docs/implementation-plan.md`.
  - Phase 1 is the Java and PostgreSQL foundation.
  - Later phases are trade capture, atomic settlement, concurrency/retry/recovery, reconciliation, then load experiment and polish.
  - Section 1.1 of this phase must not begin Phase 2 or later Phase 1 application work.

- Read `docs/detailed-plan/phase-1.md`.
  - Followed Section 1.1 numbering for implementation and logging.
  - Did not begin Section 1.2.

- Conflicts and ambiguities:
  - No product or architecture conflict was found among the spec, decisions, implementation plan and Phase 1 detailed plan.
  - Open contract C1 (numeric representation) remains unresolved and is scheduled before the Phase 1 database schema is finalized. It is not required for Section 1.1.
  - `docs/implementation-plan.md` Current State still says the next planning artifact is `docs/detailed-plan/phase-1.md`, but that file already exists. This is stale planning status, not a behavioural conflict. The file was left unchanged.

### 1.1.3 Create or update `AGENTS.md`

- `AGENTS.md` already existed as untracked work.
- Reviewed it against the Section 1.1.3 checklist.
  - Authoritative documents are recorded.
  - Approved stack is recorded: Java 21, Spring Boot, Spring MVC, Spring JDBC, PostgreSQL, Flyway, JUnit, AssertJ, Testcontainers.
  - Project namespace is recorded: group ID `com.jasonwidjaja`, artifact ID `dvp-settlement-engine`, base package `com.jasonwidjaja.dvp`.
  - Restrictions are recorded: no JPA/Hibernate, no Kafka in the core project, no microservices, no frontend, no silent scope or architecture changes.
  - Phase workflow is recorded: inspect, read documents, review detailed plan, implement current phase, verify, update this log, stop before the next phase.
- No update was required.
  - Existing instructions already matched the approved documents.
  - The file was preserved without modification.

### 1.1.4 Create the engineering log

- Created `docs/engineering-log.md`.
  - Started with Phase 1, Step 1.1.
  - Uses the same numbering as `docs/detailed-plan/phase-1.md`.
  - Records actual work rather than restating the detailed plan.

### 1.1.5 Review `.gitignore`

- Inspected the existing `.gitignore`.
  - Originated from a basic Java GitHub template.
  - Already ignored compiled classes, logs, some package archives, JVM crash logs and `.DS_Store`.
  - Did not ignore Maven `target/`.
  - Did not ignore local `.env` files.
  - Existing `*.jar` rule would later ignore `.mvn/wrapper/maven-wrapper.jar`.

- Updated `.gitignore` while keeping the existing useful rules.
  - Added `target/` so Maven build output is not tracked.
  - Added `!.mvn/wrapper/maven-wrapper.jar` so the later Maven Wrapper jar remains committable despite `*.jar`.
  - Added `.env`, `.env.*` and `!.env.example` so local credentials are not committed.
  - Preserved `.DS_Store`.
  - Did not replace the file with a generic template.
  - Did not add unused IDE ignore rules.

- Verification:
  - `target/` is ignored.
  - `.env` is ignored.
  - Existing useful ignore rules remain.

### 1.1.6 Verify repository preparation

- Re-inspected the repository after Section 1.1 work.
- Confirmed:
  - Project documents exist under `docs/`.
  - `AGENTS.md` exists.
  - `docs/engineering-log.md` exists.
  - `.gitignore` ignores Maven output and local environment files.
  - No Java application has been created yet.
  - No existing planning or README work was lost.
- Resulting repository tree after Section 1.1:

```text
.
├── .gitignore
├── AGENTS.md
├── README.md
└── docs
    ├── decisions.md
    ├── detailed-plan
    │   └── phase-1.md
    ├── engineering-log.md
    ├── implementation-plan.md
    └── project-spec.md
```

- Ready for Section 1.2.
  - Repository state is understood.
  - Working instructions exist.
  - Logging exists.
  - Git exclusions are correct.
  - Existing work is preserved.
- Stopped here. Section 1.2 was not started.

## 1.2 Java 21 and Maven foundation

### 1.2.1 Verify Java 21

- Ran `java -version` outside the sandbox.
  - The sandboxed shell could not see a JDK (`Unable to locate a Java Runtime`).
  - On the host, Java 21 is installed and active.
- Recorded version:
  - Vendor: Eclipse Adoptium (Temurin)
  - Runtime: OpenJDK 21.0.3 LTS (`Temurin-21.0.3+9`)
  - Home: `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
  - `javac 21.0.3`
- `JAVA_HOME` was unset in the shell. Later Maven commands set it explicitly so Homebrew OpenJDK 23 was not used.
- Result: Java 21 is active.

### 1.2.2 Create and configure `pom.xml`

- Added root `pom.xml`.
  - Group ID: `com.jasonwidjaja`
  - Artifact ID: `dvp-settlement-engine`
  - Version: `0.0.1-SNAPSHOT`
  - Java version: 21
  - Parent: `spring-boot-starter-parent` 4.1.1
    - Current stable Spring Boot at the time of this step (released 20 August 2026).
    - The detailed plan did not pin a Boot version.

- Application dependencies:
  - `spring-boot-starter-webmvc`
    - Spring Boot 4's Spring MVC starter.
    - Provides the HTTP/REST stack later phases will use.
  - `spring-boot-starter-jdbc`
    - Provides Spring JDBC, including `NamedParameterJdbcTemplate`.
    - Chosen instead of JPA/Hibernate ORM.
  - `spring-boot-starter-validation`
    - Provides Jakarta Bean Validation.
    - Required by the Phase 1 foundation list.
    - Transitively includes Hibernate Validator, which is a validation library, not Hibernate ORM.
  - `org.postgresql:postgresql`
    - PostgreSQL JDBC driver.
    - Runtime scope.
  - `flyway-core` and `flyway-database-postgresql`
    - Versioned PostgreSQL migrations.
    - The PostgreSQL Flyway module is required by Flyway 12.

- Test dependencies:
  - `spring-boot-starter-test`
    - Brings JUnit Jupiter and AssertJ, which the plan lists separately.
  - `testcontainers-junit-jupiter` and `testcontainers-postgresql`
    - Testcontainers 2 artifact names managed by Spring Boot 4.1.1.

- Did not add JPA, Hibernate ORM, H2, Kafka, or other future dependencies.
- Added `spring-boot-maven-plugin` so later executable-jar packaging is available.

### 1.2.3 Add Maven Wrapper

- Host `mvn` was not installed.
- Downloaded a temporary Apache Maven 3.9.11 distribution only to generate the wrapper.
- Ran `mvn -N org.apache.maven.plugins:maven-wrapper-plugin:3.3.2:wrapper -Dmaven=3.9.11`.
- Wrapper files added:
  - `mvnw`
  - `mvnw.cmd`
  - `.mvn/wrapper/maven-wrapper.properties`
- Wrapper type is `only-script`.
  - No `maven-wrapper.jar` was committed.
  - The first `./mvnw` run downloads Maven 3.9.11.
- Made `mvnw` executable with `chmod +x mvnw`.
- The wrapper is committed so later work can use a known Maven version without a local Maven install.

- Ran `./mvnw -v`.
  - Maven 3.9.11
  - Java 21.0.3, Eclipse Adoptium
  - Result: passed

### 1.2.4 Verify the Maven foundation

- Ran `./mvnw validate`.
  - Purpose: confirm Maven accepts `pom.xml`.
  - Result: passed.

- Ran `./mvnw dependency:tree`.
  - Purpose: confirm Phase 1 dependencies resolve and inspect the graph.
  - Result: passed.
  - Confirmed present: Spring MVC, Spring JDBC, validation, PostgreSQL driver, Flyway, JUnit Jupiter, AssertJ, Testcontainers PostgreSQL.
  - Confirmed absent: JPA, Hibernate ORM, H2, Kafka.
  - Hibernate Validator appeared under the validation starter only.

- Ran `./mvnw verify`.
  - Purpose: run the repository's standard accumulated check after Maven exists.
  - Result: failed.
  - Error: `spring-boot-maven-plugin:4.1.1:repackage` / `Unable to find main class`.
  - Cause: no Java sources exist yet. `DvpApplication` belongs to Section 1.3.
  - Did not add a dummy main class or skip-repackage configuration. That would start 1.3 or change later packaging behaviour.

- Ready for Section 1.3.
  - Java 21 works.
  - `pom.xml` is valid.
  - Maven Wrapper works.
  - Phase 1 dependencies resolve.
- Stopped here. Section 1.3 was not started.

## 1.3 Spring Boot application skeleton

### 1.3.1 Create the Java source structure

- Created the standard Maven directories:
  - `src/main/java` — production Java source.
  - `src/main/resources` — later configuration and SQL migrations.
  - `src/test/java` — automated tests.
- Added `src/main/resources/.gitkeep` so Git keeps the empty resources directory.
- Did not add `application.yml` or Flyway files. Those belong to later Phase 1 sections.
- Verified with `find src -print`. All three directories exist.

### 1.3.2 Create the base Java package

- Created `src/main/java/com/jasonwidjaja/dvp/`.
  - Package: `com.jasonwidjaja.dvp`
  - Matches the approved namespace and reverse-domain of `jasonwidjaja.com`.
- Did not create empty feature packages such as `account`.
- Mirrored the package in tests when the context-load test was added in 1.3.4.
- Verified the directory path matches `com.jasonwidjaja.dvp`.

### 1.3.3 Create `DvpApplication.java`

- Added `src/main/java/com/jasonwidjaja/dvp/DvpApplication.java`.
  - `main` is the JVM entry point and calls `SpringApplication.run`.
  - `@SpringBootApplication` enables auto-configuration and component scanning from `com.jasonwidjaja.dvp`.
- Kept the class minimal. No business logic, endpoints, or database configuration.

- Ran `./mvnw compile`.
  - Purpose: confirm the entry point compiles.
  - Result: passed. Produced `target/classes/com/jasonwidjaja/dvp/DvpApplication.class`.

- Ran `./mvnw spring-boot:run`.
  - Purpose: confirm a default start.
  - Result: failed.
  - Error: `Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.`
  - Cause: JDBC and Flyway are already on the classpath from Section 1.2, but PostgreSQL configuration belongs to Section 1.4.
  - Did not add H2, `application.yml`, or production auto-configuration excludes.

### 1.3.4 Verify the Spring Boot skeleton

- Added `src/test/java/com/jasonwidjaja/dvp/DvpApplicationTests.java`.
  - Mirrors the production package.
  - Loads the Spring context while excluding DataSource and Flyway auto-configuration.
  - Asserts the context starts, `DvpApplication` is present, and no `@RestController` beans exist.

- Ran `./mvnw test`.
  - Purpose: confirm the Spring context can start without PostgreSQL.
  - Result: passed. `Started DvpApplicationTests in 1.458 seconds`. Tests run: 1.

- Ran `./mvnw verify`.
  - Purpose: confirm the earlier missing-main-class repackage failure is resolved.
  - Result: passed. Spring Boot repackaged the executable jar.

- Ran `./mvnw spring-boot:run` with DataSource and Flyway auto-configuration excluded.
  - Purpose: confirm the web server starts before Section 1.4 configures PostgreSQL.
  - Result: passed. `Started DvpApplication`.
  - `GET /` returned HTTP 404 with no application endpoints.
  - Stopped the process after the check.

- Confirmed:
  - Production Java files: only `DvpApplication.java`.
  - No controllers, trade/settlement code, `application.yml`, migrations, Compose files, or `.env`.

- Ready for Section 1.4.
  - Java structure exists.
  - Base package exists.
  - Spring Boot entry point exists.
  - Application compiles and the context/web server start when database auto-configuration is deferred.
- Stopped here. Section 1.4 was not started.

## 1.4 PostgreSQL and application configuration

### 1.4.1 Establish PostgreSQL for local development

- Inspected the machine for an existing PostgreSQL instance.
  - `psql` was not installed.
  - Nothing was listening on port 5432.
  - Homebrew PostgreSQL packages were not present.
  - Docker Compose v5.5.0 was installed, but the Docker daemon was not running.

- Started Docker Desktop, then used a PostgreSQL-only Compose service.
  - Added `compose.yaml` with a single `postgres` service.
  - Image: `postgres:16`
  - Container: `dvp-postgres`
  - Database/user names come from `.env`.
  - Did not add a Spring Boot service.

- Ran `docker compose --env-file .env up -d`.
  - First run pulled `postgres:16`.
  - A health-wait script failed because zsh treats `status` as a read-only variable. The container itself started.
  - Rechecked health with a different variable name.

- Verification:
  - PostgreSQL accepted connections.
  - Version: PostgreSQL 16.15.
  - `\dt` showed no relations.

### 1.4.2 Create Spring application configuration

- Added `src/main/resources/application.yml`.
  - `spring.application.name` = `dvp-settlement-engine`
  - `spring.datasource.url/username/password` read from environment variables.
  - Non-secret defaults are `jdbc:postgresql://localhost:5432/dvp` and username `dvp`.
  - Password has an empty default so tests that do not connect can still resolve the file.
  - `spring.flyway.enabled` = `false` so Flyway does not create `flyway_schema_history` before Section 1.5.
- Removed `src/main/resources/.gitkeep` because `application.yml` now tracks the directory.
- No secrets were placed in the YAML file.

### 1.4.3 Configure environment variables

- Added `.env.example` with placeholders:
  - `POSTGRES_DB` — Compose database name
  - `POSTGRES_USER` — Compose database user
  - `POSTGRES_PASSWORD` — Compose password placeholder
  - `POSTGRES_PORT` — host port
  - `SPRING_DATASOURCE_URL` — JDBC URL
  - `SPRING_DATASOURCE_USERNAME` — application user
  - `SPRING_DATASOURCE_PASSWORD` — application password placeholder
- Created a local `.env` with generated credentials. The actual password is not recorded here.
- Confirmed `.env` is ignored by Git (`gitignore` rule `.env`).
- Confirmed `.env.example` is not ignored.
- Spring Boot does not load `.env` automatically. Local runs export the file first:
  - `set -a && source .env && set +a && ./mvnw spring-boot:run`

### 1.4.4 Configure Spring JDBC

- Used Spring Boot's default JDBC auto-configuration.
  - `DataSource` is a HikariCP pool created from `spring.datasource.*`.
  - `NamedParameterJdbcTemplate` is provided by `spring-boot-starter-jdbc`.
  - No JPA, Hibernate ORM, or custom `DataSource` bean was added.

- Added `src/test/java/com/jasonwidjaja/dvp/JdbcConnectionVerification.java`.
  - Runs only when `DVP_VERIFY_JDBC=true`.
  - Executes `SELECT 1` through `NamedParameterJdbcTemplate`.

- Ran `./mvnw test`.
  - Purpose: confirm the existing context test still passes without PostgreSQL.
  - Result: passed.

- Ran `./mvnw -Dtest=JdbcConnectionVerification test` with `.env` exported and `DVP_VERIFY_JDBC=true`.
  - Purpose: confirm Spring JDBC can query the real PostgreSQL database.
  - Result: passed.
  - Hikari opened `org.postgresql.jdbc.PgConnection`.
  - `SELECT 1` returned 1.

### 1.4.5 Verify PostgreSQL integration

- PostgreSQL container `dvp-postgres` was healthy.
- Ran `./mvnw spring-boot:run` after exporting `.env`.
  - Result: `Started DvpApplication`.
  - No missing-DataSource failure from Section 1.3.
  - The idle process did not keep an open PostgreSQL session until SQL ran. Connection was proven by the JDBC verification above, not by an idle `pg_stat_activity` row.
- After startup, `\dt` still showed no relations.
  - No Flyway history table.
  - No participant/asset/account schema.
- Stopped the application after the check.

- Ready for Section 1.5.
  - PostgreSQL runs.
  - Environment configuration works.
  - Spring Boot connects through JDBC.
- Stopped here. Section 1.5 was not started.

### 1.4 correction: PostgreSQL 18

- Section 1.4 originally used `postgres:16`. That did not match the required PostgreSQL 18 version.
- Changed `compose.yaml` image from `postgres:16` to `postgres:18`.
- Recreated the development database so no PostgreSQL 16 data directory remained:
  - `docker compose --env-file .env down -v`
  - `docker compose --env-file .env pull`
  - `docker compose --env-file .env up -d`
- Verification:
  - Container image: `postgres:18`
  - Server version: PostgreSQL 18.6
  - `\dt` showed no tables. The database is empty.
- Reran Section 1.4 JDBC verification:
  - `./mvnw -Dtest=JdbcConnectionVerification test` with `.env` exported and `DVP_VERIFY_JDBC=true`
  - Result: passed
  - Hikari opened `org.postgresql.jdbc.PgConnection`
  - `SELECT 1` returned 1
- Did not begin Section 1.5.

## 1.5 Flyway and V1 database schema

### 1.5.1 Set up Flyway migrations

- Created `src/main/resources/db/migration/`.
- Created `V1__participants_assets_accounts.sql`.
  - Version 1 schema for participant, asset, and account only.
- Enabled Flyway in `src/main/resources/application.yml`.
  - `spring.flyway.enabled=true`
  - `spring.flyway.locations=classpath:db/migration`

- First apply against a fresh PostgreSQL 18 database did not create tables.
  - Cause: Spring Boot 4 does not auto-configure Flyway from `flyway-core` alone.
  - `FlywayAutoConfiguration` lives in `spring-boot-flyway`, pulled in by `spring-boot-starter-flyway`.
- Replaced the `flyway-core` dependency with `spring-boot-starter-flyway`.
  - Kept `flyway-database-postgresql` for PostgreSQL 18.
- Recreated the empty database and applied V1 again.
  - Result: Flyway migrated schema `public` to version 1.

### 1.5.2 Define the Phase 1 numeric representation

- AUD is stored as integer minor units.
  - `100000` = AUD 1,000.
- Securities are stored as whole units.
  - `10` = 10 EQ1.
- PostgreSQL type: `BIGINT`.
- Corresponding Java type later: `long`.
- Floating-point types are not used.
- Allowed balance range: `0` through `BIGINT` maximum.
- Zero balances are allowed. Zero-value trades remain a later Phase 2 question from C1.

### 1.5.3 Create the participant table

- Table: `participant`
  - `id UUID PRIMARY KEY` — stable identifier, not the display name.
  - `name TEXT NOT NULL` — display name.
  - `participant_name_not_blank` — rejects blank names.
- Verification:
  - Valid insert succeeded.
  - Blank name was rejected.

### 1.5.4 Create the asset table

- Table: `asset`
  - `id UUID PRIMARY KEY`
  - `code TEXT NOT NULL` with unique constraint
  - `type TEXT NOT NULL` restricted to `CASH` or `SECURITY`
- Verification:
  - AUD/`CASH` inserted.
  - EQ1/`SECURITY` inserted.
  - Duplicate `AUD` code was rejected.
  - Type `CRYPTO` was rejected.

### 1.5.5 Create the account table

- Table: `account`
  - `id UUID PRIMARY KEY`
  - `participant_id` references `participant(id)`
  - `asset_id` references `asset(id)`
  - `opening_balance BIGINT NOT NULL CHECK (>= 0)` — initial holdings.
  - `current_balance BIGINT NOT NULL CHECK (>= 0)` — latest holdings.
  - Unique `(participant_id, asset_id)` — one account per pair.
- Two balances exist so later reconstruction can check `current = opening + committed postings`.
- Verification:
  - Valid account inserted (`100000` / `100000`).
  - Duplicate participant/asset pair was rejected.
  - Negative opening and current balances were rejected.
  - Unknown participant and unknown asset were rejected.

### 1.5.6 Verify Flyway V1

- Started from a fresh PostgreSQL 18 database (`docker compose --env-file .env down -v` then `up -d`).
- Applied V1 through Spring Boot / Flyway.
  - History row: `1 / participants assets accounts / V1__participants_assets_accounts.sql / success`
  - Tables present: `participant`, `asset`, `account`, `flyway_schema_history`
  - No trade, settlement, journal, or reconciliation tables.
- Ran Flyway again.
  - Log: `Schema "public" is up to date. No migration necessary.`
  - History still has exactly one V1 row.
  - Existing verification rows were unchanged.
- Deleted the disposable constraint-check rows so the schema remains empty of demo data.
  - Alice/Bob were never seeded.
- `./mvnw test` still passed without requiring the JDBC verification flag.

- Ready for Section 1.6.
  - V1 creates the Phase 1 schema.
  - Constraints work.
  - Migration is repeatable.
- Stopped here. Section 1.6 was not started.

## 1.6 Deterministic demo data

### 1.6.1 Create the seed mechanism

- Added `scripts/seed-demo.sql`.
  - Canonical seed for local development and demos.
  - Kept out of `src/main/resources/db/migration/` so Flyway V1 still creates structure only.
  - Not referenced by `application.yml`, so the application does not seed on startup.
- Invocation:

```bash
docker exec -i dvp-postgres psql -U dvp -d dvp < scripts/seed-demo.sql
```

- Chose a standalone SQL file rather than a Java CommandLineRunner or a Flyway repeatable migration.
  - It matches the Phase 1 plan.
  - It can be run only when a demo database is wanted.
  - PostgreSQL `ON CONFLICT` can make reruns non-destructive without application code.

### 1.6.2 Seed participants

- Inserted deterministic participants:
  - Alice `00000000-0000-0000-0000-000000000001`
  - Bob `00000000-0000-0000-0000-000000000002`
- Fixed UUIDs so later tests and demo scripts can refer to the same rows.
- `ON CONFLICT (id) DO NOTHING` skips Alice/Bob if they already exist.

### 1.6.3 Seed assets

- Inserted deterministic assets:
  - AUD `00000000-0000-0000-0000-0000000000a1`, type `CASH`
  - EQ1 `00000000-0000-0000-0000-0000000000e1`, type `SECURITY`
- AUD is Alice's cash for the later AUD 500 purchase.
- EQ1 is Bob's security inventory of 10 units.
- `ON CONFLICT (id) DO NOTHING` skips existing assets.

### 1.6.4 Seed accounts

- Inserted the four starting accounts:

| Account | ID | Opening | Current |
| --- | --- | ---: | ---: |
| Alice AUD | `00000000-0000-0000-0000-0000000000aa` | 100000 | 100000 |
| Alice EQ1 | `00000000-0000-0000-0000-0000000000ae` | 0 | 0 |
| Bob AUD | `00000000-0000-0000-0000-0000000000ba` | 0 | 0 |
| Bob EQ1 | `00000000-0000-0000-0000-0000000000be` | 10 | 10 |

- These values are the later example starting point: Alice has AUD 1,000 and no EQ1; Bob has 10 EQ1 and no cash.
- `ON CONFLICT (participant_id, asset_id) DO NOTHING` so a rerun cannot create a second pair or overwrite balances.

### 1.6.5 Make seeding safe to repeat

- Verified against PostgreSQL 18.6 (`postgres:18`).
- First run inserted 2 participants, 2 assets, and 4 accounts.
- Second run reported `INSERT 0 0` for all three statements.
  - Counts remained 2 / 2 / 4.
  - Opening balances were unchanged.
- Changed Alice AUD `current_balance` to `50000` as a disposable test.
- Third run still reported `INSERT 0 0`.
  - Alice AUD remained `opening 100000`, `current 50000`.
  - The seed did not reset the changed current balance.
- Restored Alice AUD `current_balance` to `100000` after that check so the local database is back to the canonical demo state.
- No trade or other Phase 2 tables were created.

- Ready for Section 1.7.
  - Alice/Bob/AUD/EQ1 data exists.
  - Seed is repeatable and non-destructive.
- Stopped here. Section 1.7 was not started.

## 1.7 Spring JDBC account read

### 1.7.1 Create the Phase 1 domain records

- Added Java records and an enum under `com.jasonwidjaja.dvp.domain`.
  - These packages were created now because Section 1.7 needs typed row mappings. Empty future packages were not added.
- `Participant`
  - Represents a simulated trading party such as Alice or Bob.
  - Fields: `id` (`UUID`), `name`.
- `AssetType`
  - Enum: `CASH`, `SECURITY`.
  - Matches the V1 `asset_type_supported` values.
- `Asset`
  - Represents AUD cash or a fictional security such as EQ1.
  - Fields: `id` (`UUID`), `code`, `type` (`AssetType`).
- `Account`
  - Represents one participant's holdings of one asset.
  - Fields: `id`, nested `participant`, nested `asset`, `openingBalance`, `currentBalance`.
  - Balances are `long` to match PostgreSQL `BIGINT` minor/whole units from Section 1.5.2.
- No JPA/Hibernate annotations.
- No settlement behaviour, locking, or mutation methods.
- Ran `./mvnw compile`.
  - Purpose: confirm the domain types compile on Java 21.
  - Result: passed.

### 1.7.2 Create `AccountRepository`

- Added `com.jasonwidjaja.dvp.persistence.AccountRepository`.
  - Persistence package exists because this phase needs JDBC access for accounts.
  - `@Repository` with constructor injection of `NamedParameterJdbcTemplate`.
- `NamedParameterJdbcTemplate` is used so SQL stays explicit and parameters are bound by name, for example `:accountId`, instead of string concatenation.
- First attempt annotated the scanned repository with `@ConditionalOnBean(DataSource.class)` so the existing no-database context test would still start.
  - Result: failed during 1.7.4.
  - Cause: `@ConditionalOnBean` on a component-scanned class is evaluated before the DataSource bean exists, so Spring skipped `AccountRepository` even when PostgreSQL was connected.
  - Fix: removed the condition. The repository is always a Spring bean.
  - `DvpApplicationTests` now registers a `@MockitoBean NamedParameterJdbcTemplate` so the DataSource-excluded context can still construct the repository.

### 1.7.3 Implement account reads

- Added two SELECT-only methods:
  - `findAll()` — every account joined to its participant and asset, ordered by `account.id`.
  - `findById(UUID)` — the same columns for one account, returning `Optional.empty()` when no row exists.
- Selected columns are explicit:
  - `account.id`, `opening_balance`, `current_balance`
  - `participant.id`, `participant.name`
  - `asset.id`, `asset.code`, `asset.type`
- Mapping:
  - `participant_id` / `participant_name` → `Participant`
  - `asset_id` / `asset_code` / `asset_type` → `Asset` / `AssetType.valueOf`
  - `account_id` / balances → `Account`
- Did not add create, update, balance-modification, locking, or settlement methods.

### 1.7.4 Verify account reads

- Added `AccountReadVerification`, gated by `DVP_VERIFY_JDBC=true`.
  - Uses the local seeded PostgreSQL database.
  - Does not start Testcontainers. That belongs to Section 1.8.
- Ran `./mvnw test`.
  - Purpose: confirm the no-database context test still passes.
  - Result: passed. Tests run: 1.
- Ran `./mvnw -Dtest=AccountReadVerification test` with `.env` exported and `DVP_VERIFY_JDBC=true`.
  - Purpose: read the deterministic demo accounts through Spring JDBC.
  - PostgreSQL: 18.6, Flyway schema still version 1, no new migration.
  - First gated run failed: `AccountRepository` bean missing because of the `@ConditionalOnBean` issue above.
  - After removing the condition, the same command passed.
- Assertions that passed:
  - Alice AUD: opening 100000, current 100000, participant Alice, asset AUD/`CASH`
  - Alice EQ1: opening 0, current 0, asset EQ1/`SECURITY`
  - Bob AUD: opening 0, current 0, participant Bob, asset AUD/`CASH`
  - Bob EQ1: opening 10, current 10, asset EQ1/`SECURITY`
  - `findAll()` returned those four IDs
  - unknown account ID returned empty
  - opening/current balances and participant/asset/account counts were unchanged after the reads

- Ready for Section 1.8.
  - Spring JDBC reads real PostgreSQL data.
  - Java mappings match the seed.
  - No financial mutation path exists.
- Stopped here. Section 1.8 was not started.

## 1.8 Testcontainers PostgreSQL integration testing

### 1.8.1 Configure PostgreSQL Testcontainers

- Added reusable test support under `com.jasonwidjaja.dvp.support`.
  - `PostgresTestDatabase` starts one `org.testcontainers.postgresql.PostgreSQLContainer` for the JVM.
  - Image: `postgres:18`, same family as local Compose.
  - The container starts in a static initializer. If Docker is unavailable, class loading fails the tests instead of skipping them.
  - `AbstractPostgresIntegrationTest` is the shared `@SpringBootTest` base.
  - `@DynamicPropertySource` sets `spring.datasource.url`, `username`, and `password` from the container.
  - Precedence is higher than OS environment variables, so a local `.env` pointing at `dvp-postgres` is not used.
- Did not add `spring-boot-testcontainers` or `@ServiceConnection`.
  - `DynamicPropertySource` already makes the JDBC settings explicit.
- Container lifecycle:
  - started once when the first integration test loads `PostgresTestDatabase`
  - database name is Testcontainers `test`, not local `dvp`
  - Ryuk removes the container when the JVM exits
- Did not enable `disabledWithoutDocker`.

### 1.8.2 Test Spring Boot startup against PostgreSQL

- Added `PostgresStartupIntegrationTest.springStartsAgainstTestcontainersPostgreSQL`.
  - Scenario: Spring Boot starts against the temporary PostgreSQL container.
  - Guarantee: the application context and `NamedParameterJdbcTemplate` work without the local `dvp` database.
  - Result: passed.
  - JDBC URL was `jdbc:postgresql://localhost:<ephemeral>/test`, not `localhost:5432/dvp`.
  - `SELECT 1` returned 1.
  - `SHOW server_version` started with `18` (18.6).

### 1.8.3 Test Flyway migration

- Added `PostgresStartupIntegrationTest.flywayAppliesV1ToCleanTestDatabase`.
  - Scenario: first connection to an empty Testcontainers database.
  - Guarantee: the repository can recreate the Phase 1 schema automatically.
  - Result: passed.
  - Flyway log: schema empty, then migrated to version 1.
  - `flyway.validate()` succeeded.
  - Current migration: `1` / `V1__participants_assets_accounts.sql`.
  - Public tables: `account`, `asset`, `flyway_schema_history`, `participant`.
  - No trade, settlement, journal, or reconciliation tables.

### 1.8.4 Test deterministic seed data

- Added `DemoSeed` to apply the real `scripts/seed-demo.sql` through `ResourceDatabasePopulator`.
  - The seed stays out of Flyway and is not copied into test resources.
- Added `DemoSeedIntegrationTest.seedIsDeterministicAndSafeToRerun`.
  - Scenario: apply the seed, change Alice AUD current to 50000, apply the seed again.
  - Guarantee: deterministic Alice/Bob/AUD/EQ1 rows exist, reruns do not duplicate them, and a changed current balance is not reset.
  - Result: passed after one assertion fix (below).
- First `./mvnw verify` failed this test.
  - Expected 2 participants, found 3.
  - Cause: constraint tests share the same container and insert a `Constraint` / `C1` fixture.
  - Fix: assert the deterministic IDs exist exactly once, instead of requiring the tables to contain only seed rows.

### 1.8.5 Test Spring JDBC account reads

- Added `DemoSeedIntegrationTest.accountRepositoryReadsSeededAccounts`.
  - Scenario: `AccountRepository` reads the seeded accounts from Testcontainers PostgreSQL.
  - Guarantee: Flyway + seed + Spring JDBC + Java mapping work together.
  - Result: passed.
  - Alice AUD 100000/100000, Alice EQ1 0/0, Bob AUD 0/0, Bob EQ1 10/10.
  - Unknown account ID returned empty.

### 1.8.6 Test database constraints

- Added `DatabaseConstraintIntegrationTest`.
  - Inserts invalid rows through JDBC so PostgreSQL is tested without application validation.
  - Each failure is a `DataIntegrityViolationException` whose root `PSQLException` names the constraint.

| Test | Guarantee | Result |
| --- | --- | --- |
| duplicate participant/asset account | `account_participant_asset_unique` | passed |
| negative opening balance | `account_opening_balance_non_negative` | passed |
| negative current balance | `account_current_balance_non_negative` | passed |
| unknown participant | `account_participant_fk` | passed |
| unknown asset | `account_asset_fk` | passed |
| duplicate asset code | `asset_code_unique` | passed |

- The successful duplicate-account row is deleted after that test so later check-constraint tests still use the same participant/asset pair.

### 1.8.7 Run Maven verification

- Ran `./mvnw verify`.
  - First run: failed on the exclusive participant count described in 1.8.4.
  - Second run: `BUILD SUCCESS`.
  - Tests run: 11. Failures: 0. Errors: 0. Skipped: 0.
  - `DvpApplicationTests` ran without Docker.
  - Integration tests started Testcontainers `postgres:18` and Flyway applied V1 to that empty database.
  - Spring Boot repackaged the executable jar.
- The older `DVP_VERIFY_JDBC` local checks were not executed. They remain optional and were not counted as skipped.

- Ready for Section 1.9.
  - Automated Phase 1 tests run against real PostgreSQL 18.
- Stopped here. Section 1.9 was not started.

### 1.8 correction: isolate test data and pin PostgreSQL 18.6

- Original shared-state problem:
  - One Testcontainers PostgreSQL instance was reused across integration tests. That remains the intended lifecycle.
  - `DatabaseConstraintIntegrationTest` inserted a `Constraint` / `C1` fixture and left those rows in the database.
  - `DemoSeedIntegrationTest` then saw 3 participants instead of the 2 seed rows.
  - The first 1.8.7 run failed. The follow-up change weakened seed assertions to count deterministic IDs only, so leaked rows no longer failed the test.
- Isolation approach:
  - Kept one shared `postgres:18.6` container per JVM, `@DynamicPropertySource`, Flyway V1, and `scripts/seed-demo.sql`.
  - `AbstractPostgresIntegrationTest` now runs `TRUNCATE TABLE account, participant, asset` before every test.
  - `flyway_schema_history` is not truncated, so V1 stays applied.
  - JUnit runs the superclass `@BeforeEach` before subclass fixture inserts, so constraint tests still create `Constraint` / `C1` on an empty data set.
- Why TRUNCATE:
  - It is the smallest reset that makes tests order-independent without a new Docker container per test.
  - A test `@Transactional` rollback would not undo `ResourceDatabasePopulator`, which commits the seed on its own connection.
  - Exclusive seed counts (2 participants, 2 assets, 4 accounts) and `findAll()` size 4 were restored. They are no longer relaxed to tolerate leaked rows.
- PostgreSQL image pin:
  - Testcontainers: `postgres:18` → `postgres:18.6`
  - `compose.yaml`: `postgres:18` → `postgres:18.6`
  - Startup test now requires `SHOW server_version` to start with `18.6`.
- Verification:
  - `./mvnw verify`
    - Result: `BUILD SUCCESS`. Tests run: 11. Failures: 0. Errors: 0. Skipped: 0.
    - Image: `postgres:18.6`. Server: PostgreSQL 18.6.
    - Flyway applied V1 to an empty schema, then `validate()` succeeded.
    - Default Surefire order still ran constraint tests before seed tests. Exclusive seed counts passed.
    - Account reads and all six constraint tests passed.
  - `./mvnw -Dsurefire.runOrder=reverseAlphabetical test`
    - Result: passed. Tests run: 11. Skipped: 0.
    - Seed tests ran before constraint tests. Same assertions still passed.

- Ready for Section 1.9.
- Stopped here. Section 1.9 was not started.

## 1.9 Phase 1 verification

Purpose: confirm the Phase 1 foundation before any trade logic. No product features were added during this review.

### 1.9.1 Run the complete build and test suite

- Ran `./mvnw clean verify` from a deleted `target/` directory.
  - Java: Temurin 21.0.3, `JAVA_HOME` = `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
  - Maven Wrapper: 3.9.11
  - Result: `BUILD SUCCESS`
  - Tests run: 11. Failures: 0. Errors: 0. Skipped: 0.
  - Testcontainers image: `postgres:18.6`
  - Flyway: empty schema, then `Successfully applied 1 migration ... now at version v1`
  - Server: PostgreSQL 18.6
  - Spring Boot repackaged the executable jar

```text
Java 21                    PASS
Maven Wrapper              PASS
Spring Boot startup        PASS
PostgreSQL connection      PASS
Flyway V1                  PASS
Participant table          PASS
Asset table                PASS
Account table              PASS
Seed data                  PASS
Spring JDBC account read   PASS
Testcontainers             PASS
Database constraints       PASS
Maven verify               PASS
```

### 1.9.2 Review the repository

- `git status`: working tree was clean before this 1.9 log update. Branch `main`.
- Tracked application Java is only:
  - `DvpApplication.java`
  - `domain/{Participant,Asset,AssetType,Account}.java`
  - `persistence/AccountRepository.java` with `findAll` and `findById` only
- No REST controllers, trade/settlement/journal classes, or write APIs.
- `.gitignore` ignores `target/`, `.env`, `.env.*`, and `.DS_Store`. `!.env.example` is kept.
- `git check-ignore` confirms `.env` and `target/` are ignored. `git ls-files` does not contain `.env` or `target/`.
- `.env.example` is tracked and contains placeholders only (`change-me`), not the local password.
- `pom.xml` dependencies stay within the approved stack: Spring Web MVC, JDBC, validation, PostgreSQL driver, Flyway, JUnit/AssertJ via `spring-boot-starter-test`, Testcontainers PostgreSQL.
- Confirmed absent: JPA, Hibernate ORM, H2, Kafka, frontend, microservices.
- Hibernate Validator remains only as the Bean Validation implementation from `spring-boot-starter-validation`.
- No empty future packages such as `api`, `service`, `trade`, or `settlement`.

### 1.9.3 Review the engineering log

- Present: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, plus the 1.4 PostgreSQL 18 correction and the 1.8 isolation/image-pin correction.
- Failures kept in history include:
  - 1.2 `verify` missing main class
  - 1.3 default `spring-boot:run` without a DataSource
  - 1.5 Flyway not applying until `spring-boot-starter-flyway`
  - 1.7 `@ConditionalOnBean` skipping `AccountRepository`
  - 1.8 leaked constraint-fixture rows weakening seed assertions
- This section is the Phase 1 final verification result.

### 1.9.4 Review the final repository structure

Actual shape matches the plan, with these justified differences:

- Production packages are `domain` and `persistence`, not `account/`.
  - Domain types are financial concepts, not JDBC types.
  - `AccountRepository` is the JDBC persistence module needed in Phase 1 (ADR-004).
- `compose.yaml` exists for local PostgreSQL 18.6. The plan allows this.
- Extra documentation already in the repository: `README.md`, `docs/detailed-plan/phase-1-diagram.md`.
- Test support lives under `src/test/java/com/jasonwidjaja/dvp/support/`.
- Optional local JDBC checks remain: `JdbcConnectionVerification` and `AccountReadVerification`, gated by `DVP_VERIFY_JDBC=true`. They are not required by `./mvnw clean verify` and were not skipped there.

### 1.9.5 Confirm Phase 1 exit criteria

- Java 21 is configured and working.
- Maven Wrapper works.
- Spring Boot application starts.
- PostgreSQL runs and accepts connections.
- Spring Boot connects to PostgreSQL.
- Flyway creates V1 successfully.
- `participant`, `asset`, and `account` tables exist.
- Database constraints reject invalid account state.
- Deterministic Alice/Bob seed data works.
- Seed reruns do not reset financial state.
- Spring JDBC reads account data correctly.
- Testcontainers runs integration tests against real PostgreSQL 18.6.
- `./mvnw clean verify` passes.
- No secrets or generated build output are committed.
- This engineering log describes the implementation, including failed attempts and corrections.

Phase 1 is complete.

- Did not implement Trade Capture.
- Did not create `docs/detailed-plan/phase-2.md`.
- Stopped here.

## 2.1 Confirm Phase 1 and approve the Trade Capture contract

### 2.1.1 Inspect the completed Phase 1 repository

- Inspected the repository before any Phase 2 application code.
  - Production Java remains Phase 1 only:
    - `DvpApplication.java`
    - `domain/{Participant,Asset,AssetType,Account}.java`
    - `persistence/AccountRepository.java` (`findAll`, `findById`)
  - Flyway still has only `V1__participants_assets_accounts.sql`.
  - No `V2` migration.
  - No trade, command, or REST controller classes.
  - Test support still uses one shared `postgres:18.6` Testcontainer, `@DynamicPropertySource`, `TRUNCATE TABLE account, participant, asset` before each test, and `DemoSeed` for `scripts/seed-demo.sql`.
- `git status` before this log update:
  - Branch `main`, up to date with `origin/main`.
  - Modified: `README.md` (existing documentation work; left untouched).
  - Untracked planning files: `docs/detailed-plan/phase-2.md`, `docs/detailed-plan/phase-2-implementation-explanation.md`.
  - No Trade Capture implementation to overwrite.
- Read `docs/project-spec.md`, `docs/decisions.md`, `docs/implementation-plan.md`, `docs/detailed-plan/phase-1.md`, `docs/detailed-plan/phase-2.md`, and this engineering log.
- Difference from the 1.9 close-out note:
  - 1.9 recorded that `docs/detailed-plan/phase-2.md` did not exist yet.
  - It now exists as the approved Phase 2 plan. That is planning, not application code.
- Ran `./mvnw verify`.
  - Purpose: confirm the Phase 1 suite still passes before Phase 2 implementation.
  - Result: `BUILD SUCCESS`.
  - Tests run: 11. Failures: 0. Errors: 0. Skipped: 0.
  - Testcontainers: `postgres:18.6`.
  - Flyway applied V1 to an empty test schema.

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

## 2.2 Trade domain and API types

### 2.2.1 Create the Trade domain records

- Added immutable records and enum in `com.jasonwidjaja.dvp.domain`.
- `TradeTerms` — accepted economic fact: `externalTradeId`, `buyerId`, `sellerId`, `securityId`, `quantity`, `cashAmount`, `settlementDate`.
  - Identifiers are `UUID`. Quantity and cash are `long`. Settlement date is `LocalDate`.
- `TradeStatus` — Phase 2 value `READY` only. `SETTLED` was not added.
- `Trade` — internal `id`, `TradeTerms`, and `status`.
- No setters, settlement methods, journal fields, balance mutation, or locking.

### 2.2.2 Create `CaptureCommand`

- Added `CaptureCommand` with `idempotencyKey` and `TradeTerms`.
- The key is not part of `TradeTerms`, so two keys can name the same business trade.

### 2.2.3 Create the HTTP DTOs

- Added `com.jasonwidjaja.dvp.api` because HTTP representations are needed now.
- `CaptureTradeRequest` — `POST /v1/trades` body. Field names match the approved JSON contract.
- `TradeResponse` — `POST /v1/trades` and `GET /v1/trades/{id}` body. Includes `id`, terms, and `status`.
- `AccountResponse` — `GET /v1/accounts` body. Maps the Phase 1 `Account` (participant id/name, asset id/code/type, opening and current balances).
- `ErrorResponse` — `code` and `message`.
- No REST controllers were added.

### 2.2.4 Configure request validation

- Bean Validation on `CaptureTradeRequest`:
  - `externalTradeId`: `@NotNull`, length 1–128, `@NoSurroundingWhitespace` (C1: reject leading/trailing whitespace, do not trim)
  - `buyerId`, `sellerId`, `securityId`: `@NotNull` UUIDs
  - `quantity` and `cashAmount`: nullable `Long` with `@NotNull` and `@Positive` so missing is distinct from zero
  - `settlementDate`: `@NotNull` `LocalDate`
- Reference-data checks (participant exists, self-trade) stay out of the DTO.
- Jackson rejects float-to-int coercion and string-to-integer coercion so `"10"` and `10.5` are structural errors.
  - `application.yml`: `spring.jackson.deserialization.accept-float-as-int=false`
  - `JacksonConfiguration` applies the same rules through `JsonMapping`.
- Tests:
  - `TradeDomainTest` — terms, `READY` only, command key separate from terms
  - `CaptureTradeRequestValidationTest` — missing/blank/whitespace/length/zero/negative/null cases; self-trade is still structurally valid
  - `CaptureTradeRequestJsonTest` — approved JSON decode, property-order independence, missing quantity is null, reject malformed/invalid UUID/date/fraction/string numbers, response field names
  - `AccountResponseTest` — Phase 1 account mapping
- First JSON test used `@SpringBootTest` and `@MockitoBean`. In the sandbox that failed to attach Mockito's mock maker. The test was rewritten as a unit test using the same `JsonMapping` rules, without a Spring context.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 28. Failures: 0. Errors: 0. Skipped: 0.
  - Flyway still applied only V1. No V2 migration exists.

- Ready for Section 2.3.
- Stopped here. Section 2.3 was not started.

## 2.3 Flyway V2 Trade Capture schema

### 2.3.1 Create Flyway V2

- Added `src/main/resources/db/migration/V2__trades_and_command_results.sql`.
  - Purpose: add the durable PostgreSQL structures required for Trade Capture.
  - Creates `trade` and `command_result` only.
- `V1__participants_assets_accounts.sql` was not edited.

### 2.3.2 Create the trade table

- Table: `trade`
  - `id UUID PRIMARY KEY`
  - `external_trade_id TEXT NOT NULL` with `trade_external_trade_id_unique`
  - `buyer_id` / `seller_id` reference `participant(id)`
  - `security_id` references `asset(id)`
  - `quantity BIGINT NOT NULL` with `trade_quantity_positive` (`> 0`)
  - `cash_amount BIGINT NOT NULL` with `trade_cash_amount_positive` (`> 0`)
  - `settlement_date DATE NOT NULL`
  - `status TEXT NOT NULL` with `trade_status_supported` (`READY` only)
  - `trade_buyer_not_seller` rejects buyer = seller
- Encoded approved C1 at the database as `trade_external_trade_id_format`:
  - length 1–128
  - value equals `btrim(value)`, so leading or trailing ordinary space is rejected
- No journal, posting, settlement-attempt, or `SETTLED` columns.
- Security type (`CASH` vs `SECURITY`) is not constrained here. That remains an application check for Section 2.4 / 2.5.

### 2.3.3 Create the command-result table

- Table: `command_result`
  - `command_key TEXT PRIMARY KEY` — one global command-key namespace (C3)
  - `operation TEXT NOT NULL` restricted to `CAPTURE_TRADE`
  - `request_identity TEXT NOT NULL` — stores the parsed request identity, not raw JSON bytes
  - `http_status INTEGER`
  - `response_body TEXT`
  - `location TEXT`
- Encoded approved C3 key format as `command_result_key_format` (1–128 characters, `btrim` for surrounding space).
- `command_result_completion_state` allows only:
  - an unfinished claim: status, body, and location all null
  - a completed result: status and body present; location may be null for a business rejection
- No foreign key to `trade`. A `422` rejection can be stored without a trade row.
- Added trigger `command_result_completed_immutable`.
  - An unfinished claim can be finalized by `UPDATE`.
  - A completed result cannot be overwritten.
  - The plan required this guarantee in 2.3.3. Phase 1 had no triggers, so this is the mechanism used here.

### 2.3.4 Verify V2 and preserve Phase 1 state

- Updated `PostgresStartupIntegrationTest.flywayAppliesV1AndV2ToCleanTestDatabase`.
  - Current version is `2` / `V2__trades_and_command_results.sql`.
  - Applied scripts: V1 then V2.
  - Public tables: `account`, `asset`, `command_result`, `flyway_schema_history`, `participant`, `trade`.
  - No settlement, journal, or reconciliation tables.
- Extended `AbstractPostgresIntegrationTest` cleanup to `TRUNCATE TABLE command_result, trade, account, participant, asset`.
  - `flyway_schema_history` is still not truncated.
- Added `TradeCaptureSchemaIntegrationTest` against Testcontainers PostgreSQL 18.6.
- Added `FlywayV2UpgradeIntegrationTest`.
  - Uses the shared `postgres:18.6` container and a separate database `dvp_v1_to_v2`.
  - Applies V1 only, loads `scripts/seed-demo.sql`, then applies V2.
  - Alice/Bob IDs, asset codes, and account balances stay unchanged.
  - `trade` and `command_result` start empty.

Constraint verification:

| Test | Guarantee | Result |
| --- | --- | --- |
| valid trade insert | accepted `READY` row | passed after two test fixes |
| duplicate external reference | `trade_external_trade_id_unique` | passed |
| unknown buyer/seller/security | `trade_buyer_fk` / `trade_seller_fk` / `trade_security_fk` | passed |
| self-trade | `trade_buyer_not_seller` | passed |
| zero/negative quantity | `trade_quantity_positive` | passed |
| zero/negative cash amount | `trade_cash_amount_positive` | passed |
| unsupported status `SETTLED` | `trade_status_supported` | passed |
| surrounding whitespace on external id | `trade_external_trade_id_format` | passed |
| valid completed command result | stored | passed |
| duplicate command key | `command_result_pkey` | passed |
| invalid partial result | `command_result_completion_state` | passed |
| business rejection without a trade | stored, trade count 0 | passed |
| unfinished claim finalized | `UPDATE` of null result fields | passed |
| completed result overwrite | `command_result_completed_immutable` | passed |
| V1 → V2 upgrade | Phase 1 seed unchanged, new tables empty | passed |

Failures encountered:

- First `validTradeRowCanBeInserted` used UUID text `00000000-0000-0000-0000-00000000t001`.
  - Cause: `t` is not a hex digit.
  - Fix: use `00000000-0000-0000-0000-000000000101`.
- Second run compared `settlement_date` to `LocalDate`.
  - Cause: `queryForMap` returns `java.sql.Date`.
  - Fix: convert with `toLocalDate()` before asserting.

- Ran `./mvnw verify`.
  - First two runs failed on the test issues above.
  - Third run: `BUILD SUCCESS`.
  - Tests run: 47. Failures: 0. Errors: 0. Skipped: 0.
  - Phase 1 tests still pass.
  - Flyway applies V1 then V2 on a clean Testcontainers database.

- No Trade Capture repositories, services, or HTTP controllers were added.
- Ready for Section 2.4.
- Stopped here. Section 2.4 was not started.

### 2.3 correction: match Java surrounding-whitespace rule

- Compared `@NoSurroundingWhitespace` (`value.equals(value.strip())`) with the first V2 format checks (`value = btrim(value)`).
  - Java `String.strip()` uses `Character.isWhitespace`, so a leading or trailing tab, newline, CR, or other Java whitespace is rejected.
  - PostgreSQL `btrim()` with no second argument removes only ordinary space (`U+0020`). A leading tab or newline would have been accepted.
- Edited `V2__trades_and_command_results.sql` in place because Section 2.3 was not closed. No V3 was added.
- Added SQL function `no_surrounding_whitespace` that rejects the same first/last characters as Java `strip()` / `Character.isWhitespace`.
  - Both `trade_external_trade_id_format` and `command_result_key_format` now call that function.
  - `command_result_request_identity_not_blank` still uses `btrim`. That constraint is only "not blank", not the C1/C3 surrounding-whitespace rule.
- Extended schema tests:
  - `surroundingWhitespaceOnExternalTradeIdIsRejected` now covers space, tab, newline, CR, and ideographic space (`U+3000`).
  - Added `surroundingWhitespaceOnCommandKeyIsRejected` with the same cases.
- Ran `TradeCaptureSchemaIntegrationTest`, `FlywayV2UpgradeIntegrationTest`, and `PostgresStartupIntegrationTest`.
  - Result: passed. 22 tests.
- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 48. Failures: 0. Errors: 0. Skipped: 0.
- Still no Trade Capture repositories, services, or HTTP controllers.
- Stopped here. Section 2.4 was not started.

## 2.4 Spring JDBC Trade Capture persistence

### 2.4.1 Create participant and asset lookup repositories

- Added `ParticipantRepository`.
  - `existsById(UUID)` — `SELECT id FROM participant WHERE id = :participantId`
  - Missing participant is `false`, not an exception.
- Added `AssetRepository`.
  - `findById(UUID)` — `SELECT id, code, type FROM asset WHERE id = :assetId`
  - Returns `Optional` so AUD can be distinguished from a missing asset and from EQ1 (`SECURITY`).
- Reused the Phase 1 `Participant`, `Asset`, and `AssetType` records.
- Both repositories use `NamedParameterJdbcTemplate` and explicit selected columns.

### 2.4.2 Create `TradeRepository`

- Added `TradeRepository`.
  - `findById(UUID)`
  - `findByExternalTradeId(String)`
  - `insertIfAbsent(TradeTerms)` generates a UUID, inserts `status = READY`, and uses `ON CONFLICT (external_trade_id) DO NOTHING RETURNING ...`
- A duplicate external reference returns empty and leaves the original terms unchanged. There is no upsert.

### 2.4.3 Create `CommandResultRepository`

- Added domain types needed to store and compare durable command outcomes:
  - `CommandResult` — `commandKey`, `operation`, `requestIdentity`, nullable `httpStatus` / `responseBody` / `location`
  - `CaptureRequestIdentity` — canonical string for C3: `CAPTURE_TRADE` plus the parsed terms in a fixed field order. JSON whitespace and property order never enter this string.
- Added `CommandResultRepository`.
  - `claim(commandKey, requestIdentity)` — `INSERT ... ON CONFLICT (command_key) DO NOTHING`. Returns `true` only when this call created the row.
  - `findByCommandKey` reads the stored identity and, if present, the completed result.
  - `finalize(...)` updates only `WHERE http_status IS NULL`. A completed row is not replaced.
- Identity comparison is string equality of `CaptureRequestIdentity.of(terms)` against the stored `request_identity`. A second claim with different terms keeps the original identity.

### 2.4.4 Verify the persistence layer

- Added PostgreSQL 18.6 integration tests on the shared Testcontainers cleanup:
  - `ReferenceDataRepositoryIntegrationTest` — Alice/Bob exist, unknown participant is false, AUD is `CASH`, EQ1 is `SECURITY`
  - `TradeRepositoryIntegrationTest` — insert/read round-trip, unknown id empty, duplicate reference does not overwrite
  - `CommandResultRepositoryIntegrationTest` — claim, duplicate key, finalize, completed overwrite rejected, 422 without location
  - `CaptureRequestIdentityTest` — same parsed terms share an identity; a changed quantity does not
- Repositories do not own a Trade Capture transaction. `CaptureTradeService` was not added.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 63. Failures: 0. Errors: 0. Skipped: 0.

- Ready for Section 2.5.
- Stopped here. Section 2.5 was not started.

### 2.4 correction: unambiguous identity and exact finalize

- First `CaptureRequestIdentity` encoding joined fields with `|`.
  - That is delimiter-based. `externalTradeId` may contain `|`, `:`, or `,`.
  - C3 still holds: identity is `CAPTURE_TRADE` plus the parsed terms, not raw JSON.
- Replaced the encoding with length-prefixed fields: `<length>:<value>,` for the operation and every term.
  - A pipe or comma inside `externalTradeId` cannot merge with the next field.
- `finalize(...)` is now `void`.
  - It succeeds only when the `UPDATE` affects exactly one unfinished row.
  - 0 or more than 1 rows throw `IncorrectResultSizeDataAccessException`.
  - Completing an already completed key, or an unknown key, is an error. The original completed row stays unchanged.
- Tests:
  - `CaptureRequestIdentityTest` — pipe-shifted fields, `5:T-001,`, and comma-like references do not collide
  - `CommandResultRepositoryIntegrationTest` — completed and unknown finalize throw
  - `CommandResultRepositoryFinalizeTest` — mocked `UPDATE` of 0 or 2 rows throws

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 68. Failures: 0. Errors: 0. Skipped: 0.
- Still no `CaptureTradeService` or capture HTTP API.
- Stopped here. Section 2.5 was not started.

## 2.5 Atomic Trade Capture service

### 2.5.1 Create the service-owned transaction boundary

- Added `com.jasonwidjaja.dvp.application.CaptureTradeService`.
- The service owns the Trade Capture transaction.
  - Injects the Spring JDBC `PlatformTransactionManager` for the application `DataSource`.
  - Wraps capture in `TransactionTemplate`.
  - Confirmed at runtime: `JdbcTransactionManager` on the same `DataSource` used by the repositories.
- The controller is not present. The service returns `CommandOutcome` only after the callback completes (commit on success, rollback on thrown failure).
- First attempt used `@ConditionalOnBean(PlatformTransactionManager.class)`. The service bean was not created in the integration tests. The condition was removed. `DvpApplicationTests` now mocks `PlatformTransactionManager` because that test excludes the DataSource.

### 2.5.2 Implement the capture sequence

- Added `CommandOutcome` (`httpStatus`, `responseBody`, `location`).
- Sequence inside the transaction:
  1. Claim the command key with `CaptureRequestIdentity.of(terms)`.
  2. Existing key + same identity → replay the stored outcome.
  3. Existing key + different identity → `409` `IDEMPOTENCY_CONFLICT`; stored result unchanged.
  4. New key: buyer ≠ seller, buyer exists, seller exists, security exists, asset type is `SECURITY`.
  5. Business failure → no trade, finalize `422`, commit.
  6. `insertIfAbsent`; new row → `201` + Location `/v1/trades/{id}`.
  7. Existing row + same terms → `200` + same Location.
  8. Existing row + different terms → `409` `TRADE_CONFLICT`; original trade unchanged.
- Response bodies are `TradeResponse` or `ErrorResponse` JSON (`code`, `message`).
- No sufficiency checks, reservations, balance updates, account locks, journals, or `SETTLED`.

Business rejection codes:

| Case | Code |
| --- | --- |
| buyer or seller missing | `UNKNOWN_PARTICIPANT` |
| security missing | `UNKNOWN_SECURITY` |
| cash asset used as security | `NOT_A_SECURITY` |
| buyer = seller | `SELF_TRADE` |

### 2.5.3 Verify Trade Capture behaviour

- `CaptureTradeServiceIntegrationTest` against Testcontainers PostgreSQL 18.6.

| Case | Result |
| --- | --- |
| valid Alice/Bob/EQ1 | `201`, `READY`, one trade, one command result, balances unchanged |
| same key + same request | replayed outcome, no second trade or command row |
| same key + changed quantity | `409` `IDEMPOTENCY_CONFLICT`, original 201 and trade unchanged |
| new key + same terms | `200`, same trade id, second command result |
| new key + different terms | `409` `TRADE_CONFLICT`, original quantity 10 |
| unknown buyer/seller/security, AUD as security, self-trade | `422`, no trade; unknown buyer replay is durable |

### 2.5.4 Prove capture rollback

- Test-only hook: `failOnceAfterTradeInsert` throws after a successful insert and before finalize. Not a product API.
- Injected `IllegalStateException("forced capture failure")`.
- After the service call ended (no outer test transaction):
  - trade `T-001` absent
  - command key `capture-T-001` absent
  - Phase 1 balances unchanged
- Retry of the same command then returned `201` once.
- Observed in `CaptureTradeRollbackIntegrationTest`.

- Ran `./mvnw verify`.
  - First run failed: service missing because of `@ConditionalOnBean`.
  - After removing the condition: `BUILD SUCCESS`.
  - Tests run: 80. Failures: 0. Errors: 0. Skipped: 0.

- No REST controllers were added.
- Ready for Section 2.6.
- Stopped here. Section 2.6 was not started.

### 2.5 correction: error codes and test-only rollback failure

- Final Phase 2 error-code names, before the public HTTP API:
  - `IDEMPOTENCY_CONFLICT` → `IDEMPOTENCY_KEY_CONFLICT`
  - `TRADE_CONFLICT` → `TRADE_REFERENCE_CONFLICT`
  - `NOT_A_SECURITY` → `INVALID_SECURITY`
  - Unchanged: `UNKNOWN_PARTICIPANT`, `UNKNOWN_SECURITY`, `SELF_TRADE`
- Removed `failOnceAfterTradeInsert` and the production failure field from `CaptureTradeService`.
  - Production capture sequence is unchanged: claim, validate, insert-if-absent, finalize.
  - `TransactionTemplate` is still the capture boundary.
- Rollback proof now uses a test-only `@Primary` `TradeRepository` decorator in `CaptureTradeRollbackIntegrationTest`.
  - `insertIfAbsent` performs the real insert, then throws once before the service can finalize.
  - After the thrown failure: no trade, no command claim, balances unchanged.
  - Retry of the same command then returns `201` once.
- First decorator attempt used a `private final` nested class.
  - Spring could not CGLIB-proxy the `@Repository` subclass.
  - Context failed to load.
  - Fixed by making the decorator package-visible and non-final.
- Updated `CaptureTradeServiceIntegrationTest` assertions to the final codes.

- Ran `CaptureTradeServiceIntegrationTest` and `CaptureTradeRollbackIntegrationTest`.
  - First run: rollback context failed (`private final` decorator).
  - After the visibility fix: passed. Failures: 0. Errors: 0. Skipped: 0.
- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 80. Failures: 0. Errors: 0. Skipped: 0.
- No REST controllers were added.
- Stopped here. Section 2.6 was not started.

## 2.6 Trade Capture REST API

### 2.6.1 Create `POST /v1/trades`

- Added `TradeController`.
  - `POST /v1/trades` requires `Content-Type: application/json` and exactly one `Idempotency-Key`.
  - Validates `CaptureTradeRequest`.
  - Maps the request to `TradeTerms` + `CaptureCommand`.
  - Calls `CaptureTradeService` and returns its status, stored JSON body, and `Location` when present.
- Controller does not contain SQL, transaction management, participant/security checks, or command-result persistence.
- `IdempotencyKey.requireExactlyOne` enforces C3: exactly one header, 1–128 characters, no surrounding whitespace via `String.strip()`. The key is not trimmed.

### 2.6.2 Create `GET /v1/trades/{id}`

- Added `GET /v1/trades/{id}` on `TradeController`.
- Reads the current stored trade through `TradeRepository.findById`.
- Returns `TradeResponse` when found.
- Valid unknown UUID → `404` `UNKNOWN_TRADE`.
- Malformed UUID → `400` `INVALID_REQUEST`.
- HTTP test captured a trade, followed `Location`, and compared every stored term.

### 2.6.3 Create `GET /v1/accounts`

- Added `AccountController`.
- `GET /v1/accounts` returns `AccountResponse` rows from `AccountRepository.findAll()`.
- Does not seed data, create accounts, or update balances.
- HTTP test: GET accounts, capture T-001, GET accounts again.
  - Alice AUD 100000/100000, Alice EQ1 0/0, Bob AUD 0/0, Bob EQ1 10/10 before and after.

### 2.6.4 Add API error handling

- Added `ApiExceptionHandler` as the single MVC `@RestControllerAdvice`.
- Request-level error body remains `{code,message}`.

| Situation | Status | Code |
| --- | --- | --- |
| missing / empty / oversized / duplicate `Idempotency-Key` | `400` | `INVALID_IDEMPOTENCY_KEY` |
| surrounding whitespace on the key | `400` | `INVALID_IDEMPOTENCY_KEY` |
| malformed JSON or invalid JSON value | `400` | `MALFORMED_REQUEST` |
| Bean Validation or malformed path UUID | `400` | `INVALID_REQUEST` |
| unsupported media type | `415` | `UNSUPPORTED_MEDIA_TYPE` |
| unknown trade | `404` | `UNKNOWN_TRADE` |
| unexpected failure | `500` | `INTERNAL_ERROR` |

- Service outcomes are unchanged: `201`/`200`, `409` `IDEMPOTENCY_KEY_CONFLICT` / `TRADE_REFERENCE_CONFLICT`, `422` business codes.
- Messages never include SQL, credentials, or exception types.
- Unexpected framework behaviour:
  - Tomcat / HTTP optional whitespace removes ordinary leading/trailing spaces and tabs from `Idempotency-Key` before the controller sees the value.
  - First HTTP test sent `" capture-T-001"` and received `201` because the header arrived as `capture-T-001`.
  - Surrounding-whitespace rejection is therefore proven by `IdempotencyKeyTest`, not by sending spaces through HTTP.
  - `String.strip()` does not treat NBSP as whitespace, matching `NoSurroundingWhitespace` / `Character.isWhitespace`.

### 2.6.5 Verify the HTTP workflow

- `TradeCaptureHttpIntegrationTest` against Testcontainers PostgreSQL 18.6 and a live Tomcat port.
  - POST valid Alice/Bob/EQ1 → `201`, `READY`, `Location /v1/trades/{id}`.
  - GET Location returns the same terms.
  - GET accounts before and after: balances unchanged.
  - Same POST replayed: one trade, same body, durable `201`.
  - New key + same terms → `200`.
  - Same key + changed quantity → `409` `IDEMPOTENCY_KEY_CONFLICT`.
  - New key + different terms → `409` `TRADE_REFERENCE_CONFLICT`.
  - Unknown buyer → durable `422` `UNKNOWN_PARTICIPANT`.
  - Self-trade → `422` `SELF_TRADE`.
  - Request-level `400`/`415` do not write `command_result` or `trade`.
- `CaptureTradeInternalErrorHttpTest` mocks `CaptureTradeService` to throw a PostgreSQL/password message.
  - HTTP returns `500` `INTERNAL_ERROR` / `An unexpected error occurred`.
- `DvpApplicationTests` now expects `tradeController` and `accountController`.

- Ran `TradeCaptureHttpIntegrationTest`, `CaptureTradeInternalErrorHttpTest`, `IdempotencyKeyTest`.
  - First HTTP run failed: space-prefixed `Idempotency-Key` became a valid key after Tomcat trimming.
  - After moving whitespace proof to `IdempotencyKeyTest` and dropping NBSP (not Java whitespace): passed.
- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 94. Failures: 0. Errors: 0. Skipped: 0.
- No settlement, journal, or Section 2.7 walkthrough was added.
- Stopped here. Section 2.7 was not started.

## 2.7 Phase 2 verification

Purpose: confirm Trade Capture is complete before settlement. No product features were added during this review. README walkthrough commands were documented after they were executed.

### 2.7.1 Run the complete build and test suite

- Ran `./mvnw clean verify` from a deleted `target/` directory.
  - Java: Temurin 21.0.3, `JAVA_HOME` = `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
  - Maven Wrapper: 3.9.11
  - Result: `BUILD SUCCESS`
  - Tests run: 94. Failures: 0. Errors: 0. Skipped: 0.
  - Testcontainers image: `postgres:18.6`
  - Flyway on a clean test schema: `Successfully applied 2 migrations ... now at version v2`
  - `FlywayV2UpgradeIntegrationTest`: V1-only database migrated to V2; Phase 1 balances unchanged.
  - Server: PostgreSQL 18.6
  - Required Testcontainers tests executed. None were skipped.
  - Spring Boot repackaged the executable jar.

```text
Phase 1 tests                    PASS
Flyway V1 + V2                   PASS
Trade schema constraints         PASS
Command-result constraints       PASS
Reference-data reads             PASS
Trade persistence                PASS
Command persistence              PASS
Trade Capture transaction        PASS
Idempotent replay                PASS
Capture rollback                 PASS
HTTP capture                     PASS
Trade read-back                  PASS
Account inspection               PASS
Account balances unchanged       PASS
Maven verify                     PASS
```

### 2.7.2 Run the minimal Trade Capture walkthrough

- Started PostgreSQL with `docker compose --env-file .env up -d`.
  - Recreated `dvp-postgres` to the current `postgres:18.6` image.
  - Server: PostgreSQL 18.6. Flyway history was still V1 only.
- Exported `.env` and ran `./mvnw spring-boot:run`.
  - Flyway: current version 1, then `Migrating schema "public" to version "2 - trades and command results"`.
  - Now at version v2.
  - Tomcat: port 8080.
- Applied `scripts/seed-demo.sql`.
  - All three inserts reported `INSERT 0 0` (Alice/Bob/accounts already present; balances not reset).
- `GET /v1/accounts` → `200`. Starting balances:
  - Alice AUD 100000/100000
  - Alice EQ1 0/0
  - Bob AUD 0/0
  - Bob EQ1 10/10
- `POST /v1/trades` with `Idempotency-Key: capture-T-001` and the approved T-001 body.
  - `201 Created`
  - `Location: /v1/trades/a490fda8-9982-47d5-80ee-16a5f7347653`
  - Body: `T-001`, Alice, Bob, EQ1, quantity 10, cashAmount 50000, settlementDate 2026-09-20, `READY`
- `GET` that Location → `200`, same terms and `READY`.
- Repeated the same POST → `201`, identical body and Location.
- `GET /v1/accounts` again: every opening/current balance unchanged.
- PostgreSQL: one `trade` row, one `command_result` row.
- Stopped the application. Nothing settled.
- Added the same commands to `README.md` after they were observed to work.

### 2.7.3 Review the repository

- `git status` before this 2.7 documentation update: branch `main`, ahead of `origin/main` by 5 commits, working tree clean.
- V1 was not edited. Last change remains `953f06b` (1.5.6).
- V2 contains only `trade` and `command_result`.
- No settlement/journal/posting schema.
- `AccountRepository` still has `findAll` / `findById` only. No account write API.
- `git check-ignore` confirms `.env` and `target/` are ignored. `git ls-files` does not contain `.env` or `target/`.
- `.env.example` is tracked and still uses placeholders (`change-me`).
- Confirmed absent from `pom.xml` and production Java: JPA, Hibernate ORM, Kafka, H2, settlement services.
- Hibernate Validator remains only as the Bean Validation implementation from `spring-boot-starter-validation`.
- `SETTLED` appears only as a rejected schema-constraint fixture in tests, not as a product status.
- No Phase 3 classes.

### 2.7.4 Review the engineering log

- Present: 2.1 baseline/contract, 2.2 domain/API types, 2.3 V2 schema, 2.4 JDBC persistence, 2.5 capture service, 2.6 REST API.
- Corrections kept in history:
  - 2.3 Java `strip()` whitespace vs `btrim`
  - 2.4 unambiguous `CaptureRequestIdentity` and exact finalize
  - 2.5 error-code names and removal of the production fail-once hook
  - 2.6 Tomcat trimming of `Idempotency-Key` OWS
- Failures kept include the `@ConditionalOnBean` service skip, CGLIB `private final` decorator, and space-prefixed HTTP key.
- This section is the Phase 2 final verification result.

### 2.7.5 Review the final repository structure

Actual production shape:

```text
src/main/java/com/jasonwidjaja/dvp/
├── DvpApplication.java
├── api/
│   ├── TradeController.java
│   ├── AccountController.java
│   ├── ApiExceptionHandler.java
│   ├── IdempotencyKey.java
│   └── DTOs, Jackson, validation
├── application/
│   ├── CaptureTradeService.java
│   └── CommandOutcome.java
├── domain/
│   ├── Phase 1 types
│   ├── TradeTerms.java, Trade.java, TradeStatus.java
│   ├── CaptureCommand.java
│   ├── CaptureRequestIdentity.java
│   └── CommandResult.java
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

Justified differences from the 2.7 plan sketch:

- `CaptureCommand`, `CaptureRequestIdentity`, and `CommandResult` live in `domain/`, not `application/`.
  - They are immutable capture types, not the orchestration service.
- Extra API types exist because 2.2/2.6 needed DTOs, Bean Validation, Jackson integer rules, and C3 key checking.

### 2.7.6 Confirm Phase 2 exit criteria

- Phase 1 still passes.
- V2 applies to a clean database.
- A V1 database upgrades to V2 without changing existing balances (automated test and this local walkthrough).
- A valid matched trade can be captured as `READY`.
- Captured economic terms cannot be silently overwritten.
- Invalid trades are rejected (`400` request-level, `422` business).
- Same external reference + different terms cannot create a second trade.
- Same idempotency key + same request returns the original outcome.
- Same idempotency key + different request is rejected.
- Business rejection replay follows C4.
- Trade and command outcome commit together.
- Technical failure before commit rolls both back.
- Trade and accounts can be read through the API.
- Trade Capture leaves every account balance unchanged.
- PostgreSQL integration tests pass.
- `./mvnw clean verify` passes: 94 tests, 0 skipped.
- No secrets or generated build output are committed.
- This engineering log describes Phase 2, including failures and corrections.

Phase 2 is complete.

- Did not implement settlement.
- Did not create `docs/detailed-plan/phase-3.md`.
- Stopped here.

## OpenAPI / Swagger bootstrap

- Added `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`.
  - Source: official springdoc documentation (https://springdoc.org/).
  - `springdoc-openapi 3.x` is the Spring Boot 4 line. Current stable version documented there: `3.1.1`.
  - Changelog `3.1.1` (2026-09-06) upgrades Spring Boot to 4.1.0. This project uses Spring Boot 4.1.1.
  - Did not use Springfox.
- No `@Operation`, `@ApiResponse`, `@Schema`, OpenAPI YAML, grouping, or extra springdoc properties.
- Started `./mvnw spring-boot:run` against local PostgreSQL 18.6.
  - `GET /v3/api-docs` → `200`, OpenAPI `3.1.0`.
  - Paths: `POST /v1/trades`, `GET /v1/trades/{id}`, `GET /v1/accounts`.
  - `GET /swagger-ui.html` → `302` to `/swagger-ui/index.html`.
  - `GET /swagger-ui/index.html` → `200` HTML titled `Swagger UI`.
- Added a short README section with those two URLs.
- First `./mvnw verify` failed: `DvpApplicationTests` still required only `tradeController` and `accountController`.
  - springdoc also registers `openApiResource` and `swaggerConfigResource`.
  - Updated the existing assertion. No new OpenAPI test was added.
- Ran `./mvnw verify` again after that fix.
  - Result: `BUILD SUCCESS`.
  - Tests run: 94. Failures: 0. Errors: 0. Skipped: 0.
- No Phase 3 work. No settlement. No `docs/detailed-plan/phase-3.md` change.

## Unmapped path 404

- `GET /` was returning `500` `INTERNAL_ERROR`.
  - Spring MVC throws `NoResourceFoundException` for the unmatched root/static-resource path.
  - `ApiExceptionHandler`'s `Exception` catch-all mapped that to a safe 500.
- Added `@ExceptionHandler(NoResourceFoundException.class)`:
  - `404` `NOT_FOUND` / `Resource does not exist`
  - does not use `UNKNOWN_TRADE`
  - does not return the exception message or path
- Left the generic `Exception` handler as `500` `INTERNAL_ERROR`.
- Added `TradeCaptureHttpIntegrationTest.unmappedRootPathReturnsNotFoundWithoutHidingOpenApi`.
  - `GET /` → `404` `NOT_FOUND`
  - `GET /v3/api-docs` still `200` and includes `/v1/trades`
  - `GET /swagger-ui/index.html` still `200`
- Existing tests still cover `GET /v1/trades/{unknown-uuid}` → `UNKNOWN_TRADE` and mocked capture failure → `INTERNAL_ERROR`.
- Ran `./mvnw verify`.
  - Result: BUILD SUCCESS. Tests run: 95, Failures: 0, Errors: 0, Skipped: 0.
- No Phase 3 work.

# Phase 3: Atomic Settlement and Financial Inspection

## 3.1 Confirm Phase 2 and approve the settlement contract

### 3.1.1 Inspect the completed Phase 2 repository

- Inspected the completed Phase 2 repository before any settlement code.
- `git status`: branch `main`, up to date with `origin/main`.
  - Uncommitted change at inspection: `docs/detailed-plan/phase-3.md` only (OpenAPI baseline notes already in the Phase 3 plan).
  - No uncommitted Java, SQL, Flyway, API or configuration changes.
- Confirmed no settlement implementation exists.
  - No `SETTLE`, journal, posting, attempt, `Clock` or `BusinessCalendar` types in `src/main/java`.
  - `TradeStatus` is `READY` only.
  - `Trade` has no `journalId`.
  - `TradeResponse` has no `journalId`.
  - `TradeRepository` has `findById`, `findByExternalTradeId` and `insertIfAbsent` only. No locking.
  - `AccountRepository` is read-only: `findAll` / `findById`.
  - `AssetRepository` is `findById` only. No lookup by code `AUD`.
  - `CommandResultRepository.claim` hardcodes `CaptureRequestIdentity.OPERATION` (`CAPTURE_TRADE`).
  - `CaptureRequestIdentity.encode` is private. Settlement will need to reuse that length-prefixed format rather than invent a second encoding.
- Confirmed no V3 migration exists.
  - Flyway files: `V1__participants_assets_accounts.sql`, `V2__trades_and_command_results.sql`.
  - `trade_status_supported` allows `READY` only.
  - `command_result_operation_supported` allows `CAPTURE_TRADE` only.
  - Public tables: `account`, `asset`, `command_result`, `flyway_schema_history`, `participant`, `trade`.
- Confirmed OpenAPI / Swagger baseline.
  - `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` is a dependency.
  - No springdoc properties in `application.yml`.
  - No `@Operation`, `@ApiResponse` or `@Schema` annotations, and no custom OpenAPI YAML.
  - `TradeCaptureHttpIntegrationTest.unmappedRootPathReturnsNotFoundWithoutHidingOpenApi` already asserts `GET /v3/api-docs` is `200` and includes `/v1/trades`, and `GET /swagger-ui/index.html` is `200`.
  - `GET /` is `404` `NOT_FOUND` via `NoResourceFoundException`, distinct from `GET /v1/trades/{unknown-uuid}` `404` `UNKNOWN_TRADE`.
- Phase 2 files Phase 3 must extend rather than replace:
  - `CaptureTradeService` — `TransactionTemplate` claim / validate / persist / finalize pattern
  - `CommandResultRepository`
  - `CaptureRequestIdentity`
  - `TradeRepository`
  - `AccountRepository`
  - `AssetRepository`
  - `TradeStatus`
  - `Trade` / `TradeResponse`
  - `ApiExceptionHandler`
  - `TradeController`
  - `AbstractPostgresIntegrationTest` truncate list
  - `PostgresStartupIntegrationTest` exact table and migration lists
  - `DemoSeed` Alice/Bob/AUD/EQ1 identifiers
- Unexpected differences from the original Phase 3 starting assumptions, already present in the repository:
  - springdoc OpenAPI / Swagger was added after Phase 2 close-out. Controllers are discovered by convention only.
  - Unmapped paths return `404` `NOT_FOUND`, not `500`.
  - `docs/implementation-plan.md` still lists C2 as unresolved. That is expected until 3.1.2 is approved.
- Ran `./mvnw verify`.
  - Purpose: Phase 2 baseline before any Phase 3 code.
  - Result: `BUILD SUCCESS`.
  - Tests run: 95. Failures: 0. Errors: 0. Skipped: 0.
- No settlement code, no V3, no production-behaviour change.
- 3.1.2, 3.1.3 and 3.1.4 were presented for human approval and were not encoded.
- Section 3.2 was not started.

### 3.1.2 Approve the settlement due-date rule (C2)

- Human approval recorded the following final C2 rule. No change from the proposal.
- Business timezone: `Australia/Sydney`.
- Business date is the current calendar date in that timezone. Whole dates only.
- A trade is due when `settlementDate <= businessDate`.
- A trade is not due when `settlementDate > businessDate`.
- Overdue trades (`settlementDate < businessDate`) remain settleable.
- No intraday cutoff, settlement window or batch rule.
- The application exposes one injected `java.time.Clock` bean fixed to `Australia/Sydney`.
- A `BusinessCalendar` component derives `businessDate()` from that clock.
- Settlement code must not call `LocalDate.now()` directly.
- Each committed settlement attempt records the evaluated business date.
- `READY` and `SETTLED` remain the only trade states (ADR-011).
- C2 is resolved.
- No Java, SQL, Flyway or configuration was added.

### 3.1.3 Approve the settlement command contract

- Human approval recorded the Phase 3 extension of C3/C4, with one amendment.

Approved request:

- `POST /v1/trades/{id}/settle`
- exactly one `Idempotency-Key`
- no request body
- settlement request identity is `SETTLE_TRADE` plus the trade id, using the shared length-prefixed canonical encoding

Approved outcomes:

| Situation | Result | Durable command outcome | Settlement attempt |
| --- | --- | --- | --- |
| New valid settlement | `201 Created` + `Location: /v1/journals/{journalId}` | yes | `SETTLED` |
| Same key + same request | replay original result | already stored | none |
| Same key + different request | `409` `IDEMPOTENCY_KEY_CONFLICT` | original unchanged | none |
| New key + already settled trade | `409` `ALREADY_SETTLED` | yes | `ALREADY_SETTLED` |
| Trade not due | `422` `NOT_DUE` | yes | `NOT_DUE` |
| Insufficient buyer cash | `422` `INSUFFICIENT_CASH` | yes | `INSUFFICIENT_CASH` |
| Insufficient seller securities | `422` `INSUFFICIENT_SECURITIES` | yes | `INSUFFICIENT_SECURITIES` |
| Required account row missing | `500` `INTERNAL_ERROR` | no — rollback | none |
| Unknown trade UUID | `404` `UNKNOWN_TRADE` | no — rollback | none |
| Malformed UUID, missing/invalid key, or non-empty body | `400` | no | none |
| Unexpected technical failure before commit | `500` `INTERNAL_ERROR` | no committed settlement result | none |

- Replay records no new settlement attempt.
- Every committed business decision records exactly one settlement attempt.
- Error body remains `{code,message}`.

Change from the original 3.1.3 proposal:

- `MISSING_ACCOUNT` is not a settlement outcome.
- A required settlement account being absent is an internal financial-state / data-integrity failure, not a client business rejection.
- Do not record a `MISSING_ACCOUNT` settlement attempt.
- Do not finalize a durable `422` command result.
- Fail the transaction, roll back the command claim and every settlement write, and expose only the existing safe `500 INTERNAL_ERROR` at the HTTP boundary.

- No Java, SQL, Flyway or configuration was added.

### 3.1.4 Approve the settlement record model

- Human approval recorded the settlement record model, with explicit extra database guarantees.

Sign convention:

- `DEBIT` decreases the account balance.
- `CREDIT` increases the account balance.
- `amount` is always positive. Direction carries the sign.
- Successful settlement postings:
  - buyer cash `DEBIT` `cashAmount`
  - seller cash `CREDIT` `cashAmount`
  - buyer security `CREDIT` `quantity`
  - seller security `DEBIT` `quantity`
- `DEBIT` / `CREDIT` are project-local balance-movement directions. They are not general-ledger / GAAP debit-credit semantics.

Record shape:

- A posting references an account and does not duplicate the asset.
- One journal per trade (`settlement_journal.trade_id` unique).
- `trade.journalId` points at that trade's settlement journal.
- Settlement cash asset is the asset with code `AUD`.
- Attempt vocabulary: `SETTLED`, `ALREADY_SETTLED`, `NOT_DUE`, `INSUFFICIENT_CASH`, `INSUFFICIENT_SECURITIES`.
- `GET /v1/trades/{id}` gains `journalId`, `null` while `READY`.
- Old durable command responses are never rewritten.

Database-enforced guarantees:

- exactly four postings per journal
- each account at most once per journal
- per-asset net movement within a journal is zero
- posting amounts equal the captured trade terms
- the four accounts belong to the trade's buyer and seller
- `trade.journal_id` must reference the `settlement_journal` whose `trade_id` is that same trade. Trade A cannot point at Trade B's journal.
- the two cash postings must use the buyer and seller AUD accounts
- the two security postings must use the buyer and seller accounts for exactly `trade.security_id`, not merely any asset whose type is `SECURITY`
- committed journals and postings reject `UPDATE` and `DELETE`
- a `SETTLED` trade cannot be modified further
- captured trade terms remain immutable

Change from the original 3.1.4 proposal:

- `MISSING_ACCOUNT` was removed from the attempt vocabulary.
- The three extra same-trade journal, AUD cash-account and exact-security-account guarantees were added.

- Later Phase 3 plan steps that still proposed `MISSING_ACCOUNT` were updated to match this approval. Section 3.2 was not implemented.
- No Java, SQL, Flyway or configuration was added.

- Section 3.1 is complete.
- C2 is resolved.
- The settlement command contract is approved.
- The settlement record model is approved.
- Stopped here. Section 3.2 was not started.

## 3.2 Flyway V3 settlement schema

### 3.2.1 Create Flyway V3

- Added `src/main/resources/db/migration/V3__settlement_journal_postings_attempts.sql`.
  - Purpose: settlement journal, postings, settlement attempts, and the trade-to-journal relationship.
- Confirmed V1 and V2 were not edited.
  - `git diff` on both files is empty.

### 3.2.2 Create the settlement journal table

- Table: `settlement_journal`
  - `id UUID PRIMARY KEY`
  - `trade_id UUID NOT NULL` unique, FK to `trade(id)` — I2, one journal per trade
  - `settled_at TIMESTAMPTZ NOT NULL`
  - `UNIQUE (id, trade_id)` — referenced by the same-trade composite FK from `trade`

### 3.2.3 Create the posting table

- Table: `posting`
  - `journal_id` FK to `settlement_journal`
  - `account_id` FK to `account`
  - `direction` in `DEBIT`, `CREDIT`
  - `amount > 0`
  - unique `(journal_id, account_id)`
  - generated `signed_amount`: `DEBIT` → `-amount`, `CREDIT` → `+amount`
- No asset column. DEBIT/CREDIT are project-local balance-movement directions, not GAAP.

### 3.2.4 Create the settlement attempt table

- Table: `settlement_attempt`
  - `trade_id` FK to `trade`
  - `command_key` unique FK to `command_result`
  - `outcome` in `SETTLED`, `ALREADY_SETTLED`, `NOT_DUE`, `INSUFFICIENT_CASH`, `INSUFFICIENT_SECURITIES`
  - `journal_id` present exactly when outcome is `SETTLED` or `ALREADY_SETTLED`
  - `business_date` and `decided_at` `NOT NULL`
  - partial unique index: at most one `SETTLED` attempt per trade
- `MISSING_ACCOUNT` is not an allowed outcome. Inserting it fails `settlement_attempt_outcome_supported`.

### 3.2.5 Extend the trade and command-result tables

- Widened `trade_status_supported` to `READY`, `SETTLED`.
- Added nullable unique `trade.journal_id` FK to `settlement_journal`.
- `CHECK ((status = 'SETTLED') = (journal_id IS NOT NULL))`.
- Composite FK `trade (journal_id, id) → settlement_journal (id, trade_id)` so Trade A cannot point at Trade B's journal.
- Widened `command_result_operation_supported` to `CAPTURE_TRADE`, `SETTLE_TRADE`.
- Existing V2 rows were not rewritten. Upgrade test: captured `READY` trade stays `READY` with `journal_id IS NULL`.

### 3.2.6 Protect settlement history from modification

- `settlement_history_immutable` rejects `UPDATE` and `DELETE` on `settlement_journal`, `posting`, and `settlement_attempt`.
- `trade_protect_settlement_state` rejects captured-term edits and any update of a `SETTLED` row. The only allowed update is `READY → SETTLED` that also sets `journal_id`.
- Deferred constraint trigger `settlement_journal_shape` at commit:
  - exactly four postings
  - buyer/seller AUD cash DEBIT/CREDIT of `cash_amount`
  - buyer/seller accounts for exactly `trade.security_id` CREDIT/DEBIT of `quantity`
  - per-asset signed net is zero
- `TRUNCATE` still clears the tables because it does not fire row-level triggers.

### 3.2.7 Verify V3 and preserve Phase 1 and Phase 2 state

- Updated `AbstractPostgresIntegrationTest` truncate list to include `settlement_attempt`, `posting`, `settlement_journal`.
- Updated `PostgresStartupIntegrationTest` to V1+V2+V3 and the nine public tables.
- Added `SettlementSchemaIntegrationTest` covering 3.2.2–3.2.6.
- Added `FlywayV3UpgradeIntegrationTest`: V2 database with seed + captured trade/command result upgrades to V3 without changing those rows; new settlement tables start empty.

Deviations and corrections:

- `FlywayV2UpgradeIntegrationTest` now pins Flyway `target` to version 2. Without that pin, adding V3 would make the V1→V2 test apply V3.
- `TradeCaptureSchemaIntegrationTest.unsupportedStatusIsRejected` now inserts `CANCELLED` instead of `SETTLED`, because V3 accepts `SETTLED` when `journal_id` is set.
- Updating `posting.signed_amount` is rejected as invalid SQL (`BadSqlGrammarException`) because it is a generated column. The immutability trigger still rejects `UPDATE posting SET amount`.
- First `SettlementSchemaIntegrationTest` compile failed: `Autowired` must be imported from `org.springframework.beans.factory.annotation`. Fixed.
- No settlement services, repositories, controllers, or Java domain types were added.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 125. Failures: 0. Errors: 0. Skipped: 0.
- Section 3.2 is complete.
- Stopped here. Section 3.3 was not started.

## 3.3 Settlement domain types

### 3.3.1 Extend the trade domain for the settled state

- Added `SETTLED` to `TradeStatus`.
- Added nullable `journalId` to `Trade`.
- `TradeTerms` was not changed.
- Updated `TradeRepository` to select and map `journal_id`. Insert still stores `READY` with a null journal.
- `TradeResponse` still omits `journalId`. That HTTP field belongs to a later API step.
- Tests: `TradeDomainTest` READY with null journal and SETTLED with a journal; `TradeRepositoryIntegrationTest` asserts inserted trades have `journalId == null`.

### 3.3.2 Create the journal and posting domain types

- Added `SettlementJournal` (`id`, `tradeId`, `settledAt`).
- Added `Posting` (`id`, `journalId`, `accountId`, `direction`, `amount`, `signedAmount`).
- Added `PostingDirection` (`DEBIT`, `CREDIT`) with the approved project-local meaning: DEBIT decreases the balance, CREDIT increases it. Not GAAP.
- Types are immutable records with no persistence annotations.

### 3.3.3 Create the settlement attempt and outcome types

- Added `SettlementOutcome`: `SETTLED`, `ALREADY_SETTLED`, `NOT_DUE`, `INSUFFICIENT_CASH`, `INSUFFICIENT_SECURITIES`.
- `MISSING_ACCOUNT` is not a value.
- Added `SettlementAttempt` (`id`, `tradeId`, `commandKey`, `outcome`, `journalId`, `businessDate`, `decidedAt`).
- `SettlementOutcomeIntegrationTest` reads `settlement_attempt_outcome_supported` and asserts every enum name is accepted and `MISSING_ACCOUNT` is not.

### 3.3.4 Create the settlement command and its request identity

- Added `SettleCommand` (`idempotencyKey`, `tradeId`).
- Added `SettleRequestIdentity` (`SETTLE_TRADE` + trade id).
- Extracted package-private `RequestIdentityEncoding` so capture and settlement share one length-prefixed encoder.
- `CaptureRequestIdentity` now delegates to that helper. Output is unchanged.
- Regression: `CaptureRequestIdentityTest.phase2CaptureIdentityEncodingIsUnchanged` asserts the exact Phase 2 string for T-001 / Alice / Bob / EQ1 / 10 / 50000 / 2026-09-20.
- `SettleRequestIdentityTest` asserts same-trade identity sharing, different-trade difference, and no collision with capture identity.

- No settlement repositories, services, controllers, locking, or balance mutation.
- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 134. Failures: 0. Errors: 0. Skipped: 0.
- Section 3.3 is complete.
- Stopped here. Section 3.4 was not started.

## 3.4 Settlement JDBC persistence

Repositories provide explicit SQL. They do not own the settlement transaction. No `SettleTradeService` or settlement controllers were added. No Java-level locking. No concurrency race tests.

### 3.4.1 Trade locking and the settled transition

- Extended `TradeRepository`.
  - `lockById(UUID)`: `SELECT ... FROM trade WHERE id = :tradeId FOR UPDATE`.
    - Single-table lock. Serializes settlement of the same trade before any account is touched.
    - Unknown id returns empty.
  - `markSettled(UUID tradeId, UUID journalId)`: `UPDATE trade SET status = 'SETTLED', journal_id = :journalId WHERE id = :tradeId AND status = 'READY'`.
    - Conditional write: the `READY` guard prevents a second settle even if a check above it is removed.
    - Requires exactly one updated row. Otherwise `IncorrectResultSizeDataAccessException`.
  - No method changes captured terms.

- Tests: `TradeRepositoryIntegrationTest` lock read / unknown id; `TradeSettlementPersistenceIntegrationTest` `READY → SETTLED` and already-settled failure.

### 3.4.2 Account resolution, row locking, and relative balance updates

- Extended `AccountRepository`.
  - `findIdByParticipantAndAsset`: `SELECT id FROM account WHERE participant_id AND asset_id`. Resolves one of the four accounts without locking.
  - `lockBalance(UUID)`: `SELECT id, asset_id, current_balance FROM account WHERE id = :accountId FOR UPDATE`.
    - Single-table query. Does not reuse the joined `findById` read, which would also lock `participant` and `asset`.
  - `applyDelta(UUID, long)`: `UPDATE account SET current_balance = current_balance + :delta WHERE id = :accountId`.
    - Relative only. Exact one-row requirement.
  - No absolute balance setter.
- Added `LockedAccount` (`id`, `assetId`, `currentBalance`) for the locked row.
- Extended `AssetRepository.findByCode(String)` so AUD can be resolved without knowing its id.

- Tests: `AccountSettlementPersistenceIntegrationTest` — Alice/Bob AUD and EQ1 resolution, unknown pair empty, AUD by code, locked balance, increase/decrease deltas, unknown-account exact-row failure, negative delta rejected by `account_current_balance_non_negative`, no setter method on the repository.

### 3.4.3 SettlementJournalRepository

- Added `src/main/java/com/jasonwidjaja/dvp/persistence/SettlementJournalRepository.java`.
  - `insertJournal(tradeId)` generates the journal UUID in Java and returns the inserted row.
  - `insertPostings(journalId, postings)` requires list size 4, inserts each posting, and requires exactly four affected rows.
  - `findJournalById`, `findJournalByTradeId`.
  - `findPostingsByJournalId` orders by `asset.code`, `posting.direction`, `posting.account_id`.
  - No update or delete methods.

- JDBC `Instant` cannot be bound by the PostgreSQL driver (`Can't infer the SQL type`). Inserts bind `OffsetDateTime` in UTC; reads map `OffsetDateTime` back to `Instant`.

- Tests wrap `insertJournal` + four postings in `TransactionTemplate` because the deferred `settlement_journal_shape` trigger fires at commit. The repository still does not begin or commit that transaction.

### 3.4.4 SettlementAttemptRepository

- Added `src/main/java/com/jasonwidjaja/dvp/persistence/SettlementAttemptRepository.java`.
  - `insert(tradeId, commandKey, outcome, journalId, businessDate)` generates the attempt UUID in Java.
  - `journalId` may be null for rejections; `HashMap` is used because `Map.of` rejects null.
  - `findByTradeId` orders by `decided_at`, then `id`.
  - No update or delete methods.
  - `MISSING_ACCOUNT` is not an outcome and is not written.

### 3.4.5 CommandResultRepository SETTLE_TRADE

- Changed `claim` from `claim(commandKey, requestIdentity)` to `claim(commandKey, operation, requestIdentity)`.
  - Capture call site: `CaptureTradeService` now passes `CaptureRequestIdentity.OPERATION`.
  - Settlement tests pass `SettleRequestIdentity.OPERATION`.
- `findByCommandKey` and exact `finalize` are unchanged.
- Identity comparison remains string equality on stored `request_identity`.
- Call sites updated: `CaptureTradeService`, `CommandResultRepositoryIntegrationTest`.

### 3.4.6 Persistence-layer verification

- Added PostgreSQL integration tests on the existing Testcontainers PostgreSQL 18.6 setup and shared `TRUNCATE` cleanup:
  - `TradeSettlementPersistenceIntegrationTest`
  - `AccountSettlementPersistenceIntegrationTest`
  - `SettlementJournalRepositoryIntegrationTest`
  - `SettlementAttemptRepositoryIntegrationTest`
  - Command-result settlement claim tests in `CommandResultRepositoryIntegrationTest`
  - Trade lock tests in `TradeRepositoryIntegrationTest`

Deviations and corrections:

- `insertPostings` uses four individual `jdbc.update` calls rather than `batchUpdate`. PostgreSQL JDBC batch results can return `SUCCESS_NO_INFO` (`-2`), which cannot prove exactly four affected rows.
- First `./mvnw verify` failed: 1 failure and 10 errors. Cause: binding `java.time.Instant` to `TIMESTAMPTZ`. Fixed by binding `OffsetDateTime` at UTC. Re-ran verify.
- Journal + four postings are committed together in tests because of the deferred shape trigger. That is test/transaction-boundary usage, not repository-owned settlement.
- No competing-transaction lock tests. Those belong to Phase 4.
- No `SettleTradeService`, settlement controllers, `Clock`, or `BusinessCalendar`.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 159. Failures: 0. Errors: 0. Skipped: 0.
- Section 3.4 is complete.
- Stopped here. Section 3.5 was not started.

## 3.5 Settlement transaction, locking and validation

`SettleTradeService` owns the settlement PostgreSQL transaction through `TransactionTemplate` and the existing Spring JDBC `PlatformTransactionManager`. Isolation is the default. No settlement controller was added. Successful settlement journal, posting, balance and `SETTLED` writes were not added.

### 3.5.1 Service-owned settlement transaction boundary

- Added `SettleTradeService` in the application package.
  - Injects the same `PlatformTransactionManager` used by capture.
  - Wraps settlement in `TransactionTemplate`.
  - Does not set a non-default isolation level. Correctness is row locking (ADR-007).
- Repositories used inside the transaction: `CommandResultRepository`, `TradeRepository`, `AccountRepository`, `AssetRepository`, `SettlementAttemptRepository`.
- `SettlementJournalRepository` is not injected. Journal writes belong to 3.6.
- Added `TimeConfiguration` with one `Clock` bean: `Clock.system(Australia/Sydney)`.
- Added `BusinessCalendar` which derives `businessDate()` as `LocalDate.now(clock)`. Settlement code does not call `LocalDate.now()` directly.

### 3.5.2 Settlement command claim and replay

- First step inside the transaction: `SettleRequestIdentity.of(tradeId)` then `claim(key, SETTLE_TRADE, identity)`.
- Trade is not read or locked before the claim.
- Replay:
  - same identity + completed result → return stored status, body and location; write nothing
  - same identity + unfinished result → `IllegalStateException`
  - different identity → `409 IDEMPOTENCY_KEY_CONFLICT`; original row unchanged; no attempt

### 3.5.3 Trade lock, state check and due-date rule

- After a successful claim, `lockById`.
- Unknown trade → `UnknownTradeException` (existing handler maps this to `404 UNKNOWN_TRADE`). Claim rolls back. No attempt.
- Locked `SETTLED` trade → `409 ALREADY_SETTLED` attempt linked to the existing journal. No balance change.
- C2: due when `settlementDate <= businessDate`. Overdue remains settleable. `businessDate` is stored on every committed attempt.
- Future-dated trade → `422 NOT_DUE`, trade stays `READY`.

### 3.5.4 Four-account resolution

- Cash asset: `AssetRepository.findByCode("AUD")` and type `CASH`.
- Four ids without locking: buyer+AUD, seller+AUD, buyer+security, seller+security.
- Distinctness asserted (`HashSet` size 4).
- Missing required account → `SettlementIntegrityException`. Transaction rolls back. No attempt. No durable command result. Existing `Exception` handler will surface `500 INTERNAL_ERROR`. `MISSING_ACCOUNT` is not recorded.

### 3.5.5 Deterministic account locking

- Sort the four ids with `UUID::compareTo`.
- Lock one at a time with `lockBalance`. No `ORDER BY ... FOR UPDATE`.
- Observed Alice/Bob lock order: trade, then `...0aa` Alice AUD, `...0ae` Alice EQ1, `...0ba` Bob AUD, `...0be` Bob EQ1.
- Resolve events are recorded before any account lock.
- No Java `synchronized` / `Lock` on the settlement path.
- Competing-transaction behaviour is left to Phase 4.

### 3.5.6 Post-lock cash and securities validation

- Buyer cash first: locked `currentBalance >= cashAmount`.
- Then seller securities: locked `currentBalance >= quantity`.
- Both insufficient → `INSUFFICIENT_CASH`.
- Rejections commit an attempt + durable `422`, leave the trade `READY`, and change no balances, journals or postings.
- No reservations.
- A due, sufficiently funded trade reaches this point and then throws `SuccessfulSettlementNotImplementedException`, rolling the claim back. Journal/posting/balance/`SETTLED` writes belong to 3.6.

Deviations and corrections:

- Valid due/overdue/exactly-sufficient trades are not persisted as `SETTLED` in this section. Tests prove they are not rejected as `NOT_DUE` / `INSUFFICIENT_*` and that they reach the 3.6 write boundary.
- `SettlementJournalRepository` is omitted from the service until 3.6.
- First `./mvnw verify` failed to start the new Spring test contexts: a test `@Bean Clock clock()` collided with production bean name `clock` (`BeanDefinitionOverrideException`). Renamed the test bean to `fixedClock` and marked it `@Primary`. Re-ran verify.
- `UnknownTradeException` was made public so the application service can throw the same type the existing HTTP handler already maps to `404`.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 179. Failures: 0. Errors: 0. Skipped: 0.
- Section 3.5 is complete.
- Stopped here. Section 3.6 was not started.

## 3.6 Settlement writes and durable outcome

Successful settlement writes now happen in the same `TransactionTemplate` transaction as the 3.5 claim/lock/validate protocol. `SuccessfulSettlementNotImplementedException` was removed. No REST settlement endpoints were added.

Write order after post-lock validation:

```text
insert journal
→ insert four postings
→ apply four relative deltas in lock order
→ markSettled
→ insert SETTLED attempt
→ finalize 201 + Location /v1/journals/{journalId}
```

### 3.6.1 Create exactly one settlement journal

- Injected `SettlementJournalRepository` into `SettleTradeService`.
- `insertJournal(tradeId)` runs only on the approved success path.
- Rejection paths still create no journal. Unique `trade_id` remains the database authority.

### 3.6.2 Create exactly four postings

- Built from trade terms and resolved accounts:
  - buyer AUD `DEBIT` `cashAmount`
  - seller AUD `CREDIT` `cashAmount`
  - buyer security `CREDIT` `quantity`
  - seller security `DEBIT` `quantity`
- Inserted through `insertPostings`, which requires exactly four affected rows.
- No fifth, netted or zero-amount posting.
- Malformed journal shape is still rejected at commit by the V3 deferred trigger (proven in 3.2).

### 3.6.3 Update the four account balances

- Relative `applyDelta` only:
  - buyer cash `-cashAmount`
  - seller cash `+cashAmount`
  - buyer security `+quantity`
  - seller security `-quantity`
- Applied with the same `orderedAccountIds` helper used for locking (ascending UUID).
- Observed Alice/Bob T-001 (10 EQ1 for 50000):
  - Alice AUD 50000, Alice EQ1 10, Bob AUD 50000, Bob EQ1 0
  - opening balances unchanged
  - total AUD 100000, total EQ1 10

### 3.6.4 Transition READY to SETTLED

- `markSettled(tradeId, journalId)` after the deltas.
- Captured terms unchanged.
- A second `markSettled` fails with `IncorrectResultSizeDataAccessException`.

### 3.6.5 SETTLED attempt and durable command outcome

- Added `SettlementResponse` (`tradeId`, `status`, `outcome`, `journalId`, `settledAt`).
- Finalize `201`, that JSON body, `Location: /v1/journals/{journalId}`.
- One `SETTLED` attempt linked to the journal, with the evaluated `businessDate`.
- Outcome is returned only after `TransactionTemplate` completes.
- Replay returns the identical status, body and location and writes nothing new.

### 3.6.6 Complete successful settlement

- `SettleTradeServiceIntegrationTest.aliceBobSettlementProducesTheApprovedFinancialState` asserts the full state set against Testcontainers PostgreSQL 18.6.
- Today-dated, overdue and exactly sufficient trades now settle instead of hitting the 3.5 stub.

### 3.6.7 Rollback before commit

- Added `SettleTradeWriteRollbackIntegrationTest`.
- Test-only package-visible non-final `@Primary` `TradeRepository` decorator throws on the first `markSettled`, after journal, postings and deltas, before the trade transition and finalize.
- No production failure hook.
- After the failed call, queried from outside the service transaction:
  - no journal, posting, attempt or `command_result`
  - trade still `READY` with `journalId` null
  - current and opening balances unchanged
  - `current_balance = opening_balance + sum(signed_amount)`
- Retry of the same key then settles exactly once; a second replay adds no financial work.

Deviations and corrections:

- Postings still use four individual `jdbc.update` calls via `insertPostings` rather than JDBC `batchUpdate`, as in 3.4, so affected-row counts are real.
- A deliberately malformed posting set is not written by the service. The V3 deferred shape trigger already covers that failure at commit.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 182. Failures: 0. Errors: 0. Skipped: 0.
- Section 3.6 is complete.
- Stopped here. Section 3.7 was not started.

## 3.7 Settlement and financial inspection API

Settlement and inspection are now reachable over HTTP. Controllers translate requests into existing services and repositories. They do not contain SQL, locking, balance validation, transaction logic or idempotency persistence.

Observed Alice/Bob HTTP workflow (due T-001, 10 EQ1 for 50000):

```text
GET  /v1/accounts
POST /v1/trades                         capture-T-001 → 201 READY, journalId null
POST /v1/trades/{id}/settle             settle-T-001 → 201, Location /v1/journals/{id}
GET  Location                           one journal, four postings
GET  /v1/trades/{id}                    SETTLED with journal id
GET  /v1/trades/{id}/attempts           one SETTLED attempt
GET  /v1/commands/settle-T-001          stored 201 + journal location
GET  /v1/accounts                       Alice AUD 50000, Alice EQ1 10, Bob AUD 50000, Bob EQ1 0
replay settle-T-001                     identical status, body and Location; balances unchanged
```

### 3.7.1 Create `POST /v1/trades/{id}/settle`

- Added `POST /v1/trades/{id}/settle` on `TradeController`.
- Requires exactly one `Idempotency-Key` via existing `IdempotencyKey.requireExactlyOne`.
- No request body. A non-empty body is `400 INVALID_REQUEST`.
- Builds `SettleCommand` and returns `SettleTradeService` status, stored JSON and `Location`.
- Missing, empty, duplicate or too-long keys are `400 INVALID_IDEMPOTENCY_KEY`.
- Malformed trade UUID is `400 INVALID_REQUEST`.
- Replay of a successful settle returns the identical `201` body and journal `Location`.

### 3.7.2 Create `GET /v1/journals/{id}`

- Added `JournalController` with `GET /v1/journals/{id}` only. No journal or posting write API.
- Response: `id`, `tradeId`, `settledAt`, `postings[]` with `id`, `accountId`, participant `{id,name}`, asset `{id,code,type}`, `direction`, `amount`.
- Postings use `SettlementJournalRepository` order (`asset.code`, `direction`, `account_id`).
- Alice/Bob journal:

```text
Bob AUD CREDIT 50000
Alice AUD DEBIT 50000
Alice EQ1 CREDIT 10
Bob EQ1 DEBIT 10
```

- Unknown valid UUID → `404 UNKNOWN_JOURNAL` `"Journal does not exist"`.
- Malformed UUID → `400 INVALID_REQUEST`.
- `signedAmount` is not exposed.

### 3.7.3 Create `GET /v1/trades/{id}/attempts`

- Added `GET /v1/trades/{id}/attempts` returning `SettlementAttemptResponse` in decision order: `id`, `outcome`, `journalId`, `businessDate`, `decidedAt`, `commandKey`.
- Known trade with no attempts → `[]`.
- Unknown trade → `404 UNKNOWN_TRADE`.
- Added `journalId` to `TradeResponse`: `null` while `READY`, journal id once `SETTLED`.
- `GET /v1/commands/{key}` still embeds the stored `command_result.response_body` as parsed JSON and does not rewrite old rows.

### 3.7.4 Create `GET /v1/commands/{key}`

- Added `CommandController`.
- Response: `commandKey`, `operation`, `httpStatus`, `location`, `response` (parsed JSON, not an escaped string).
- Only completed results are readable.
- Key format uses the same `IdempotencyKey` rules.
- `request_identity` is not exposed.
- Capture key returns stored `201` + trade location.
- Settlement key returns stored `201` + journal location.
- Durable rejection (e.g. `409 ALREADY_SETTLED`) returns the stored outcome.
- Unknown key → `404 UNKNOWN_COMMAND` `"Command does not exist"`.

### 3.7.5 Extend API error handling for settlement

- Added `UnknownJournalException` → `404 UNKNOWN_JOURNAL`.
- Added `UnknownCommandException` → `404 UNKNOWN_COMMAND`.
- Phase 2 mappings unchanged (`INVALID_IDEMPOTENCY_KEY`, `UNKNOWN_TRADE`, `MALFORMED_REQUEST`, `INVALID_REQUEST`, `UNSUPPORTED_MEDIA_TYPE`, `NOT_FOUND`, `INTERNAL_ERROR`).
- Service-produced settlement outcomes are returned as stored, not remapped by `ApiExceptionHandler`:
  - `409 IDEMPOTENCY_KEY_CONFLICT`
  - `409 ALREADY_SETTLED`
  - `422 NOT_DUE`
  - `422 INSUFFICIENT_CASH`
  - `422 INSUFFICIENT_SECURITIES`
- Missing required account remains `SettlementIntegrityException` → `500 INTERNAL_ERROR` `"An unexpected error occurred"`. No durable command result.
- `DataIntegrityViolationException` with constraint/trigger names maps to the same safe `500`. Message and body contain no SQL, credentials, constraint names, trigger names or stack traces.

### 3.7.6 Verify the HTTP settlement workflow

- Added `SettlementHttpIntegrationTest` against Testcontainers PostgreSQL 18.6.
- Workflow proved: one journal, four postings, balances moved exactly once, trade `SETTLED` with journal id, one `SETTLED` attempt, identical replay.
- HTTP rejections leave financial state unchanged:
  - already settled under a new key
  - not due
  - insufficient cash
  - insufficient securities
  - unknown trade
  - reused key against a different trade
- `/v3/api-docs` automatically contains:
  - `POST /v1/trades/{id}/settle`
  - `GET /v1/journals/{id}`
  - `GET /v1/trades/{id}/attempts`
  - `GET /v1/commands/{key}`
- No `@Operation`, `@ApiResponse`, `@Schema`, examples, custom YAML, grouping or Swagger-specific application code.

Deviations and corrections:

- New capture command-result bodies now include `"journalId":null` because `CaptureTradeService` serializes `TradeResponse.from()`. That keeps POST capture and `GET /v1/trades/{id}` equal for `READY` trades. `GET /v1/commands/{key}` still returns the stored JSON as-is and never rewrites an existing `command_result` row.
- Settle rejects a non-empty body by reading `HttpServletRequest` instead of declaring `@RequestBody`, so OpenAPI does not invent a settle request schema and the controller still contains no settlement decision logic.
- A real deferred journal-shape failure at HTTP commit is not forced through the success path. HTTP coverage uses `@MockitoBean SettleTradeService` throwing `DataIntegrityViolationException` with a constraint/trigger name; V3 already proves the trigger at commit in 3.2.

- Ran `./mvnw verify`.
  - Result: `BUILD SUCCESS`.
  - Tests run: 190. Failures: 0. Errors: 0. Skipped: 0.
- Section 3.7 is complete.
- Stopped here. Section 3.8 was not started.








