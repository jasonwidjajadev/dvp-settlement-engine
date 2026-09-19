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
