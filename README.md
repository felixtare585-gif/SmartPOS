# SmartPOS — Desktop Point of Sale System

A desktop-based Point of Sale (POS) system built using Java, Apache Maven, and MySQL.  
This application provides retail management capabilities including transaction processing, inventory management, and database record keeping through a clean graphical user interface.

---

## ?? Features

* **User Authentication & Role Permissions:** Secure login for system access.
* **Point of Sale Interface:** Real-time item lookup, cart management, and receipt calculation.
* **Product & Inventory Management:** Add, update, view, and organize products.
* **Transaction Recording:** Save sales history and transaction records directly to MySQL.
* **MySQL Database Connectivity:** Robust relational data storage using JDBC.
* **Custom Styling:** Clean desktop interface styled with CSS (\style.css\).

---

## ?? Screenshots

*(Add application screenshots in a \screenshots/\ directory to display them here)*

| Login Screen | POS Dashboard |
| :---: | :---: |
| ![Login](screenshots/01login.png) | ![Dashboard](screenshots/02dashboard.png) |

---

## ??? Technologies Used

* **Java (JDK 17+)**
* **Apache Maven**
* **MySQL Database**
* **JDBC (Java Database Connectivity)**
* **Git & GitHub**

---

## ?? Project Structure

\\\
SmartPOS
¦
+-- .github
+-- pom.xml
+-- smartpos.bat
+-- .gitignore
+-- README.md
¦
+-- src
    +-- main
        +-- java
        ¦   +-- com.smartpos
        ¦       +-- App.java
        ¦       +-- CartItem.java
        ¦       +-- Database.java
        ¦       +-- Main.java
        ¦       +-- Product.java
        ¦       +-- smartpos.sql
        ¦
        +-- resources
            +-- style.css
\\\

---

## ??? Database Setup

1. Start your MySQL Server (via **XAMPP Control Panel** or standalone MySQL Server).
2. Open **phpMyAdmin** (\http://localhost/phpmyadmin\) or your MySQL client.
3. Create a database named \smartpos\:
   \\\sql
   CREATE DATABASE smartpos;
   \\\
4. Import the included database script:
   * Execute the queries inside \src/main/java/com/smartpos/smartpos.sql\ to generate all tables and initial schema.

---

## ?? Configuration

If your local MySQL credentials differ from defaults, update connection parameters in \src/main/java/com/smartpos/Database.java\:

\\\java
String url = "jdbc:mysql://localhost:3306/smartpos";
String user = "root";      // Replace with your MySQL username
String password = "";      // Replace with your MySQL password
\\\

---

## ?? How To Run

### Option 1: Quick Launch (Windows Batch File)
Double-click \smartpos.bat\ or execute in PowerShell:
\\\powershell
.\smartpos.bat
\\\

### Option 2: Using Maven
Clone the repository and run via Maven:
\\\ash
git clone https://github.com/felixtare585-gif/SmartPOS.git
cd SmartPOS
mvn clean compile exec:java -Dexec.mainClass="com.smartpos.Main"
\\\

---

## ????? Author

**Felix Tare**
* GitHub: [@felixtare585-gif](https://github.com/felixtare585-gif)
