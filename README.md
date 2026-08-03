# SmartPOS — Desktop Point of Sale System

A desktop-based Point of Sale (POS) system built using Java, Apache Maven, and MySQL.  
This application allows users to manage retail transactions, product inventory, and sales analytics through a graphical user interface with database storage using JDBC.

---

## Features

* User Authentication & Login Screen
* POS Terminal with real-time shopping cart and receipt calculations
* Inventory Management Panel for product administration
* Real-time Sales Analytics & Revenue Reports
* Export sales history directly to CSV reports
* MySQL database integration via JDBC
* Custom UI styling (style.css)

---

## Screenshots

### Login Screen
![Login Screen](screenshots/01login.png)

### POS Terminal Dashboard
![POS Dashboard](screenshots/02Dashboard.png)

### Inventory Management Panel
![Inventory Panel](screenshots/03Inventory.png)

### Sales & Revenue Analytics
![Sales Analytics](screenshots/04Analytics.png)

---

## Technologies Used

* Java (JDK 17+)
* Apache Maven
* MySQL Database
* JDBC (Java Database Connectivity)
* Git & GitHub

---

## Project Structure

\\\
SmartPOS
├── .github
├── screenshots
│   ├── 01login.png
│   ├── 02Dashboard.png
│   ├── 03Inventory.png
│   └── 04Analytics.png
├── pom.xml
├── smartpos.bat
├── .gitignore
└── README.md
│
└── src
    └── main
        ├── java
        │   └── com.smartpos
        │       ├── App.java
        │       ├── CartItem.java
        │       ├── Database.java
        │       ├── Main.java
        │       ├── Product.java
        │       └── smartpos.sql
        │
        └── resources
            └── style.css
\\\

---

## Database Setup

1. Start your MySQL Server (via **XAMPP Control Panel** or standalone MySQL Server).
2. Open **phpMyAdmin** (http://localhost/phpmyadmin) or your MySQL client.
3. Create a database named smartpos:
   \\\sql
   CREATE DATABASE smartpos;
   \\\
4. Import the included database script:
   * Execute the queries inside src/main/java/com/smartpos/smartpos.sql to generate all tables and initial schema.

---

## Configuration

If your local MySQL credentials differ from defaults, update connection parameters in src/main/java/com/smartpos/Database.java:

\\\java
String url = "jdbc:mysql://localhost:3306/smartpos";
String user = "root";      // Replace with your MySQL username
String password = "";      // Replace with your MySQL password
\\\

---

## How To Run

### Clone the repository:
\\\ash
git clone https://github.com/felixtare585-gif/SmartPOS.git
cd SmartPOS
\\\

### Run the application:
Double-click smartpos.bat or execute via Maven in terminal:
\\\powershell
mvn clean compile exec:java -Dexec.mainClass="com.smartpos.Main"
\\\

---

## Author

**Felix Tare**  
GitHub: [@felixtare585-gif](https://github.com/felixtare585-gif)
