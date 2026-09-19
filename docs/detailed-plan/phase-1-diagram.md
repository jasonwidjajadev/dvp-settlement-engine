# Diagram 

- Java 21 → language/version
- Temurin OpenJDK JDK → provides the Java compiler and runtime
- JAVA_HOME → tells tools which JDK to use
- Maven → builds the project and manages libraries
- pom.xml → tells Maven what this project needs
- Spring Boot → framework that runs your backend
- Spring JDBC → lets your Java application execute SQL
- PostgreSQL JDBC Driver → actual bridge between Java and PostgreSQL
- PostgreSQL → durable source of truth
- Flyway → creates/version-controls PostgreSQL structure
- seed-demo.sql → creates known example data
- AccountRepository → Java code that reads the database
- Testcontainers → starts disposable real PostgreSQL for tests
- ./mvnw verify → puts the whole Phase 1 stack through automated verification


## Phase 1 FOUNDATION

- 1.1 repo setup  → no app logic
- 1.2 Java + Maven exist → no app logic → ./mvnw -v
- 1.3 actual Java application exists → only application startup
    - → ./mvnw compile
    - → ./mvnw verify

- 1.4 → infrastructure/configuration, database configuration exists
    - → ./mvnw spring-boot:run
    - → Spring starts normally and connects to PostgreSQL

- 1.5 Flyway schema → database structure
- 1.6 seed data → example database rows
- 1.7 Spring JDBC → FIRST real Java data-access logic
       - Account.java
       - Participant.java
       - Asset.java
       - AccountRepository.java
       - SELECT accounts from PostgreSQL
       - map database rows → Java objects

- 1.8 tests → verify the above
- 1.9 final Phase 1 verification

- later, Phase 2 first real REST endpoint exists
    - → curl endpoint
    - → receive actual application response

   

