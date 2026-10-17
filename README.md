# 🩸 BloodBankManagementSystem

### Java and MySQL Based Blood Bank Management System

BloodBankManagementSystem is a web-based application designed to manage blood donors, blood stock, donations, and hospital blood requests in one place. It demonstrates how Java, MySQL, and web technologies can work together to simplify blood bank operations.

## ✨ Features

- **Dashboard:** View donor statistics, blood stock, and request status.
- **Donor Management:** Register and view blood donor details.
- **Blood Stock Management:** Track available units by blood group.
- **Donation Management:** Record donations and update blood stock.
- **Blood Requests:** Register hospital blood requests and track their status.
- **Request Fulfilment:** Manage requests and deduct available stock when fulfilled.

## 🛠️ Technologies Used

- **Backend:** Java 21
- **Frontend:** HTML, CSS, JavaScript
- **Database:** MySQL
- **Database Connectivity:** JDBC, MySQL Connector/J
- **Server:** Java HTTP Server

## 📁 Project Structure

```text
BloodBankManagementSystem/
├── src/
│   └── BloodBankServer.java
├── public/
│   ├── index.html
│   ├── style.css
│   └── app.js
├── lib/
│   └── mysql-connector-j-26.7.0.jar
├── sample_database.sql
├── SETUP_FOR_RECRUITERS.md
└── .gitignore
```

## ⚙️ Prerequisites

Install the following before running the application:

- Java JDK 21
- MySQL Server
- MySQL Workbench
- MySQL Connector/J JAR included in the `lib` folder

Verify Java installation:

```bash
java -version
javac -version
```

## 🚀 Setup and Installation

### 1. Clone the Repository

```bash
git clone https://github.com/Christy-29/BloodBankManagementSystem.git
cd BloodBankManagementSystem
```

Alternatively, download the repository as a ZIP file and extract it.

### 2. Set Up the Database

1. Open MySQL Workbench and connect to your local MySQL server.
2. Select **Server → Data Import**.
3. Choose **Import from Self-Contained File**.
4. Select `sample_database.sql`.
5. Import the database and verify that the `blood_bank_db` schema and its tables exist.

The sample SQL file uses fictional demo records for testing.

### 3. Configure the Database Password

The application reads the MySQL password from an environment variable.

In Windows Command Prompt, run:

```bat
set "BLOODBANK_DB_PASSWORD=YOUR_MYSQL_PASSWORD"
```

Replace the placeholder with your own local MySQL password. Do not commit passwords to the repository.

Ensure the database username and connection URL in `BloodBankServer.java` match your local MySQL configuration.

### 4. Compile the Java Application

Run this command from the project root:

```bat
javac -cp "lib\mysql-connector-j-26.7.0.jar" -d out src\BloodBankServer.java
```

### 5. Start the Server

```bat
java -cp "out;lib\mysql-connector-j-26.7.0.jar" BloodBankServer
```

Keep the terminal open while using the application.

### 6. Open the Website

Visit:

**http://localhost:8080**

The application will run locally on your computer.

For additional setup instructions and troubleshooting, see [`SETUP_FOR_RECRUITERS.md`](SETUP_FOR_RECRUITERS.md).

## 🧪 Testing

You can test the application by:

- Registering a fictional blood donor.
- Viewing blood stock by blood group.
- Recording a donation and checking the stock update.
- Creating a hospital blood request.
- Testing request fulfilment and stock deduction.

## 🔐 Security and Privacy

- The sample database is intended for demonstration and testing.
- Do not use real donor or patient information in a public demo.
- Keep database passwords and other secrets out of source code and GitHub.
- Use fictional records when sharing screenshots or demonstrating the application.

## 🎯 Project Objective

The objective of this project is to demonstrate database-driven web application development using Java, JDBC, MySQL, HTML, CSS, and JavaScript.

## 👩‍💻 Author

**Christy Grace V**

GitHub: [Christy-29](https://github.com/Christy-29)

## 📄 License

No license has been specified for this repository. All rights are reserved by default unless a license is added. Contact the author for permission to reuse or redistribute the source code.
