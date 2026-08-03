package com.smartpos;

public class Product {
    private int id;
    private String name;
    private double price;
    private int stock;
    private int reorderLevel;

    public Product(int id, String name, double price, int stock, int reorderLevel) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.reorderLevel = reorderLevel;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public int getReorderLevel() { return reorderLevel; }
    public boolean isLowStock() { return stock <= reorderLevel; }

    @Override
    public String toString() {
        return name + " - $" + String.format("%.2f", price) + " (Stock: " + stock + ")";
    }
}