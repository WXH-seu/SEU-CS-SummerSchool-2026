package edu.seu.vcampus.client.service;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderStatusRequest;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Converts store UI actions into object-stream requests. */
public final class StoreClientService {
    private final ClientConnection connection;
    private final String sessionToken;

    public StoreClientService(ClientConnection connection, String sessionToken) {
        this.connection = connection;
        this.sessionToken = sessionToken;
    }

    public List<ProductDto> queryProducts(StoreQueryRequest query) throws IOException {
        return listRequest(Operation.STORE_PRODUCT_QUERY, query, ProductDto.class);
    }

    public void saveProduct(ProductDto product) throws IOException {
        request(Operation.STORE_PRODUCT_SAVE, product);
    }

    public void deleteProduct(String productId) throws IOException {
        request(Operation.STORE_PRODUCT_DELETE, new EntityIdRequest(productId));
    }

    public List<CartItemDto> queryCart() throws IOException {
        return listRequest(Operation.STORE_CART_QUERY, null, CartItemDto.class);
    }

    public void updateCart(String productId, int quantity) throws IOException {
        request(Operation.STORE_CART_UPDATE, new CartUpdateRequest(productId, quantity));
    }

    public OrderDto createOrder() throws IOException {
        Object body = request(Operation.STORE_ORDER_CREATE, null).getBody();
        if (!(body instanceof OrderDto)) {
            throw new IOException("服务器返回的数据格式不正确");
        }
        return (OrderDto) body;
    }

    public List<OrderDto> queryOrders() throws IOException {
        return listRequest(Operation.STORE_ORDER_QUERY, null, OrderDto.class);
    }

    public void updateOrderStatus(String orderId, String statusName) throws IOException {
        request(Operation.STORE_ORDER_STATUS, new OrderStatusRequest(orderId, statusName));
    }

    private <T> List<T> listRequest(Operation operation, Serializable body, Class<T> type)
            throws IOException {
        Object responseBody = request(operation, body).getBody();
        if (!(responseBody instanceof List)) {
            throw new IOException("服务器返回的数据格式不正确");
        }
        List<?> raw = (List<?>) responseBody;
        List<T> result = new ArrayList<T>();
        for (Object item : raw) {
            if (!type.isInstance(item)) {
                throw new IOException("服务器返回的数据类型不正确");
            }
            result.add(type.cast(item));
        }
        return result;
    }

    private ResponseMessage<?> request(Operation operation, Serializable body) throws IOException {
        ResponseMessage<?> response = connection.request(
                new RequestMessage<Serializable>(operation, sessionToken, body));
        if (!response.isSuccess()) {
            throw new IOException(response.getMessage());
        }
        return response;
    }
}
