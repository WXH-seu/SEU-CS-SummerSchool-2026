package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderItemDto;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.enums.Operation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Ensures store DTOs remain compatible with the object-stream protocol. */
public class StoreMessageSerializationTest {
    @Test
    public void serializesProductSaveRequest() throws Exception {
        ProductDto product = new ProductDto("P100", "测试商品", "文具", "说明",
                new BigDecimal("12.50"), 10, true);
        RequestMessage<ProductDto> request = new RequestMessage<ProductDto>(
                Operation.STORE_PRODUCT_SAVE, "session", product);
        RequestMessage<?> restored = roundTrip(request);
        assertEquals(Operation.STORE_PRODUCT_SAVE, restored.getOperation());
        ProductDto restoredProduct = (ProductDto) restored.getBody();
        assertEquals("P100", restoredProduct.getProductId());
        assertEquals(0, new BigDecimal("12.50").compareTo(restoredProduct.getPrice()));
    }

    @Test
    public void serializesCartUpdateRequest() throws Exception {
        RequestMessage<CartUpdateRequest> request = new RequestMessage<CartUpdateRequest>(
                Operation.STORE_CART_UPDATE, "session", new CartUpdateRequest("P100", 3));
        RequestMessage<?> restored = roundTrip(request);
        CartUpdateRequest body = (CartUpdateRequest) restored.getBody();
        assertEquals("P100", body.getProductId());
        assertEquals(3, body.getQuantity());
    }

    @Test
    public void serializesOrderWithItems() throws Exception {
        List<OrderItemDto> items = new ArrayList<OrderItemDto>();
        items.add(new OrderItemDto("ODI1", "ORD1", "P100", "测试商品",
                new BigDecimal("12.50"), 2, new BigDecimal("25.00")));
        OrderDto order = new OrderDto("ORD1", "student", new BigDecimal("25.00"),
                "待付款", "2026-08-25 10:00:00", items);
        RequestMessage<OrderDto> request = new RequestMessage<OrderDto>(
                Operation.STORE_ORDER_CREATE, "session", order);
        RequestMessage<?> restored = roundTrip(request);
        OrderDto restoredOrder = (OrderDto) restored.getBody();
        assertEquals("ORD1", restoredOrder.getOrderId());
        assertEquals(1, restoredOrder.getItems().size());
        assertEquals("测试商品", restoredOrder.getItems().get(0).getProductName());
    }

    private RequestMessage<?> roundTrip(RequestMessage<?> request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        return (RequestMessage<?>) input.readObject();
    }
}
