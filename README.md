# dvp-settlement-engine
Java/Spring Boot backend that simulates Delivery-versus-Payment trade settlement, safe retries, concurrency control, and reconciliation against external records.

## Local development

### Prerequisites

- Java 21
- Docker Desktop

### Setup

1. Clone the repository.

2. Create your local environment file:

```bash
cp .env.example .env
```

3. Update `.env` with your local database credentials if needed.

4. Start PostgreSQL:

```bash
docker compose --env-file .env up -d
```

5. Export the environment variables:

```bash
set -a
source .env
set +a
```

### Run the application

Start the Spring Boot application:

```bash
./mvnw spring-boot:run
```

Stop it with:

```text
Ctrl+C
```

### Run the tests

Run the full test suite:

```bash
./mvnw clean verify
```

### Environment files

- `.env.example`
  - committed to Git
  - documents the required environment variables
  - contains placeholder values only

- `.env`
  - local to each developer
  - contains actual local credentials
  - must not be committed

## Architecture
```txt
DVP SETTLEMENT ENGINE
================================================================================

Developer machine
│
├── Java 21 / Temurin
│   └── Java language + JDK used to compile and run the application
│
├── Maven Wrapper
│   ├── mvnw / mvnw.cmd
│   ├── .mvn/wrapper/
│   └── pom.xml
│       └── builds the project and manages dependencies
│
├── Spring Boot application
│   │
│   ├── DvpApplication.java
│   │   └── main entry point that starts the application
│   │
│   ├── application.yml
│   │   └── application configuration
│   │
│   ├── Spring MVC
│   │   └── receives HTTP requests and provides the REST API
│   │
│   ├── Spring JDBC
│   │   └── application queries and updates database data using explicit SQL
│   │
│   ├── NamedParameterJdbcTemplate
│   │   └── Spring JDBC helper for executing parameterized SQL
│   │
│   ├── DataSource
│   │   └── Java abstraction for obtaining database connections
│   │
│   ├── HikariCP
│   │   └── connection pool that manages reusable PostgreSQL connections
│   │
│   └── PostgreSQL JDBC Driver
│       └── actual Java driver that communicates with PostgreSQL
│
├── Flyway
│   └── creates and changes the database structure using versioned SQL migrations
│
├── Environment configuration
│   │
│   ├── .env
│   │   └── local database credentials/config; never committed
│   │
│   └── .env.example
│       └── template showing which environment variables are required
│
└── Docker Desktop
    └── runs containers on your Mac
        │
        └── PostgreSQL container
            └── PostgreSQL 18.x
                └── durable relational database that stores project data


HOW THE MAIN PIECES CONNECT
================================================================================

Java 21 / Temurin
    │
    └── compiles + runs Java
             │
             ▼
         Maven
             │
             ├── reads pom.xml
             │
             ├── downloads dependencies
             │
             ├── compiles code
             │
             ├── runs tests
             │
             └── packages application
             │
             ▼
       Spring Boot application
             │
             ├── Spring MVC
             │   └── HTTP / REST API
             │
             ├── Spring JDBC
             │   └── queries + updates data
             │
             └── Flyway
                 └── creates + changes database structure
             │
             ▼
          DataSource
             │
             ▼
          HikariCP
             │
             ▼
     PostgreSQL JDBC Driver
             │
             ▼
       PostgreSQL 18
             │
             ├── tables
             ├── rows
             ├── constraints
             ├── transactions
             └── later: row locks


SPRING JDBC VS FLYWAY
================================================================================

Spring JDBC
    │
    └── works with DATA inside existing tables

Examples:

    SELECT ...
    INSERT ...
    UPDATE ...

Example:

    SELECT *
    FROM account;


Flyway
    │
    └── works with DATABASE STRUCTURE

Examples:

    CREATE TABLE ...
    ALTER TABLE ...
    CREATE INDEX ...

Example:

    CREATE TABLE account (...);


Simple distinction:
- Flyway → creates the shelves
- Spring JDBC → reads and changes the things stored on the shelves


DATABASE EVOLUTION
================================================================================

1.4 PostgreSQL connection
    │
    └── PostgreSQL exists but database is empty
              │
              ▼

1.5 Flyway
    │
    └── creates schema
        ├── participant
        ├── asset
        └── account
              │
              ▼

1.6 Seed data
    │
    └── inserts known demo data
        ├── Alice
        ├── Bob
        ├── AUD
        ├── EQ1
        └── starting balances
              │
              ▼

1.7 Spring JDBC
    │
    └── Java reads those records
              │
              ▼

1.8 Testcontainers
    │
    └── automatically creates temporary PostgreSQL for tests


CONFIGURATION FLOW
================================================================================

.env
│
│ contains actual local values
│
▼
environment variables
│
▼
application.yml
│
│ tells Spring where PostgreSQL is
│
▼
Spring Boot
│
▼
DataSource / HikariCP
│
▼
PostgreSQL JDBC Driver
│
▼
PostgreSQL


TEST FLOW
================================================================================

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
    └── creates database schema
    │
    ▼
seed data
    │
    ▼
Spring JDBC
    │
    └── reads/queries database
    │
    ▼
assertions
    │
    ▼
PASS / FAIL
```

Terminology

- Java 21 / Temurin → language + JDK used to compile and run Java
- Maven → builds the project and manages dependencies
- pom.xml → Maven configuration for this project
- Spring Boot → framework that runs the backend application
- DvpApplication.java → main starting point of the backend
-  Spring MVC → receives HTTP requests and provides REST endpoints
- Spring JDBC → application queries and updates database data
- NamedParameterJdbcTemplate → helper for executing SQL safely with named parameters
- DataSource → gives Java database connections
- HikariCP → manages a pool of reusable database connections
- PostgreSQL JDBC Driver → lets Java communicate with PostgreSQL
- PostgreSQL → durable database that stores financial state
- Flyway → creates/changes database structure
- Migration → one versioned database-structure change
- Seed data → inserts known example records
- Docker Desktop → runs PostgreSQL locally inside a container
- .env → private local environment values
- application.yml → Spring application configuration
- JUnit → Java testing framework
- AssertJ → readable test assertions
- Testcontainers → starts temporary real PostgreSQL databases for automated tests
- ./mvnw verify → builds + tests + verifies the whole project


## Schema

```txt
participant
├── id
└── name

asset
├── id
├── code
└── type

account
├── id
├── participant_id  → participant.id
├── asset_id        → asset.id
├── opening_balance
└── current_balance
```

- participant + asset → only one account
- opening balance → cannot be negative
- current balance → cannot be negative
- participant_id → must point to a real participant
- asset_id → must point to a real asset