```txt

PHASE 1: JAVA + POSTGRESQL FOUNDATION
================================================================================

                         YOUR MAC / DEVELOPMENT MACHINE
                                      │
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1.1 REPOSITORY SETUP                                                        │
│                                                                             │
│ dvp-settlement-engine/                                                      │
│                                                                             │
│ ├── AGENTS.md                                                               │
│ │   └── instructions for coding agents                                      │
│ │                                                                           │
│ ├── docs/                                                                   │
│ │   ├── project-spec.md                                                     │
│ │   │   └── WHAT the finished project must do                              │
│ │   │                                                                       │
│ │   ├── decisions.md                                                        │
│ │   │   └── WHY important architecture choices were made                   │
│ │   │                                                                       │
│ │   ├── implementation-plan.md                                              │
│ │   │   └── HIGH-LEVEL implementation phases                               │
│ │   │                                                                       │
│ │   ├── detailed-plan/phase-1.md                                            │
│ │   │   └── EXACT TODO LIST for Phase 1                                    │
│ │   │                                                                       │
│ │   └── engineering-log.md                                                  │
│ │       └── WHAT ACTUALLY HAPPENED                                         │
│ │                                                                           │
│ └── .gitignore                                                              │
│     └── prevents generated files / secrets being committed                  │
│                                                                             │
│ PURPOSE                                                                     │
│ Establish the rules and repository state before writing application code.   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │
                                      ▼
================================================================================
                        1.2 JAVA + MAVEN FOUNDATION
================================================================================


                           JAVA ECOSYSTEM
                                │
          ┌─────────────────────┴──────────────────────┐
          │                                            │
          ▼                                            ▼
┌───────────────────────┐                    ┌────────────────────────┐
│ JAVA LANGUAGE         │                    │ JDK                    │
│                       │                    │                        │
│ The language you      │                    │ Java Development Kit   │
│ write code in.        │                    │                        │
│                       │                    │ Contains:              │
│ Versions include:     │                    │                        │
│                       │                    │ java                   │
│ 17                    │                    │ └── runs Java programs │
│ 21  ← WE USE THIS     │                    │                        │
│ 23                    │                    │ javac                  │
│ 25                    │                    │ └── compiles .java     │
└───────────────────────┘                    │                        │
                                             │ standard libraries     │
                                             └───────────┬────────────┘
                                                         │
                                                         ▼
                                             ┌────────────────────────┐
                                             │ OPENJDK                │
                                             │                        │
                                             │ Open-source Java       │
                                             │ implementation.        │
                                             │                        │
                                             │ Different vendors      │
                                             │ package OpenJDK:       │
                                             │                        │
                                             │ Eclipse Temurin        │
                                             │   ← YOUR JAVA 21       │
                                             │                        │
                                             │ Amazon Corretto        │
                                             │ Azul Zulu              │
                                             │ Oracle builds          │
                                             └────────────────────────┘


YOUR MACHINE CURRENTLY HAS:

    Eclipse Temurin OpenJDK 21.0.3
                │
                │ installed at
                ▼
    /Library/Java/JavaVirtualMachines/
        temurin-21.jdk/Contents/Home


JAVA_HOME
──────────────────────────────────────────────────────────────────────────────

JAVA_HOME is NOT Java itself.

It is an environment variable that tells tools:

    "Use THIS JDK."

Example:

    JAVA_HOME=
    /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

Why it matters on your machine:

                         YOUR MAC
                            │
               ┌────────────┴────────────┐
               │                         │
               ▼                         ▼
       Temurin Java 21           Homebrew OpenJDK 23
          WE WANT                     ALSO EXISTS
               │
               └────────────┐
                            │
                      JAVA_HOME
                            │
                            ▼
                     Maven uses
                     Java 21 ✓


                                      │
                                      │ Java exists
                                      ▼

                           ┌───────────────────────┐
                           │ MAVEN                 │
                           │                       │
                           │ Build + dependency    │
                           │ management tool.      │
                           │                       │
                           │ NOT part of Java.     │
                           │                       │
                           │ Maven USES the JDK.   │
                           └───────────┬───────────┘
                                       │
                                       │ reads
                                       ▼
                           ┌───────────────────────┐
                           │ pom.xml               │
                           │                       │
                           │ Project configuration │
                           │ for Maven.            │
                           │                       │
                           │ Defines:              │
                           │                       │
                           │ Java 21               │
                           │ project name          │
                           │ dependencies          │
                           │ plugins               │
                           │ build behaviour       │
                           └───────────┬───────────┘
                                       │
                 ┌─────────────────────┼─────────────────────────┐
                 │                     │                         │
                 ▼                     ▼                         ▼
       DOWNLOAD DEPENDENCIES      COMPILE JAVA              RUN TESTS
                 │                     │                         │
                 │                     │ uses                    │
                 │                     ▼                         │
                 │                   javac                       │
                 │                  Java 21                      │
                 │                                               │
                 └─────────────────────┬─────────────────────────┘
                                       │
                                       ▼
                                 PACKAGE APP


pom.xml DEPENDENCIES
──────────────────────────────────────────────────────────────────────────────

pom.xml
   │
   ├── Spring Boot / Spring MVC
   │   └── backend + REST application framework
   │
   ├── Spring JDBC
   │   └── Java ↔ SQL/database helper
   │
   ├── PostgreSQL JDBC Driver
   │   └── actual Java driver that speaks to PostgreSQL
   │
   ├── Flyway
   │   └── database migration system
   │
   ├── Validation
   │   └── validates Java/API inputs
   │
   ├── JUnit
   │   └── testing framework
   │
   ├── AssertJ
   │   └── readable test assertions
   │
   └── Testcontainers
       └── starts temporary real PostgreSQL for tests


MAVEN WRAPPER
──────────────────────────────────────────────────────────────────────────────

Normal Maven:

    mvn verify
       │
       └── requires Maven installed globally


Your project:

    ./mvnw verify
       │
       ▼
    Maven Wrapper
       │
       ├── mvnw
       │   └── macOS / Linux launcher
       │
       ├── mvnw.cmd
       │   └── Windows launcher
       │
       └── .mvn/wrapper/
           └── says which Maven version to use
                   │
                   ▼
              Maven 3.9.11
                   │
                   ▼
               uses JDK 21


So:

    Java 21
       ↑
       │ used by
       │
    Maven
       │
       ├── reads pom.xml
       ├── downloads Spring/Postgres/Flyway/etc.
       ├── compiles Java
       ├── runs tests
       └── packages application


                                      │
                                      ▼
================================================================================
                      1.3 SPRING BOOT APPLICATION
================================================================================


Maven expects conventional folders:

dvp-settlement-engine/
│
└── src/
    │
    ├── main/
    │   │
    │   ├── java/
    │   │   └── production Java source code
    │   │
    │   └── resources/
    │       └── configuration + SQL migrations
    │
    └── test/
        └── java/
            └── automated Java tests


JAVA PACKAGE
──────────────────────────────────────────────────────────────────────────────

Your Java namespace:

    com.jasonwidjaja.dvp

maps to:

    src/main/java/
        com/
          jasonwidjaja/
            dvp/


Why?

    jasonwidjaja.com
           │
           │ reverse domain convention
           ▼
    com.jasonwidjaja
           │
           └── dvp
               └── this specific application


APPLICATION ENTRY POINT
──────────────────────────────────────────────────────────────────────────────

src/main/java/com/jasonwidjaja/dvp/
└── DvpApplication.java
        │
        │ contains
        ▼
      main()
        │
        ▼
@SpringBootApplication
        │
        │ starts
        ▼
    SPRING BOOT
        │
        ├── creates application context
        ├── discovers components
        ├── configures dependencies
        ├── starts web server
        └── later configures database access


At this point:

    Java 21                  ✓
    Maven                    ✓
    pom.xml                  ✓
    Spring dependencies      ✓
    source structure         ✓
    DvpApplication.java      ✓
    Spring Boot starts       ✓

But:

    database schema          ✗
    accounts                 ✗
    trades                   ✗


                                      │
                                      ▼
================================================================================
                 1.4 POSTGRESQL + APPLICATION CONFIGURATION
================================================================================


                              SPRING BOOT APP
                                    │
                                    │ reads
                                    ▼
                         ┌─────────────────────┐
                         │ application.yml     │
                         │                     │
                         │ Spring config file  │
                         └──────────┬──────────┘
                                    │
                                    │ references
                                    ▼
                         ENVIRONMENT VARIABLES
                                    │
                  ┌─────────────────┼─────────────────┐
                  │                 │                 │
                  ▼                 ▼                 ▼
             DB URL/HOST         DB USER          DB PASSWORD
                                                       │
                                                       │
                                     actual secrets stay outside Git
                                                       │
                                                       ▼
                                                    .env
                                              NOT COMMITTED


.env.example
    │
    └── contains variable NAMES / placeholders
        but NO real password


SPRING DATABASE STACK
──────────────────────────────────────────────────────────────────────────────

                       YOUR JAVA CODE
                             │
                             ▼
                       Spring JDBC
                             │
                             ▼
                 NamedParameterJdbcTemplate
                             │
                             ▼
                         DataSource
                             │
                     connection pool
                             │
                             ▼
                  PostgreSQL JDBC Driver
                             │
                             │ network/database protocol
                             ▼
                       POSTGRESQL
                             │
                             ▼
                       stored data


WHAT EACH LAYER DOES:

Your Java code
    │
    └── "give me account 123"

Spring JDBC
    │
    └── helps execute explicit SQL safely

NamedParameterJdbcTemplate
    │
    └── supports parameters such as:
        WHERE id = :accountId

DataSource
    │
    └── supplies database connections

PostgreSQL JDBC Driver
    │
    └── lets Java actually communicate with PostgreSQL

PostgreSQL
    │
    └── stores durable financial state


LOCAL POSTGRESQL
──────────────────────────────────────────────────────────────────────────────

Could run as:

    Docker
      │
      ▼
    compose.yaml
      │
      ▼
    PostgreSQL container

Only PostgreSQL needs Docker at this stage.

Spring Boot still runs directly on your Mac:

    Mac
    │
    ├── Java/Spring application
    │
    └── Docker
        └── PostgreSQL


                                      │
                                      ▼
================================================================================
                       1.5 FLYWAY + DATABASE SCHEMA
================================================================================


At 1.4 PostgreSQL exists...

but it is basically an empty database.

                    PostgreSQL
                         │
                         ▼
                    empty structure


We now need to describe:

    WHICH TABLES EXIST?
    WHICH COLUMNS?
    WHICH RELATIONSHIPS?
    WHICH CONSTRAINTS?


Enter:

                         FLYWAY
                            │
                            │ reads
                            ▼
        src/main/resources/db/migration/
                            │
                            └── V1__participants_assets_accounts.sql
                                      │
                                      ▼
                                 PostgreSQL
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
        participant                asset                  account
            table                   table                   table


DATABASE MODEL
──────────────────────────────────────────────────────────────────────────────

                    PARTICIPANT
                    ┌──────────┐
                    │ Alice    │
                    │ Bob      │
                    └────┬─────┘
                         │
                         │ owns
                         ▼
                    ┌──────────┐
                    │ ACCOUNT  │
                    └────┬─────┘
                         │
                         │ holds
                         ▼
                       ASSET
                    ┌──────────┐
                    │ AUD      │
                    │ EQ1      │
                    └──────────┘


Actual combinations:

Alice ──────► Alice/AUD account ──────► AUD
Alice ──────► Alice/EQ1 account ──────► EQ1

Bob   ──────► Bob/AUD account   ──────► AUD
Bob   ──────► Bob/EQ1 account   ──────► EQ1


ACCOUNT
──────────────────────────────────────────────────────────────────────────────

Account stores:

    participant
        +
    asset
        +
    opening_balance
        +
    current_balance


Example:

    Alice / AUD
        │
        ├── opening balance = 100000
        │                     means AUD 1,000
        │
        └── current balance = 100000


Why both?

    opening_balance
          │
          │ fixed starting state
          ▼
       HISTORY
          │
          │ + later settlement postings
          ▼
    current_balance


Eventually:

    current balance
          =
    opening balance
          +
    committed postings


DATABASE CONSTRAINTS
──────────────────────────────────────────────────────────────────────────────

PostgreSQL itself protects:

    no negative balances

    no account pointing at
    nonexistent participant

    no account pointing at
    nonexistent asset

    only one:
    participant + asset account pair

    unique asset code


So correctness is NOT only:

    Java says "don't do this"

It is also:

    PostgreSQL physically rejects invalid state


                                      │
                                      ▼
================================================================================
                         1.6 DETERMINISTIC SEED DATA
================================================================================


FLYWAY created:

    structure

SEED creates:

    example data


                SCHEMA                        SEED
                  │                            │
                  ▼                            ▼
             participant                    Alice
             asset                          Bob
             account                        AUD
                                            EQ1
                                            accounts


scripts/seed-demo.sql
        │
        ▼
    PostgreSQL
        │
        ├── Alice
        ├── Bob
        ├── AUD
        ├── EQ1
        │
        ├── Alice/AUD  = 100000
        ├── Alice/EQ1  = 0
        ├── Bob/AUD    = 0
        └── Bob/EQ1    = 10


DEMO STARTING STATE
──────────────────────────────────────────────────────────────────────────────

             ALICE                         BOB
          ┌─────────┐                  ┌─────────┐
          │ AUD     │                  │ AUD     │
          │ 1000    │                  │ 0       │
          └─────────┘                  └─────────┘

          ┌─────────┐                  ┌─────────┐
          │ EQ1     │                  │ EQ1     │
          │ 0       │                  │ 10      │
          └─────────┘                  └─────────┘


This prepares the later transaction:

          Alice buys 10 EQ1 from Bob
                    for AUD 500


Seed must be SAFE TO REPEAT:

first run
    │
    ▼
creates demo state

second run
    │
    ▼
DOES NOT duplicate anything

after settlement someday
    │
    ▼
DOES NOT reset current balances


                                      │
                                      ▼
================================================================================
                        1.7 SPRING JDBC ACCOUNT READ
================================================================================


Now we connect:

      JAVA DOMAIN MODEL
             │
             ▼
        DATABASE DATA


JAVA DOMAIN OBJECTS
──────────────────────────────────────────────────────────────────────────────

Participant.java
    │
    └── represents Alice/Bob

Asset.java
    │
    └── represents AUD/EQ1

AssetType.java
    │
    └── CASH / SECURITY

Account.java
    │
    └── represents participant holding an asset


DATABASE ACCESS
──────────────────────────────────────────────────────────────────────────────

              Java application
                    │
                    │ asks
                    ▼
             AccountRepository
                    │
                    │ uses
                    ▼
        NamedParameterJdbcTemplate
                    │
                    │ executes explicit SQL
                    ▼
              PostgreSQL JDBC
                    │
                    ▼
               PostgreSQL
                    │
                    ▼
              account table


Example:

Java:

    accountRepository.findById(accountId)

             │
             ▼

Repository SQL:

    SELECT ...
    FROM account
    WHERE id = :accountId

             │
             ▼

PostgreSQL result:

    Alice
    AUD
    opening = 100000
    current = 100000

             │
             ▼

Java Account object


IMPORTANT:

Spring JDBC
    ≠ database

Spring JDBC
    ≠ ORM

It is simply the Java-side tool helping us talk to PostgreSQL.

There is deliberately:

    NO JPA
    NO Hibernate ORM


                                      │
                                      ▼
================================================================================
                     1.8 TESTCONTAINERS INTEGRATION TESTS
================================================================================


Until now you can run a local PostgreSQL database.

But automated tests should NOT depend on:

    "Jason happens to have PostgreSQL configured correctly"


Instead:

                       ./mvnw verify
                              │
                              ▼
                           Maven
                              │
                              ▼
                            JUnit
                              │
                              ▼
                       Testcontainers
                              │
                              │ talks to Docker
                              ▼
                           Docker
                              │
                              ▼
                temporary PostgreSQL container
                              │
                              ▼
                            Flyway
                              │
                              ▼
                         V1 migration
                              │
                              ▼
                           seed data
                              │
                              ▼
                       Spring JDBC tests
                              │
                              ▼
                     database assertions
                              │
                              ▼
                         TEST PASSES
                              │
                              ▼
                 temporary database destroyed


THIS TESTS THE REAL STACK:

    Java 21
       │
       ▼
    Spring Boot
       │
       ▼
    Spring JDBC
       │
       ▼
    PostgreSQL Driver
       │
       ▼
    REAL PostgreSQL
       │
       ▼
    Flyway schema
       │
       ▼
    Seed data
       │
       ▼
    Repository reads


DATABASE CONSTRAINT TESTS
──────────────────────────────────────────────────────────────────────────────

Tests deliberately try:

    negative balance
          │
          ▼
    PostgreSQL rejects ✓


    duplicate Alice/AUD account
          │
          ▼
    PostgreSQL rejects ✓


    account referencing unknown participant
          │
          ▼
    PostgreSQL rejects ✓


This proves the database itself protects important invariants.


                                      │
                                      ▼
================================================================================
                        1.9 FINAL PHASE VERIFICATION
================================================================================


                    ./mvnw clean verify
                             │
                             ▼
                          Maven
                             │
          ┌──────────────────┼─────────────────────┐
          │                  │                     │
          ▼                  ▼                     ▼
       compile              test              integration test
          │                  │                     │
          ▼                  ▼                     ▼
      Java 21              JUnit              Testcontainers
                                                   │
                                                   ▼
                                              PostgreSQL
                                                   │
                                                   ▼
                                                Flyway
                                                   │
                                                   ▼
                                             Repository tests
                                                   │
                                                   ▼
                                             constraints tests


FINAL CHECK:

Java 21                         ✓
Temurin JDK                     ✓
JAVA_HOME selects Java 21      ✓

Maven Wrapper                   ✓
pom.xml                         ✓
dependencies                    ✓

Spring Boot                     ✓
DvpApplication.java             ✓
application starts              ✓

PostgreSQL                      ✓
JDBC Driver                     ✓
Spring JDBC                     ✓
DataSource                      ✓

Flyway                          ✓
V1 schema                       ✓

participant table               ✓
asset table                     ✓
account table                   ✓

Alice/Bob seed                  ✓
AUD/EQ1 seed                    ✓

AccountRepository               ✓
Java ↔ database mapping         ✓

Testcontainers                  ✓
real PostgreSQL tests           ✓
database constraint tests       ✓

./mvnw clean verify             ✓


================================================================================
                   COMPLETE PHASE 1 MENTAL MODEL
================================================================================


                        YOUR SOURCE CODE
                              │
                              │ written in
                              ▼
                           JAVA 21
                              │
                              │ compiled/run by
                              ▼
                     TEMURIN OPENJDK JDK
                              ▲
                              │
                      selected through
                         JAVA_HOME
                              ▲
                              │
                              │ used by
                              │
                            MAVEN
                              │
                              │ configured by
                              ▼
                           pom.xml
                              │
                 ┌────────────┼────────────┐
                 │            │            │
                 ▼            ▼            ▼
            Spring Boot   PostgreSQL     Test tools
                 │          Driver       JUnit
                 │            │          AssertJ
                 │            │          Testcontainers
                 │            │
                 ▼            ▼
              Spring JDBC
                 │
                 ▼
     NamedParameterJdbcTemplate
                 │
                 ▼
              DataSource
                 │
                 ▼
             PostgreSQL
                 │
        ┌────────┼─────────┐
        │        │         │
        ▼        ▼         ▼
 participant   asset     account
        ▲        ▲         ▲
        │        │         │
        └────────┼─────────┘
                 │
                 │ created by
                 ▼
               Flyway
                 │
                 ▼
       V1__participants_assets_accounts.sql

                 +
                 │
                 ▼
             seed-demo.sql
                 │
                 ▼
      Alice / Bob / AUD / EQ1


TEST PATH:

./mvnw verify
      │
      ▼
    Maven
      │
      ▼
    JUnit
      │
      ▼
 Testcontainers
      │
      ▼
    Docker
      │
      ▼
temporary PostgreSQL
      │
      ▼
    Flyway
      │
      ▼
  seed data
      │
      ▼
AccountRepository
      │
      ▼
 assertions
      │
      ▼
    PASS


RUNTIME PATH:

User / future API request
      │
      ▼
Spring MVC
      │
      ▼
future service
      │
      ▼
AccountRepository
      │
      ▼
Spring JDBC
      │
      ▼
PostgreSQL Driver
      │
      ▼
PostgreSQL


PHASE 1 STOPS HERE.

There is still NO:

    Trade
    Settlement
    Journal
    Idempotency
    Concurrency handling
    Reconciliation

Those come in later phases.
```