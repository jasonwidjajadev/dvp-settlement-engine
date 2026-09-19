# Repository Instructions

## Repository

* DvP Settlement and Reconciliation Engine.
* Java 21 backend using Spring Boot, Spring JDBC and PostgreSQL.
* Base package: `com.jasonwidjaja.dvp`.
* Maven group ID: `com.jasonwidjaja`.
* Maven artifact ID: `dvp-settlement-engine`.

Keep this file concise. Detailed product requirements, architecture reasoning, phase plans and implementation history belong in the documents below.

## Sources of truth

* `docs/project-spec.md`

  * Defines product behaviour, features, scope, invariants, non-goals and definition of done.

* `docs/decisions.md`

  * Defines accepted architecture decisions and their rationale.
  * Do not reopen these decisions during implementation unless a conflict is explicitly raised.

* `docs/implementation-plan.md`

  * Defines the high-level implementation phases and dependency order.

* `docs/detailed-plan/phase-N.md`

  * Defines the detailed approved plan for one implementation phase.
  * The detailed plan explains what will be done, why, how it fits into the repository and how each step will be verified.

* `docs/engineering-log.md`

  * Records what actually happened during implementation.
  * Uses the same phase and step numbering as the corresponding detailed phase plan.

Document ownership:

```text
project-spec.md
→ WHAT the product must do

decisions.md
→ WHY major architecture choices were made

implementation-plan.md
→ HIGH-LEVEL implementation sequence

detailed-plan/phase-N.md
→ DETAILED plan for the current phase

engineering-log.md
→ WHAT actually happened
```

Do not duplicate information already owned by another authoritative document.

## Phase workflow

For every implementation phase:

1. Inspect the current repository state.
2. Read the project specification, architecture decisions and implementation plan.
3. Create or review `docs/detailed-plan/phase-N.md`.
4. Break the phase into numbered tasks and subtasks such as:

   * `1.1`
   * `1.1.1`
   * `1.1.2`
   * `1.2`
5. The detailed phase plan must explain unfamiliar repository-specific concepts before they are implemented.
6. Obtain human approval of the detailed phase plan before implementation begins.
7. Implement only the approved current phase.
8. Follow the detailed phase plan unless the actual repository requires a justified deviation.
9. Record actual work in `docs/engineering-log.md` using the same numbering.
10. Record deviations, failures, fixes and verification results.
11. Run the required verification before declaring the phase complete.
12. Stop when the phase is complete.
13. Do not automatically begin the next phase.

If the repository state conflicts with an approved document, flag the conflict instead of silently changing behaviour or architecture.

## Detailed phase plans

`docs/detailed-plan/phase-N.md` is an executable, annotated todo list for one phase.

Use:

```text
Phase
└── task
    └── subtask
        ├── action
        ├── explanation
        ├── reason
        └── verification
```

For example:

```text
1.2 Java and Maven foundation
    1.2.1 Verify Java 21
    1.2.2 Configure pom.xml
    1.2.3 Add Maven Wrapper
    1.2.4 Verify Maven
```

For each meaningful subtask, explain:

* what is being introduced
* what it is
* why the project needs it
* how it fits into this repository
* files, dependencies or configuration involved
* work that must be performed
* how the result will be verified
* what must be true before proceeding

Use explanations tied to this repository rather than generic tutorials.

Do not implement while preparing a detailed phase plan.

## Engineering log

`docs/engineering-log.md` records completed work, not future plans.

Mirror the numbering from the detailed phase plan.

Example:

```markdown
### 1.2 Java and Maven foundation

#### 1.2.2 Configure pom.xml

- Added `pom.xml`.
  - Defines the Maven project.
  - Configured Java 21.
  - Set the group ID to `com.jasonwidjaja`.
  - Set the artifact ID to `dvp-settlement-engine`.

- Added Spring JDBC.
  - Provides explicit SQL-based PostgreSQL access.
  - Required because this project deliberately does not use JPA/Hibernate.

- Ran `./mvnw validate`.
  - Purpose: verify the Maven project configuration.
  - Result: passed.
```

Record:

* actions actually performed
* important files created or modified
* important configuration choices
* commands run
* tests and verification performed
* actual results
* failures encountered
* causes where known
* fixes applied
* deviations from the detailed plan and why

Do not erase failed attempts after they are fixed.

Do not claim an unrun or skipped check passed.

## Engineering conventions

* Use Java 21.
* Use Spring Boot and Spring MVC.
* Use Spring JDBC with `NamedParameterJdbcTemplate`.
* Keep SQL explicit and parameterized.
* Use PostgreSQL as the transactional store.
* Use Flyway for database migrations.
* Store migrations under `src/main/resources/db/migration/`.
* Use JUnit and AssertJ for tests.
* Use Testcontainers for PostgreSQL integration behaviour.
* Test PostgreSQL-specific transaction, locking and constraint behaviour against PostgreSQL rather than an in-memory substitute.
* Create packages and abstractions when they are needed by the current phase.
* Do not prebuild empty layers for future work.

## Build and verification

Once the Maven project exists:

* Build and run accumulated verification with:

```bash
./mvnw verify
```

* Use a clean verification when required:

```bash
./mvnw clean verify
```

* Run checks relevant to the current change while implementing.
* Run the phase-level verification before declaring a phase complete.
* Do not bypass failing required tests to make a phase appear complete.

## Architecture constraints

Do not introduce:

* JPA
* Hibernate ORM
* Kafka into the core project
* microservices
* Kubernetes
* frontend work
* event sourcing
* distributed database transactions

The transactional outbox is optional and may only be considered after the core project is complete as defined by the project specification.

Do not:

* add product features not defined in `docs/project-spec.md`
* silently change accepted architecture decisions
* add speculative infrastructure for possible future requirements
* skip ahead into later phases
* create arbitrary financial-state mutation paths

## Documentation rules

* Keep `AGENTS.md` limited to durable repository-wide instructions.
* Keep detailed implementation planning under `docs/detailed-plan/`.
* Keep implementation history in `docs/engineering-log.md`.
* Keep architecture rationale in `docs/decisions.md`.
* Keep product behaviour and scope in `docs/project-spec.md`.
* Keep the high-level implementation sequence in `docs/implementation-plan.md`.
* Prefer references to authoritative documents instead of copying their contents.
* Add new `AGENTS.md` rules when a stable repository convention emerges or a repeated agent mistake shows that a durable instruction is missing.
