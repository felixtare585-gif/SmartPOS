package com.smartpos;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final String URL = "jdbc:mysql://localhost:3306/smartpos_db"; // Adjust database name
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * Prevents duplicate products by using GROUP BY and COALESCE
     */
    public static List<Product> getProducts() {
        List<Product> products = new ArrayList<>();
        String sql = """
            SELECT p.id, p.name, p.price, p.reorder_level,
                   COALESCE(SUM(i.quantity), 0) AS total_stock
            FROM products p
            LEFT JOIN inventory i ON p.id = i.product_id
            WHERE p.is_active = 1
            GROUP BY p.id, p.name, p.price, p.reorder_level
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                products.add(new Product(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getDouble("price"),
                    rs.getInt("total_stock"),
                    rs.getInt("reorder_level")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    /**
     * Fetch products that need restocking
     */
    public static List<Product> getLowStockProducts() {
        List<Product> lowStock = new ArrayList<>();
        for (Product p : getProducts()) {
            if (p.isLowStock()) {
                lowStock.add(p);
            }
        }
        return lowStock;
    }

    /**
     * Deducts stock when a checkout transaction occurs
     */
    public static boolean updateStock(int productId, int quantitySold) {
        String sql = "UPDATE inventory SET quantity = quantity - ? WHERE product_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, quantitySold);
            pstmt.setInt(2, productId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}