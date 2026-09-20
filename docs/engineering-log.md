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





