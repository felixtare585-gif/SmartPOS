package com.smartpos;

import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class App extends Application {

    private final ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private final Label totalLabel = new Label("Total: KSh 0.00");
    private final FlowPane productGrid = new FlowPane();
    private final TextField searchField = new TextField();
    
    private final List<ProductCardData> allProducts = new ArrayList<>();
    private final ObservableList<ProductCardData> inventoryList = FXCollections.observableArrayList();
    private double grandTotal = 0.0;

    // Analytics Controls
    private final Label todaySalesLabel = new Label("KSh 0.00");
    private final Label todayTxLabel = new Label("0");
    private final Label cashSalesLabel = new Label("KSh 0.00");
    private final Label mpesaSalesLabel = new Label("KSh 0.00");
    private final ObservableList<SaleRecord> salesHistory = FXCollections.observableArrayList();
    
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Stage primaryStageRef;

    // Session Info
    private String loggedInUser = "Guest";
    private String userRole = "CASHIER";

    public static class ProductCardData {
        private int id;
        private String name;
        private double price;
        private int stock;

        public ProductCardData(int id, String name, double price, int stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public int getStock() { return stock; }

        public void setName(String name) { this.name = name; }
        public void setPrice(double price) { this.price = price; }
        public void setStock(int stock) { this.stock = stock; }
    }

    private static class PaymentDetails {
        String method;
        double tendered;
        double change;

        PaymentDetails(String method, double tendered, double change) {
            this.method = method;
            this.tendered = tendered;
            this.change = change;
        }
    }

    public static class SaleRecord {
        private final int id;
        private final double totalAmount;
        private final String paymentMethod;
        private final String saleDate;

        public SaleRecord(int id, double totalAmount, String paymentMethod, String saleDate) {
            this.id = id;
            this.totalAmount = totalAmount;
            this.paymentMethod = paymentMethod;
            this.saleDate = saleDate;
        }

        public int getId() { return id; }
        public double getTotalAmount() { return totalAmount; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getSaleDate() { return saleDate; }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStageRef = primaryStage;

        if (!showLoginDialog()) {
            System.exit(0);
            return;
        }

        primaryStage.setTitle("Smart POS System - Logged in as: " + loggedInUser + " (" + userRole + ")");

        TabPane mainTabs = new TabPane();
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab posTab = new Tab("🛒 POS Terminal", createPosView());
        mainTabs.getTabs().add(posTab);

        // RBAC: Only ADMIN can access Inventory and Analytics
        if ("ADMIN".equalsIgnoreCase(userRole)) {
            Tab inventoryTab = new Tab("📦 Inventory", createInventoryView());
            Tab analyticsTab = new Tab("📊 Sales Analytics", createAnalyticsView());

            inventoryTab.setOnSelectionChanged(e -> {
                if (inventoryTab.isSelected()) loadProductsFromDatabase();
            });

            analyticsTab.setOnSelectionChanged(e -> {
                if (analyticsTab.isSelected()) loadAnalyticsData();
            });

            mainTabs.getTabs().addAll(inventoryTab, analyticsTab);
        }

        Scene scene = new Scene(mainTabs, 1100, 700);

        try {
            String css = getClass().getResource("/style.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (NullPointerException e) {
            System.out.println("Warning: style.css not found in resources folder.");
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private boolean showLoginDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Smart POS Login");
        dialog.setHeaderText("Please sign in to access the application.");

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Button loginButton = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();

            if (!authenticateUser(username, password)) {
                showErrorAlert("Authentication Error", "Invalid username or password.");
                event.consume();
            }
        });

        dialog.setResultConverter(dialogButton -> dialogButton == loginButtonType);
        Optional<Boolean> result = dialog.showAndWait();
        return result.orElse(false);
    }

    private boolean authenticateUser(String username, String password) {
        String query = "SELECT username, role FROM users WHERE username = ? AND password = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    this.loggedInUser = rs.getString("username");
                    this.userRole = rs.getString("role");
                    return true;
                }
            }
        } catch (SQLException e) {
            showErrorAlert("Database Error", "Login check failed: " + e.getMessage());
        }
        return false;
    }

    private BorderPane createPosView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        VBox topContainer = new VBox(10);
        topContainer.setPadding(new Insets(0, 0, 15, 0));

        Label title = new Label("Smart POS Dashboard");
        title.getStyleClass().add("header-title");

        searchField.setPromptText("🔍 Search product by name...");
        searchField.setPrefWidth(260);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldText, newText) -> filterProducts(newText));

        HBox searchRow = new HBox(20, title, searchField);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        topContainer.getChildren().add(searchRow);
        root.setTop(topContainer);

        productGrid.setHgap(15);
        productGrid.setVgap(15);
        productGrid.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(productGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        root.setCenter(scrollPane);

        loadProductsFromDatabase();

        VBox cartSection = new VBox(12);
        cartSection.setPadding(new Insets(0, 0, 0, 15));
        cartSection.setPrefWidth(380);

        Label cartTitle = new Label("Current Order");
        cartTitle.getStyleClass().add("section-title");

        TableView<CartItem> cartTable = new TableView<>();
        cartTable.setItems(cartItems);

        TableColumn<CartItem, String> nameCol = new TableColumn<>("Item");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(120);

        TableColumn<CartItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setPrefWidth(45);

        TableColumn<CartItem, CartItem> actionCol = new TableColumn<>("Adjust");
        actionCol.setPrefWidth(90);
        actionCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button btnMinus = new Button("-");
            private final Button btnPlus = new Button("+");
            private final HBox container = new HBox(4, btnMinus, btnPlus);

            {
                container.setAlignment(Pos.CENTER);
                btnMinus.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-cursor: hand;");
                btnPlus.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-cursor: hand;");

                btnMinus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null) {
                        if (item.getQuantity() > 1) {
                            double unitPrice = item.getTotalPrice() / item.getQuantity();
                            item.setQuantity(item.getQuantity() - 1);
                            item.setTotalPrice(item.getQuantity() * unitPrice);
                        } else {
                            cartItems.remove(item);
                        }
                        recalculateTotal();
                        cartTable.refresh();
                    }
                });

                btnPlus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null) {
                        ProductCardData prod = findProductByName(item.getName());
                        if (prod != null && item.getQuantity() + 1 > prod.getStock()) {
                            showErrorAlert("Stock Limit", "Cannot exceed available stock (" + prod.getStock() + ")");
                            return;
                        }
                        double unitPrice = item.getTotalPrice() / item.getQuantity();
                        item.setQuantity(item.getQuantity() + 1);
                        item.setTotalPrice(item.getQuantity() * unitPrice);
                        recalculateTotal();
                        cartTable.refresh();
                    }
                });
            }

            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });

        TableColumn<CartItem, Double> priceCol = new TableColumn<>("Price (KSh)");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        priceCol.setPrefWidth(100);

        cartTable.getColumns().addAll(nameCol, qtyCol, actionCol, priceCol);

        totalLabel.getStyleClass().add("total-label");

        Button checkoutBtn = new Button("Complete Checkout");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.getStyleClass().add("btn-success");
        checkoutBtn.setOnAction(e -> handleCheckout());

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.getStyleClass().add("btn-danger");
        clearBtn.setOnAction(e -> clearCart());

        cartSection.getChildren().addAll(cartTitle, cartTable, totalLabel, checkoutBtn, clearBtn);
        root.setRight(cartSection);

        return root;
    }

    private BorderPane createInventoryView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox topBox = new VBox(5);
        Label header = new Label("Inventory Management Panel");
        header.getStyleClass().add("header-title");
        Label subHeader = new Label("💡 Double-click Price or Stock cells to edit directly.");
        subHeader.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        topBox.getChildren().addAll(header, subHeader);
        topBox.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(topBox);

        TableView<ProductCardData> table = new TableView<>();
        table.setItems(inventoryList);
        table.setEditable(true);

        TableColumn<ProductCardData, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(60);

        TableColumn<ProductCardData, String> nameCol = new TableColumn<>("Product Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);

        TableColumn<ProductCardData, Double> priceCol = new TableColumn<>("Price (KSh)");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        priceCol.setOnEditCommit(event -> {
            ProductCardData product = event.getRowValue();
            double newPrice = event.getNewValue();
            product.setPrice(newPrice);
            updateProductField(product.getId(), "price", newPrice);
        });
        priceCol.setPrefWidth(120);

        TableColumn<ProductCardData, Integer> stockCol = new TableColumn<>("Current Stock");
        stockCol.setCellValueFactory(new PropertyValueFactory<>("stock"));
        stockCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        stockCol.setOnEditCommit(event -> {
            ProductCardData product = event.getRowValue();
            int newStock = event.getNewValue();
            product.setStock(newStock);
            updateProductField(product.getId(), "stock", newStock);
        });
        stockCol.setPrefWidth(120);

        table.getColumns().addAll(idCol, nameCol, priceCol, stockCol);
        root.setCenter(table);

        HBox formRow = new HBox(10);
        formRow.setPadding(new Insets(15, 0, 0, 0));
        formRow.setAlignment(Pos.CENTER_LEFT);

        TextField nameInput = new TextField();
        nameInput.setPromptText("Product Name");

        TextField priceInput = new TextField();
        priceInput.setPromptText("Price (KSh)");

        TextField stockInput = new TextField();
        stockInput.setPromptText("Initial Stock");

        Button addBtn = new Button("➕ Add Product");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> {
            try {
                String name = nameInput.getText().trim();
                double price = Double.parseDouble(priceInput.getText().trim());
                int stock = Integer.parseInt(stockInput.getText().trim());

                if (name.isEmpty()) {
                    showErrorAlert("Input Error", "Please enter a product name.");
                    return;
                }

                saveNewProduct(name, price, stock);
                nameInput.clear();
                priceInput.clear();
                stockInput.clear();
                loadProductsFromDatabase();

            } catch (NumberFormatException ex) {
                showErrorAlert("Input Error", "Please enter valid numeric values for Price and Stock.");
            }
        });

        Button deleteBtn = new Button("🗑️ Delete Selected");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            ProductCardData selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteProduct(selected.getId());
                loadProductsFromDatabase();
            } else {
                showErrorAlert("Selection Error", "Please select a product from the table to delete.");
            }
        });

        formRow.getChildren().addAll(nameInput, priceInput, stockInput, addBtn, deleteBtn);
        root.setBottom(formRow);

        return root;
    }

    private VBox createAnalyticsView() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        Label header = new Label("Sales & Revenue Reports");
        header.getStyleClass().add("header-title");

        HBox kpiRow = new HBox(15);
        kpiRow.setAlignment(Pos.CENTER);

        VBox totalCard = createKpiCard("Today's Revenue", todaySalesLabel, "#27ae60");
        VBox txCard = createKpiCard("Total Transactions", todayTxLabel, "#2980b9");
        VBox cashCard = createKpiCard("Cash Sales", cashSalesLabel, "#8e44ad");
        VBox mpesaCard = createKpiCard("M-Pesa Sales", mpesaSalesLabel, "#d35400");

        kpiRow.getChildren().addAll(totalCard, txCard, cashCard, mpesaCard);

        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        startDatePicker = new DatePicker(LocalDate.now().minusDays(7));
        endDatePicker = new DatePicker(LocalDate.now());

        Button filterBtn = new Button("🔍 Filter Records");
        filterBtn.getStyleClass().add("btn-primary");
        filterBtn.setOnAction(e -> loadAnalyticsData());

        Button exportBtn = new Button("📥 Export CSV Report");
        exportBtn.getStyleClass().add("btn-success");
        exportBtn.setOnAction(e -> exportSalesToCSV());

        filterRow.getChildren().addAll(
            new Label("From:"), startDatePicker, 
            new Label("To:"), endDatePicker, 
            filterBtn, exportBtn
        );

        Label historyTitle = new Label("Recent Sales History");
        historyTitle.getStyleClass().add("section-title");

        TableView<SaleRecord> historyTable = new TableView<>();
        historyTable.setItems(salesHistory);

        TableColumn<SaleRecord, Integer> idCol = new TableColumn<>("Sale ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(100);

        TableColumn<SaleRecord, Double> amtCol = new TableColumn<>("Amount (KSh)");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        amtCol.setPrefWidth(150);

        TableColumn<SaleRecord, String> modeCol = new TableColumn<>("Method");
        modeCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        modeCol.setPrefWidth(120);

        TableColumn<SaleRecord, String> dateCol = new TableColumn<>("Date & Time");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        dateCol.setPrefWidth(220);

        historyTable.getColumns().addAll(idCol, amtCol, modeCol, dateCol);

        root.getChildren().addAll(header, kpiRow, filterRow, historyTitle, historyTable);
        return root;
    }

    private void exportSalesToCSV() {
        if (salesHistory.isEmpty()) {
            showErrorAlert("Export Warning", "No sales records available to export!");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Sales Report");
        fileChooser.setInitialFileName("SalesReport_" + LocalDate.now() + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(primaryStageRef);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Sale ID,Amount (KSh),Payment Method,Date & Time\n");
                for (SaleRecord record : salesHistory) {
                    writer.write(String.format("%d,%.2f,%s,\"%s\"\n",
                        record.getId(), record.getTotalAmount(), record.getPaymentMethod(), record.getSaleDate()));
                }
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Report successfully exported to:\n" + file.getAbsolutePath(), ButtonType.OK);
                alert.setHeaderText("Export Successful");
                alert.showAndWait();
            } catch (IOException e) {
                showErrorAlert("Export Error", "Failed to write CSV file: " + e.getMessage());
            }
        }
    }

    private VBox createKpiCard(String title, Label valueLabel, String accentColor) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setPrefSize(220, 90);
        card.setStyle(String.format("-fx-background-color: #ffffff; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2); -fx-border-color: %s; -fx-border-width: 0 0 0 4px;", accentColor));

        Label tLabel = new Label(title);
        tLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d; -fx-font-weight: bold;");

        valueLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        card.getChildren().addAll(tLabel, valueLabel);
        return card;
    }

    private void updateProductField(int productId, String columnName, Object newValue) {
        String query = "UPDATE products SET " + columnName + " = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            if (newValue instanceof Double) {
                stmt.setDouble(1, (Double) newValue);
            } else if (newValue instanceof Integer) {
                stmt.setInt(1, (Integer) newValue);
            }
            stmt.setInt(2, productId);
            stmt.executeUpdate();

            loadProductsFromDatabase();
        } catch (SQLException e) {
            showErrorAlert("Database Error", "Failed to update product: " + e.getMessage());
        }
    }

    private void saveNewProduct(String name, double price, int stock) {
        String query = "INSERT INTO products (name, price, stock) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setDouble(2, price);
            stmt.setInt(3, stock);
            stmt.executeUpdate();
        } catch (SQLException e) {
            showErrorAlert("Database Error", "Could not add product: " + e.getMessage());
        }
    }

    private void deleteProduct(int id) {
        String query = "DELETE FROM products WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            showErrorAlert("Database Error", "Could not delete product: " + e.getMessage());
        }
    }

    private void loadAnalyticsData() {
        salesHistory.clear();
        double totalRev = 0;
        int txCount = 0;
        double cashTotal = 0;
        double mpesaTotal = 0;

        String query = "SELECT id, total_amount, payment_method, sale_date FROM sales WHERE DATE(sale_date) BETWEEN ? AND ? ORDER BY sale_date DESC";

        LocalDate startDate = (startDatePicker != null && startDatePicker.getValue() != null) ? startDatePicker.getValue() : LocalDate.now().minusDays(30);
        LocalDate endDate = (endDatePicker != null && endDatePicker.getValue() != null) ? endDatePicker.getValue() : LocalDate.now();

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    double amount = rs.getDouble("total_amount");
                    String method = rs.getString("payment_method");
                    String date = rs.getString("sale_date");

                    salesHistory.add(new SaleRecord(id, amount, method, date));

                    totalRev += amount;
                    txCount++;
                    if ("M-PESA".equalsIgnoreCase(method)) {
                        mpesaTotal += amount;
                    } else {
                        cashTotal += amount;
                    }
                }
            }

            todaySalesLabel.setText(String.format("KSh %.2f", totalRev));
            todayTxLabel.setText(String.valueOf(txCount));
            cashSalesLabel.setText(String.format("KSh %.2f", cashTotal));
            mpesaSalesLabel.setText(String.format("KSh %.2f", mpesaTotal));

        } catch (SQLException e) {
            showErrorAlert("Database Error", "Failed to load sales analytics: " + e.getMessage());
        }
    }

    private ProductCardData findProductByName(String name) {
        return allProducts.stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private void loadProductsFromDatabase() {
        allProducts.clear();
        inventoryList.clear();

        String query = "SELECT id, name, price, stock FROM products";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                ProductCardData product = new ProductCardData(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getDouble("price"),
                    rs.getInt("stock")
                );
                allProducts.add(product);
                inventoryList.add(product);
            }
            renderProducts(allProducts);
        } catch (SQLException e) {
            showErrorAlert("Database Error", "Failed to fetch products: " + e.getMessage());
        }
    }

    private void filterProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            renderProducts(allProducts);
            return;
        }

        String lower = keyword.toLowerCase().trim();
        List<ProductCardData> filtered = allProducts.stream()
            .filter(p -> p.getName().toLowerCase().contains(lower))
            .collect(Collectors.toList());

        renderProducts(filtered);
    }

    private void renderProducts(List<ProductCardData> products) {
        productGrid.getChildren().clear();
        for (ProductCardData p : products) {
            productGrid.getChildren().add(createProductCard(p));
        }
    }

    private VBox createProductCard(ProductCardData p) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.getStyleClass().add("product-card");
        card.setPrefSize(165, 160);

        Label nameLabel = new Label(p.getName());
        nameLabel.getStyleClass().add("product-title");
        nameLabel.setWrapText(true);

        Label priceLabel = new Label(String.format("KSh %.2f", p.getPrice()));
        priceLabel.getStyleClass().add("product-price");

        Label stockLabel = new Label();
        if (p.getStock() <= 0) {
            stockLabel.setText("Out of Stock");
            stockLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 11px;");
        } else if (p.getStock() < 10) {
            stockLabel.setText("⚠️ Low Stock: " + p.getStock());
            stockLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 11px;");
        } else {
            stockLabel.setText("Stock: " + p.getStock());
            stockLabel.getStyleClass().add("stock-label");
        }

        Button addBtn = new Button("Add to Cart");
        addBtn.setDisable(p.getStock() <= 0);
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> addToCart(p));

        card.getChildren().addAll(nameLabel, priceLabel, stockLabel, addBtn);
        return card;
    }

    private void addToCart(ProductCardData p) {
        for (CartItem item : cartItems) {
            if (item.getName().equals(p.getName())) {
                if (item.getQuantity() + 1 > p.getStock()) {
                    showErrorAlert("Stock Limit", "Cannot add more items than current stock!");
                    return;
                }
                item.setQuantity(item.getQuantity() + 1);
                item.setTotalPrice(item.getQuantity() * p.getPrice());
                recalculateTotal();
                return;
            }
        }
        cartItems.add(new CartItem(p.getName(), 1, p.getPrice()));
        recalculateTotal();
    }

    private void recalculateTotal() {
        grandTotal = cartItems.stream().mapToDouble(CartItem::getTotalPrice).sum();
        totalLabel.setText(String.format("Total: KSh %.2f", grandTotal));
    }

    private void clearCart() {
        cartItems.clear();
        recalculateTotal();
    }

    private void handleCheckout() {
        if (cartItems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Your cart is empty!", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        Optional<PaymentDetails> paymentOpt = promptPaymentDetails();
        if (paymentOpt.isEmpty()) {
            return;
        }

        PaymentDetails payment = paymentOpt.get();

        String insertSaleQuery = "INSERT INTO sales (total_amount, payment_method, cashier_name) VALUES (?, ?, ?)";
        String updateStockQuery = "UPDATE products SET stock = stock - ? WHERE name = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement saleStmt = conn.prepareStatement(insertSaleQuery);
                 PreparedStatement stockStmt = conn.prepareStatement(updateStockQuery)) {

                saleStmt.setDouble(1, grandTotal);
                saleStmt.setString(2, payment.method);
                saleStmt.setString(3, loggedInUser);
                saleStmt.executeUpdate();

                for (CartItem item : cartItems) {
                    stockStmt.setInt(1, item.getQuantity());
                    stockStmt.setString(2, item.getName());
                    stockStmt.addBatch();
                }
                stockStmt.executeBatch();

                conn.commit();

                showReceiptDialog(payment);

                clearCart();
                loadProductsFromDatabase();

            } catch (SQLException ex) {
                conn.rollback();
                showErrorAlert("Transaction Failed", "Could not complete transaction: " + ex.getMessage());
            }

        } catch (SQLException e) {
            showErrorAlert("Database Error", e.getMessage());
        }
    }

    private Optional<PaymentDetails> promptPaymentDetails() {
        Dialog<PaymentDetails> dialog = new Dialog<>();
        dialog.setTitle("Process Payment");
        dialog.setHeaderText(String.format("Total Amount Due: KSh %.2f", grandTotal));

        ButtonType payButtonType = new ButtonType("Confirm Payment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(payButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 15, 10, 10));

        ComboBox<String> methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll("CASH", "M-PESA");
        methodCombo.setValue("CASH");

        TextField tenderedField = new TextField();
        tenderedField.setPromptText("Amount Tendered");

        Label changeLabel = new Label("Change: KSh 0.00");
        changeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");

        tenderedField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                double tendered = Double.parseDouble(newVal);
                double change = tendered - grandTotal;
                if (change >= 0) {
                    changeLabel.setText(String.format("Change: KSh %.2f", change));
                    changeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
                } else {
                    changeLabel.setText(String.format("Shortage: KSh %.2f", Math.abs(change)));
                    changeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                }
            } catch (NumberFormatException e) {
                changeLabel.setText("Invalid amount");
                changeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }
        });

        methodCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("M-PESA".equals(newVal)) {
                tenderedField.setText(String.format("%.2f", grandTotal));
                tenderedField.setDisable(true);
            } else {
                tenderedField.clear();
                tenderedField.setDisable(false);
            }
        });

        grid.add(new Label("Payment Method:"), 0, 0);
        grid.add(methodCombo, 1, 0);
        grid.add(new Label("Amount Tendered (KSh):"), 0, 1);
        grid.add(tenderedField, 1, 1);
        grid.add(changeLabel, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Button confirmBtn = (Button) dialog.getDialogPane().lookupButton(payButtonType);
        confirmBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                double tendered = Double.parseDouble(tenderedField.getText());
                if (tendered < grandTotal) {
                    showErrorAlert("Payment Error", "Tendered amount is less than the total balance!");
                    event.consume();
                }
            } catch (NumberFormatException e) {
                showErrorAlert("Input Error", "Please enter a valid numeric tendered amount.");
                event.consume();
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == payButtonType) {
                double tendered = Double.parseDouble(tenderedField.getText());
                double change = tendered - grandTotal;
                return new PaymentDetails(methodCombo.getValue(), tendered, Math.max(0, change));
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void showReceiptDialog(PaymentDetails payment) {
        Stage receiptStage = new Stage();
        receiptStage.setTitle("Receipt - Smart POS");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-background-color: #ffffff; -fx-font-family: 'Courier New', monospace;");

        Label header = new Label("=== SMART POS RECEIPT ===");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Label dateLabel = new Label("Date: " + timeStr + "\nServed by: " + loggedInUser);

        Separator sep1 = new Separator();

        VBox itemsBox = new VBox(5);
        for (CartItem item : cartItems) {
            HBox row = new HBox();
            Label name = new Label(item.getQuantity() + "x " + item.getName());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label price = new Label(String.format("KSh %.2f", item.getTotalPrice()));
            row.getChildren().addAll(name, spacer, price);
            itemsBox.getChildren().add(row);
        }

        Separator sep2 = new Separator();

        VBox totalsBox = new VBox(4);
        totalsBox.getChildren().add(createReceiptRow("TOTAL:", String.format("KSh %.2f", grandTotal), true));
        totalsBox.getChildren().add(createReceiptRow("PAYMENT (" + payment.method + "):", String.format("KSh %.2f", payment.tendered), false));
        totalsBox.getChildren().add(createReceiptRow("CHANGE DUE:", String.format("KSh %.2f", payment.change), false));

        Label footer = new Label("Thank you for shopping with us!");
        footer.setStyle("-fx-font-style: italic; -fx-padding: 15 0 0 0;");

        Button closeBtn = new Button("Done / Print");
        closeBtn.getStyleClass().add("btn-success");
        closeBtn.setOnAction(e -> receiptStage.close());

        content.getChildren().addAll(header, dateLabel, sep1, itemsBox, sep2, totalsBox, footer, closeBtn);

        Scene scene = new Scene(content, 360, 480);
        receiptStage.setScene(scene);
        receiptStage.showAndWait();
    }

    private HBox createReceiptRow(String label, String value, boolean isBold) {
        HBox row = new HBox();
        Label lbl = new Label(label);
        Label val = new Label(value);
        if (isBold) {
            lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            val.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lbl, spacer, val);
        return row;
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}