CREATE DATABASE IF NOT EXISTS smartpos;
USE smartpos;

-- 1. Users table (RBAC)
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CASHIER'
);

-- 2. Products table
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DOUBLE NOT NULL,
    stock INT NOT NULL DEFAULT 0
);

-- 3. Sales table (with M-Pesa tracking)
CREATE TABLE IF NOT EXISTS sales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    total_amount DOUBLE NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    cashier_name VARCHAR(50) DEFAULT 'Guest',
    mpesa_code VARCHAR(20) DEFAULT NULL,
    sale_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed default accounts & sample inventory
INSERT INTO users (username, password, role) VALUES 
('admin', 'admin123', 'ADMIN'),
('cashier', 'cashier123', 'CASHIER')
ON DUPLICATE KEY UPDATE username=username;

INSERT INTO products (name, price, stock) VALUES 
('Bread 800g', 110.00, 45),
('Milk 500ml', 65.00, 80),
('Sugar 1kg', 185.00, 30),
('Cooking Oil 1L', 320.00, 15)
ON DUPLICATE KEY UPDATE name=name;