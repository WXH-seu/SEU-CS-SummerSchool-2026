package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderItemDto;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.server.database.AccessDatabase;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Access-backed implementation of the store repository. */
public final class AccessStoreRepository implements StoreRepository {
    private static final DateTimeFormatter ORDER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AccessDatabase database;

    public AccessStoreRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeSchema();
        seedDemoData();
    }

    @Override
    public List<ProductDto> findProducts(StoreQueryRequest query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM [tblProduct] WHERE 1=1");
        List<Object> parameters = new ArrayList<Object>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                sql.append(" AND ([productId] LIKE ? OR [productName] LIKE ?)");
                String pattern = "%" + query.getKeyword().trim() + "%";
                parameters.add(pattern);
                parameters.add(pattern);
            }
            if (!isBlank(query.getCategory())) {
                sql.append(" AND [category] = ?");
                parameters.add(query.getCategory().trim());
            }
            if (query.isActiveOnly()) {
                sql.append(" AND [active] = ?");
                parameters.add(Boolean.TRUE);
            }
        }
        sql.append(" ORDER BY [productId]");
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<ProductDto> products = new ArrayList<ProductDto>();
                while (result.next()) {
                    products.add(readProduct(result));
                }
                return products;
            }
        }
    }

    @Override
    public ProductDto findProduct(String productId) throws SQLException {
        String sql = "SELECT * FROM [tblProduct] WHERE [productId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, productId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readProduct(result) : null;
            }
        }
    }

    @Override
    public void saveProduct(ProductDto product) throws SQLException {
        String update = "UPDATE [tblProduct] SET [productName]=?, [category]=?, "
                + "[description]=?, [price]=?, [stock]=?, [active]=? "
                + "WHERE [productId]=?";
        String insert = "INSERT INTO [tblProduct] ([productName], [category], "
                + "[description], [price], [stock], [active], [productId]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            if (executeProductSave(connection, update, product) == 0) {
                executeProductSave(connection, insert, product);
            }
        }
    }

    @Override
    public boolean deleteProduct(String productId) throws SQLException {
        return deleteById("tblProduct", "productId", productId);
    }

    @Override
    public boolean productExists(String productId) throws SQLException {
        return countReferences("tblProduct", "productId", productId) > 0;
    }

    @Override
    public boolean productIsReferenced(String productId) throws SQLException {
        return countReferences("tblCartItem", "productId", productId) > 0
                || countReferences("tblOrderItem", "productId", productId) > 0;
    }

    @Override
    public List<CartItemDto> findCart(String userId) throws SQLException {
        try (Connection connection = database.openConnection()) {
            return findCartItems(connection, userId);
        }
    }

    @Override
    public void upsertCartItem(String userId, String productId, int quantity)
            throws SQLException {
        String update = "UPDATE [tblCartItem] SET [quantity]=? "
                + "WHERE [userId]=? AND [productId]=?";
        try (Connection connection = database.openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setInt(1, quantity);
                statement.setString(2, userId);
                statement.setString(3, productId);
                if (statement.executeUpdate() > 0) {
                    return;
                }
            }
            String insert = "INSERT INTO [tblCartItem] "
                    + "([cartItemId], [userId], [productId], [quantity]) "
                    + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, newId("CID"));
                statement.setString(2, userId);
                statement.setString(3, productId);
                statement.setInt(4, quantity);
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void removeCartItem(String userId, String productId) throws SQLException {
        String sql = "DELETE FROM [tblCartItem] WHERE [userId]=? AND [productId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, productId);
            statement.executeUpdate();
        }
    }

    @Override
    public OrderDto createOrder(String userId) throws SQLException {
        String orderId = newId("ORD");
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                List<CartItemDto> cart = findCartItems(connection, userId);
                if (cart.isEmpty()) {
                    throw new SQLException("购物车为空，无法下单");
                }
                BigDecimal total = BigDecimal.ZERO;
                List<OrderItemDto> items = new ArrayList<OrderItemDto>();
                for (CartItemDto item : cart) {
                    if (!item.isActive()) {
                        throw new SQLException("商品已下架：" + item.getProductName());
                    }
                    if (item.getStock() < item.getQuantity()) {
                        throw new SQLException("库存不足：" + item.getProductName()
                                + "（剩余 " + item.getStock() + "）");
                    }
                    BigDecimal subtotal = money(item.getUnitPrice().multiply(
                            BigDecimal.valueOf(item.getQuantity())));
                    items.add(new OrderItemDto(newId("ODI"), orderId,
                            item.getProductId(), item.getProductName(),
                            money(item.getUnitPrice()), item.getQuantity(), subtotal));
                    total = total.add(subtotal);
                }
                String now = LocalDateTime.now().format(ORDER_TIME_FORMAT);
                insertOrder(connection, orderId, userId, money(total), "待付款", now);
                for (OrderItemDto item : items) {
                    insertOrderItem(connection, item);
                    deductStock(connection, item.getProductId(), item.getQuantity());
                }
                clearCart(connection, userId);
                connection.commit();
                return new OrderDto(orderId, userId, money(total), "待付款", now, items);
            } catch (SQLException e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<OrderDto> findOrders(String userId) throws SQLException {
        String sql = "SELECT * FROM [tblOrder]"
                + (userId == null ? "" : " WHERE [userId] = ?")
                + " ORDER BY [orderTime] DESC";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId != null) {
                statement.setString(1, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<OrderDto> orders = new ArrayList<OrderDto>();
                while (result.next()) {
                    orders.add(readOrder(connection, result));
                }
                return orders;
            }
        }
    }

    @Override
    public boolean orderExists(String orderId) throws SQLException {
        return countReferences("tblOrder", "orderId", orderId) > 0;
    }

    @Override
    public void updateOrderStatus(String orderId, String statusName) throws SQLException {
        String sql = "UPDATE [tblOrder] SET [statusName]=? WHERE [orderId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, statusName);
            statement.setString(2, orderId);
            statement.executeUpdate();
        }
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = database.openConnection()) {
            if (!tableExists(connection, "tblProduct")) {
                execute(connection, "CREATE TABLE [tblProduct] ("
                        + "[productId] TEXT(20) NOT NULL PRIMARY KEY, "
                        + "[productName] TEXT(64) NOT NULL, [category] TEXT(32), "
                        + "[description] TEXT(255), [price] CURRENCY NOT NULL, "
                        + "[stock] INTEGER NOT NULL, [active] YESNO NOT NULL, "
                        + "CONSTRAINT [uqProductName] UNIQUE ([productName]))");
            }
            if (!tableExists(connection, "tblCartItem")) {
                execute(connection, "CREATE TABLE [tblCartItem] ("
                        + "[cartItemId] TEXT(40) NOT NULL PRIMARY KEY, "
                        + "[userId] TEXT(32) NOT NULL, [productId] TEXT(20) NOT NULL, "
                        + "[quantity] INTEGER NOT NULL, "
                        + "CONSTRAINT [uqCartUserProduct] UNIQUE ([userId], [productId]), "
                        + "CONSTRAINT [fkCartProduct] FOREIGN KEY ([productId]) "
                        + "REFERENCES [tblProduct] ([productId]))");
            }
            if (!tableExists(connection, "tblOrder")) {
                execute(connection, "CREATE TABLE [tblOrder] ("
                        + "[orderId] TEXT(40) NOT NULL PRIMARY KEY, "
                        + "[userId] TEXT(32) NOT NULL, [totalAmount] CURRENCY NOT NULL, "
                        + "[statusName] TEXT(16) NOT NULL, [orderTime] TEXT(19) NOT NULL)");
            }
            if (!tableExists(connection, "tblOrderItem")) {
                execute(connection, "CREATE TABLE [tblOrderItem] ("
                        + "[orderItemId] TEXT(40) NOT NULL PRIMARY KEY, "
                        + "[orderId] TEXT(40) NOT NULL, [productId] TEXT(20) NOT NULL, "
                        + "[productName] TEXT(64) NOT NULL, [unitPrice] CURRENCY NOT NULL, "
                        + "[quantity] INTEGER NOT NULL, [subtotal] CURRENCY NOT NULL, "
                        + "CONSTRAINT [fkOrderItemOrder] FOREIGN KEY ([orderId]) "
                        + "REFERENCES [tblOrder] ([orderId]), "
                        + "CONSTRAINT [fkOrderItemProduct] FOREIGN KEY ([productId]) "
                        + "REFERENCES [tblProduct] ([productId]))");
            }
        }
    }

    private void seedDemoData() throws SQLException {
        if (!productExists("P001")) {
            saveProduct(new ProductDto("P001", "东南大学笔记本", "文具",
                    "印有东南大学校徽的皮面笔记本", new BigDecimal("12.50"), 100, true));
        }
        if (!productExists("P002")) {
            saveProduct(new ProductDto("P002", "vCampus 纪念马克杯", "文创",
                    "虚拟校园主题陶瓷马克杯", new BigDecimal("25.00"), 50, true));
        }
        if (!productExists("P003")) {
            saveProduct(new ProductDto("P003", "数据结构教材", "图书",
                    "计算机专业核心课程教材", new BigDecimal("45.80"), 30, true));
        }
        if (!productExists("P004")) {
            saveProduct(new ProductDto("P004", "校园明信片套装", "文创",
                    "九龙湖校区风景明信片十张装", new BigDecimal("8.00"), 200, true));
        }
        if (!productExists("P005")) {
            saveProduct(new ProductDto("P005", "运动水壶", "生活",
                    "校园体育课推荐运动水壶（演示缺货商品）", new BigDecimal("30.00"), 0, true));
        }
        if (!productExists("P006")) {
            saveProduct(new ProductDto("P006", "旧版数据库教材", "图书",
                    "已下架的演示商品，仅管理员可见", new BigDecimal("15.00"), 5, false));
        }
    }

    private List<CartItemDto> findCartItems(Connection connection, String userId)
            throws SQLException {
        String sql = "SELECT c.[productId], p.[productName], p.[price], c.[quantity], "
                + "p.[stock], p.[active] FROM [tblCartItem] c "
                + "INNER JOIN [tblProduct] p ON c.[productId] = p.[productId] "
                + "WHERE c.[userId] = ? ORDER BY c.[cartItemId]";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                List<CartItemDto> items = new ArrayList<CartItemDto>();
                while (result.next()) {
                    items.add(new CartItemDto(result.getString("productId"),
                            result.getString("productName"),
                            result.getBigDecimal("price"),
                            result.getInt("quantity"),
                            result.getInt("stock"),
                            result.getBoolean("active")));
                }
                return items;
            }
        }
    }

    private void insertOrder(Connection connection, String orderId, String userId,
                             BigDecimal total, String statusName, String orderTime)
            throws SQLException {
        String sql = "INSERT INTO [tblOrder] "
                + "([orderId], [userId], [totalAmount], [statusName], [orderTime]) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            statement.setString(2, userId);
            statement.setBigDecimal(3, total);
            statement.setString(4, statusName);
            statement.setString(5, orderTime);
            statement.executeUpdate();
        }
    }

    private void insertOrderItem(Connection connection, OrderItemDto item)
            throws SQLException {
        String sql = "INSERT INTO [tblOrderItem] ([orderItemId], [orderId], [productId], "
                + "[productName], [unitPrice], [quantity], [subtotal]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getOrderItemId());
            statement.setString(2, item.getOrderId());
            statement.setString(3, item.getProductId());
            statement.setString(4, item.getProductName());
            statement.setBigDecimal(5, item.getUnitPrice());
            statement.setInt(6, item.getQuantity());
            statement.setBigDecimal(7, item.getSubtotal());
            statement.executeUpdate();
        }
    }

    private void deductStock(Connection connection, String productId, int quantity)
            throws SQLException {
        String sql = "UPDATE [tblProduct] SET [stock] = [stock] - ? WHERE [productId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setString(2, productId);
            statement.executeUpdate();
        }
    }

    private void clearCart(Connection connection, String userId) throws SQLException {
        String sql = "DELETE FROM [tblCartItem] WHERE [userId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
    }

    private OrderDto readOrder(Connection connection, ResultSet result) throws SQLException {
        String orderId = result.getString("orderId");
        List<OrderItemDto> items = findOrderItems(connection, orderId);
        return new OrderDto(orderId, result.getString("userId"),
                result.getBigDecimal("totalAmount"), result.getString("statusName"),
                result.getString("orderTime"), items);
    }

    private List<OrderItemDto> findOrderItems(Connection connection, String orderId)
            throws SQLException {
        String sql = "SELECT * FROM [tblOrderItem] WHERE [orderId] = ? ORDER BY [orderItemId]";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                List<OrderItemDto> items = new ArrayList<OrderItemDto>();
                while (result.next()) {
                    items.add(new OrderItemDto(result.getString("orderItemId"),
                            result.getString("orderId"), result.getString("productId"),
                            result.getString("productName"),
                            result.getBigDecimal("unitPrice"),
                            result.getInt("quantity"),
                            result.getBigDecimal("subtotal")));
                }
                return items;
            }
        }
    }

    private ProductDto readProduct(ResultSet result) throws SQLException {
        return new ProductDto(result.getString("productId"),
                result.getString("productName"), result.getString("category"),
                result.getString("description"), result.getBigDecimal("price"),
                result.getInt("stock"), result.getBoolean("active"));
    }

    private int executeProductSave(Connection connection, String sql, ProductDto product)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getProductName());
            setNullableString(statement, 2, product.getCategory());
            setNullableString(statement, 3, product.getDescription());
            statement.setBigDecimal(4, money(product.getPrice()));
            statement.setInt(5, product.getStock());
            statement.setBoolean(6, product.isActive());
            statement.setString(7, product.getProductId());
            return statement.executeUpdate();
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Keep the original failure.
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean deleteById(String table, String idColumn, String id) throws SQLException {
        String sql = "DELETE FROM [" + table + "] WHERE [" + idColumn + "]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private int countReferences(String table, String column, String value) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [" + table + "] WHERE [" + column + "]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void bindParameters(PreparedStatement statement, List<Object> values)
            throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Boolean) {
                statement.setBoolean(i + 1, ((Boolean) value).booleanValue());
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (isBlank(value)) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
