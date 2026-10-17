# BloodBankManagementSystem — Setup for Recruiters

This project uses Java 21, MySQL, and an HTML/CSS/JavaScript frontend served by Java.

## Requirements
- Java JDK 21 (`java` and `javac` available in Command Prompt)
- MySQL Server and MySQL Workbench
- MySQL Connector/J JAR at `lib\mysql-connector-j-26.7.0.jar`

## 1. Import the demo database
Use a fresh local demo database/server. `sample_database.sql` creates/uses `blood_bank_db`, matching the current Java JDBC URL.

In MySQL Workbench:
1. Connect to your local MySQL server.
2. Select **Server → Data Import**.
3. Choose **Import from Self-Contained File**, then select `sample_database.sql`.
4. Choose `blood_bank_db` as the target schema if asked.
5. Click **Start Import**.
6. Refresh Schemas and confirm these tables exist: `donors`, `blood_stock`, `donations`, `blood_requests`.

The data is fictional. This script does not drop tables, but it adds sample rows/stock. Do not run it against a database containing real data if you want to keep that database unchanged.

## 2. Configure the password safely
The Java source should read its password from an environment variable, not contain a hard-coded password. The connection settings should be equivalent to:

```java
private static final String DB_URL =
    "jdbc:mysql://localhost:3306/blood_bank_db";
private static final String DB_USER = "root";
private static final String DB_PASSWORD =
    System.getenv("BLOODBANK_DB_PASSWORD");
```

If `BloodBankServer.java` currently contains a literal password, replace it before pushing to GitHub. If that password has already been pushed or shared, change it in MySQL.

In Command Prompt, set your own password for this terminal session (replace the placeholder; never commit the real password):

```bat
set BLOODBANK_DB_PASSWORD=YOUR_LOCAL_MYSQL_PASSWORD
```

If your MySQL username is not `root`, update `DB_USER` accordingly.

## 3. Compile and run
Open Command Prompt in the project root (the folder containing `src`, `public`, and `lib`) and run:

```bat
javac -cp "lib\mysql-connector-j-26.7.0.jar" -d out src\BloodBankServer.java
java -cp "out;lib\mysql-connector-j-26.7.0.jar" BloodBankServer
```

Keep the Command Prompt window open. Then visit:

http://localhost:8080

## 4. Test
- View dashboard and stock.
- Register a fictional donor.
- Record a donation using an existing donor ID.
- Create a blood request and test fulfilment if that action is available in your current version.

## Troubleshooting
- **Access denied:** Check the environment variable and MySQL username.
- **Unknown database:** Import the SQL and confirm `blood_bank_db` exists.
- **Communications link failure:** Make sure MySQL Server is running.
- **Connector class not found:** Confirm the Connector/J JAR is in `lib`.
- **Port 8080 in use:** Stop the other process or change the Java server port and use the matching URL.

## GitHub safety checklist
- Never commit `.env`, passwords, database dumps containing real data, or real donor/patient contact information.
- Keep `out/` and `*.class` out of Git.
- Use only fictional records in a public demo.
