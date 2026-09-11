package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.BalanceRechargeRequest;
import edu.seu.vcampus.common.dto.OrderCreateRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderQueryRequest;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.dao.AccessStoreRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the real Access store schema, cart, order and role rules. */
public class StoreServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private StoreService service;
    private AccessDatabase database;
    private AccessUserRepository users;
    private UserAccount admin;
    private UserAccount studentAccount;

    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        database = new AccessDatabase(file.getAbsolutePath());
        users = new AccessUserRepository(database, new PasswordHasher());
        AccessStoreRepository store = new AccessStoreRepository(database);
        service = new StoreService(store);
        admin = users.findById("admin");
        studentAccount = users.findById("student");
    }

    @Test
    public void userCancelsPaidOrderAndGetsStockAndBalanceBack() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 2));
        OrderDto order = service.createOrder(studentAccount,
                new OrderCreateRequest(Collections.singletonList("P001")));
        assertEquals(98, findProduct("P001").getStock());
        assertEquals(0, new BigDecimal("75.00").compareTo(
                service.queryBalance(studentAccount)));

        service.cancelOrder(studentAccount, order.getOrderId());

        assertEquals(100, findProduct("P001").getStock());
        assertEquals(0, new BigDecimal("100.00").compareTo(
                service.queryBalance(studentAccount)));
        assertEquals("已取消",
                service.queryOrders(studentAccount, null).get(0).getStatusName());
    }

    @Test
    public void cancelIsRejectedForShippedOrderOrOtherUsersOrder() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 1));
        OrderDto order = service.createOrder(studentAccount,
                new OrderCreateRequest(Collections.singletonList("P001")));

        service.updateOrderStatus(admin, order.getOrderId(), "已发货");
        try {
            service.cancelOrder(studentAccount, order.getOrderId());
            fail("Shipped order should not be cancellable by the user");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        PasswordHasher hasher = new PasswordHasher();
        String salt = hasher.newSalt();
        users.insert(new UserAccount("student2", hasher.hash("student123", salt), salt,
                "演示学生二", Role.STUDENT, true));
        UserAccount other = users.findById("student2");
        try {
            service.cancelOrder(other, order.getOrderId());
            fail("Other users should not cancel someone else's order");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
    }

    @Test
    public void cartAndOrderReferenceExistingUsers() throws Exception {
        assertTrue(hasUserForeignKey("tblCartItem"));
        assertTrue(hasUserForeignKey("tblOrder"));
    }

    @Test
    public void seedsProductsAndLetsShopperSeeActiveOnly() throws Exception {
        assertFalse(service.queryProducts(studentAccount, null).isEmpty());
        for (ProductDto product : service.queryProducts(studentAccount, null)) {
            assertTrue("shopper must only see active products", product.isActive());
        }
        assertNotNull(findProduct("P006"));
        assertFalse(findProduct("P006").isActive());
    }

    @Test
    public void shopperCreatesPaidOrderAndStockIsDeducted() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 2));
        service.updateCart(studentAccount, new CartUpdateRequest("P002", 1));
        assertEquals(2, service.queryCart(studentAccount).size());
        assertEquals(0, new BigDecimal("100.00").compareTo(
                service.queryBalance(studentAccount)));

        OrderDto order = service.createOrder(studentAccount,
                new OrderCreateRequest(Arrays.asList("P001", "P002")));
        assertEquals("已付款", order.getStatusName());
        assertEquals(2, order.getItems().size());
        assertEquals(0, new BigDecimal("50.00").compareTo(order.getTotalAmount()));

        assertTrue(service.queryCart(studentAccount).isEmpty());
        assertEquals(98, findProduct("P001").getStock());
        assertEquals(49, findProduct("P002").getStock());
        assertEquals(0, new BigDecimal("50.00").compareTo(
                service.queryBalance(studentAccount)));
        assertEquals(1, service.queryOrders(studentAccount, null).size());
    }

    @Test
    public void rechargeValidatesAmountAndChannel() throws Exception {
        BigDecimal balance = service.rechargeBalance(studentAccount,
                new BalanceRechargeRequest(new BigDecimal("200.00"), "一卡通充值"));
        assertEquals(0, new BigDecimal("300.00").compareTo(balance));

        assertInvalidRecharge(new BigDecimal("0.00"), "微信");
        assertInvalidRecharge(new BigDecimal("-1.00"), "微信");
        assertInvalidRecharge(new BigDecimal("1.234"), "微信");
        assertInvalidRecharge(new BigDecimal("10001.00"), "银行卡");
        assertInvalidRecharge(new BigDecimal("50.00"), "现金");
    }

    private void assertInvalidRecharge(BigDecimal amount, String channel) throws Exception {
        try {
            service.rechargeBalance(studentAccount,
                    new BalanceRechargeRequest(amount, channel));
            fail("Invalid recharge should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
        }
    }

    @Test
    public void insufficientBalanceRollsBackWithoutSideEffects() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P003", 3));
        try {
            service.createOrder(studentAccount,
                    new OrderCreateRequest(Collections.singletonList("P003")));
            fail("Insufficient balance should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("余额不足"));
        }
        // 订单不生成、购物车仍在、库存不扣、余额不变。
        assertEquals(1, service.queryCart(studentAccount).size());
        assertEquals(30, findProduct("P003").getStock());
        assertEquals(0, new BigDecimal("100.00").compareTo(
                service.queryBalance(studentAccount)));
        assertTrue(service.queryOrders(studentAccount, null).isEmpty());
    }

    @Test
    public void orderQueryFiltersByStatusCategoryAndTimeRange() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 1));
        service.createOrder(studentAccount,
                new OrderCreateRequest(Collections.singletonList("P001")));

        assertEquals(1, service.queryOrders(studentAccount,
                new OrderQueryRequest("已付款", null, "全部")).size());
        assertEquals(0, service.queryOrders(studentAccount,
                new OrderQueryRequest("已完成", null, "全部")).size());

        assertEquals(1, service.queryOrders(studentAccount,
                new OrderQueryRequest(null, "文具", "全部")).size());
        assertEquals(0, service.queryOrders(studentAccount,
                new OrderQueryRequest(null, "图书", "全部")).size());

        assertEquals(1, service.queryOrders(studentAccount,
                new OrderQueryRequest(null, null, "今天")).size());

        try {
            service.queryOrders(studentAccount,
                    new OrderQueryRequest(null, null, "上月"));
            fail("Invalid time range should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
        }
    }

    @Test
    public void partialSelectionOrdersOnlyCheckedItems() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 2));
        service.updateCart(studentAccount, new CartUpdateRequest("P002", 1));
        service.updateCart(studentAccount, new CartUpdateRequest("P003", 1));

        // 只勾选 P001：订单只有 1 行，只扣 P001 库存，其余商品留在购物车。
        OrderDto order = service.createOrder(studentAccount,
                new OrderCreateRequest(Collections.singletonList("P001")));
        assertEquals("已付款", order.getStatusName());
        assertEquals(1, order.getItems().size());
        assertEquals("P001", order.getItems().get(0).getProductId());
        assertEquals(0, new BigDecimal("25.00").compareTo(order.getTotalAmount()));

        assertEquals(98, findProduct("P001").getStock());
        assertEquals(50, findProduct("P002").getStock());
        assertEquals(0, new BigDecimal("75.00").compareTo(
                service.queryBalance(studentAccount)));
        assertEquals(2, service.queryCart(studentAccount).size());
    }

    @Test
    public void emptySelectionIsRejectedWithoutSideEffects() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 1));
        try {
            service.createOrder(studentAccount, new OrderCreateRequest(
                    Collections.<String>emptyList()));
            fail("Empty selection should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
        }
        assertEquals(1, service.queryCart(studentAccount).size());
        assertEquals(100, findProduct("P001").getStock());
    }

    @Test
    public void stockShortageAtOrderTimeIsRejected() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P001", 1));
        service.updateCart(studentAccount, new CartUpdateRequest("P002", 1));
        // Admin empties the stock of P001 while it is still in the cart.
        ProductDto current = findProduct("P001");
        service.saveProduct(admin, new ProductDto(current.getProductId(),
                current.getProductName(), current.getCategory(), current.getDescription(),
                current.getPrice(), 0, true));
        try {
            service.createOrder(studentAccount,
                    new OrderCreateRequest(Arrays.asList("P001", "P002")));
            fail("Order should be rejected when stock becomes insufficient");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("库存不足"));
        }
    }

    @Test
    public void adminManagesOrdersAndStatuses() throws Exception {
        service.updateCart(studentAccount, new CartUpdateRequest("P004", 3));
        OrderDto order = service.createOrder(studentAccount,
                new OrderCreateRequest(Collections.singletonList("P004")));

        assertEquals(1, service.queryOrders(admin, null).size());
        service.updateOrderStatus(admin, order.getOrderId(), "已发货");
        assertEquals("已发货",
                service.queryOrders(studentAccount, null).get(0).getStatusName());
        try {
            service.updateOrderStatus(studentAccount, order.getOrderId(), "已取消");
            fail("Only admin can change order status");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
    }

    @Test
    public void roleRulesAndProductDeleteReferenceCheck() throws Exception {
        try {
            service.saveProduct(studentAccount, new ProductDto("P900", "越权商品",
                    "文具", "", new BigDecimal("1.00"), 1, true));
            fail("Student should not maintain products");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }

        service.saveProduct(admin, new ProductDto("P900", "临时商品",
                "文具", "", new BigDecimal("1.00"), 1, true));
        assertNotNull(findProduct("P900"));
        service.updateCart(studentAccount, new CartUpdateRequest("P900", 1));
        try {
            service.deleteProduct(admin, "P900");
            fail("Referenced product should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
        service.updateCart(studentAccount, new CartUpdateRequest("P900", 0));
        service.deleteProduct(admin, "P900");
        try {
            service.deleteProduct(admin, "P900");
            fail("Missing product should not be deleted twice");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.NOT_FOUND, expected.getResponseCode());
        }
    }

    private ProductDto findProduct(String productId) throws Exception {
        for (ProductDto product : service.queryProducts(admin,
                new StoreQueryRequest(productId, null, false))) {
            if (product.getProductId().equals(productId)) {
                return product;
            }
        }
        return null;
    }

    private boolean hasUserForeignKey(String table) throws Exception {
        try (Connection connection = database.openConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (keys.next()) {
                if ("userId".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && "tblUser".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                        && "userId".equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
