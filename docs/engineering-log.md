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






