package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.OrderCreateRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
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
    private UserAccount admin;
    private UserAccount studentAccount;

    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        AccessUserRepository users = new AccessUserRepository(database, new PasswordHasher());
        AccessStoreRepository store = new AccessStoreRepository(database);
        service = new StoreService(store);
        admin = users.findById("admin");
        studentAccount = users.findById("student");
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

        OrderDto order = service.createOrder(studentAccount,
                new OrderCreateRequest(Arrays.asList("P001", "P002")));
        assertEquals("已付款", order.getStatusName());
        assertEquals(2, order.getItems().size());
        assertEquals(0, new BigDecimal("50.00").compareTo(order.getTotalAmount()));

        assertTrue(service.queryCart(studentAccount).isEmpty());
        assertEquals(98, findProduct("P001").getStock());
        assertEquals(49, findProduct("P002").getStock());
        assertEquals(1, service.queryOrders(studentAccount).size());
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

        assertEquals(1, service.queryOrders(admin).size());
        service.updateOrderStatus(admin, order.getOrderId(), "已发货");
        assertEquals("已发货", service.queryOrders(studentAccount).get(0).getStatusName());
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
}
