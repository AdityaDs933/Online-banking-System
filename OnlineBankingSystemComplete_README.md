# OnlineBankingSystemComplete

A simple demo **Java** desktop application that simulates an online banking system. The single-file demo implements in-memory and JDBC-backed DAOs, a Swing UI (admin & customer panels), basic account types (savings/checking), transactions, and an example service layer that supports deposits, withdrawals and atomic transfers.

---

## Features

- Swing-based GUI with **Admin** and **Customer** panels
- User, Account and Transaction domain models
- Two persistence modes:
  - **JDBC (H2 in-memory)** when the H2 driver is available on the classpath
  - **In-memory fallback** if H2 driver is not found (no external dependency required)
- DAO interfaces + implementations (`Jdbc*` and `InMemory*`)
- `BankService` implements business logic with:
  - deposit, withdraw (synchronized per-account)
  - atomic transfer using a single JDBC connection with commit/rollback
- Demo data seeding and example users: `admin@bank/admin`, `alice@bank/alice`, `bob@bank/bob`

---

## Requirements

- Java 8+ (JDK)
- Optional: H2 JDBC driver JAR to enable JDBC-mode persistence. If you don't provide H2, the app will run in memory only.
  - Download H2 from https://www.h2database.com and add the JAR to your classpath when running.

---

## Files

This project is distributed as a single file in the example you provided:

- `OnlineBankingSystemComplete.java` — contains the entire demo application (models, DAOs, service, UI).

> Tip: If you prefer a real project layout, split classes into packages and files (e.g. `model/`, `dao/`, `service/`, `ui/`).

---

## Build & Run

### 1) Compile

```bash
javac OnlineBankingSystemComplete.java
```

### 2) Run (in-memory fallback only)

```bash
# runs without H2; only in-memory DAOs will be used
java OnlineBankingSystemComplete
```

### 3) Run with H2 (JDBC mode)

Place the H2 JAR (for example `h2-2.1.214.jar` or similar) in the same folder or otherwise reference its path. Examples below assume `h2.jar` is the JAR file name.

**Windows**

```powershell
javac OnlineBankingSystemComplete.java
java -cp .;h2.jar OnlineBankingSystemComplete
```

**macOS / Linux**

```bash
javac OnlineBankingSystemComplete.java
java -cp .:h2.jar OnlineBankingSystemComplete
```

When the H2 driver is present, the app will initialize an H2 in-memory database (`jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1`) and use JDBC-based DAOs (persistent for the lifetime of the JVM).

---

## Demo credentials & quick usage

- Admin user: `admin@bank` / `admin`
- Customer users: `alice@bank` / `alice`, `bob@bank` / `bob`

UI tips:
- Admin panel: manage users and inspect transactions
- Customer panel: create accounts, deposit, withdraw, transfer, and view transactions

---

## Design notes

- **Domain models**: `User`, `Account` (abstract), `SavingsAccount`, `CheckingAccount`, `Transaction`.
- **DAO interfaces**: `UserDAO`, `AccountDAO`, `TransactionDAO` provide persistence abstractions.
- **DAO implementations**:
  - `Jdbc*` classes use the H2 `DriverManager` with simple SQL statements
  - `InMemory*` classes use `List` stores and are thread-safe via synchronization where relevant
- **Concurrency**:
  - `Account.deposit` and `Account.withdraw` are `synchronized` for thread-safety
  - `BankService` holds a `ConcurrentMap<Long,Object>` of per-account locks for safe modify operations
  - `transferAtomic` shows a pattern for a multi-row DB transaction: a single JDBC `Connection` with `setAutoCommit(false)`, `commit()` and `rollback()` on exception
- **Transactions in JDBC**:
  - The sample uses a single connection for multi-step operations (update balances + insert transactions) and commits only on success
  - Note: row-level `SELECT ... FOR UPDATE` semantics differ between databases; the demo uses an optimistic/connection-based approach appropriate for H2 demo mode

---

## Extending the demo

Ideas to improve or production-harden this demo:

- Split classes into separate files and packages
- Use a proper build system (Maven or Gradle) and declare H2 as a test/runtime dependency
- Hash passwords instead of storing plaintext
- Add input validation & better error handling in UI
- Add logging (SLF4J + Logback)
- Add unit tests for service/DAO logic
- Use connection pooling (HikariCP) for better DB performance
- Add pagination/filtering for transaction history
- Add multi-currency and BigDecimal support for money (the demo uses `long` whole-units)

---

## Known limitations

- Money is represented as `long` whole units (not `BigDecimal`). Consider migrating to `BigDecimal` with explicit currency handling for production use.
- Passwords are stored in plaintext in demo users — **do not** use this approach in real systems.
- The UI is intentionally simple (Swing). For production, separate backend and frontend cleanly.

---

