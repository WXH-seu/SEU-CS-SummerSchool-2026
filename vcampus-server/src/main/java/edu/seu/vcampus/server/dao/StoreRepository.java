package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** Persistence contract for the campus store. */
public interface StoreRepository {
    List<ProductDto> findProducts(StoreQueryRequest query) throws SQLException;
    ProductDto findProduct(String productId) throws SQLException;
    void saveProduct(ProductDto product) throws SQLException;
    boolean deleteProduct(String productId) throws SQLException;
    boolean productExists(String productId) throws SQLException;
    boolean productIsReferenced(String productId) throws SQLException;

    List<CartItemDto> findCart(String userId) throws SQLException;
    void upsertCartItem(String userId, String productId, int quantity) throws SQLException;
    void removeCartItem(String userId, String productId) throws SQLException;

    /** 对购物车中指定的商品结算；productIds 为空集合表示没有可结算项。 */
    OrderDto createOrder(String userId, Set<String> productIds) throws SQLException;
    List<OrderDto> findOrders(String userId) throws SQLException;
    boolean orderExists(String orderId) throws SQLException;
    void updateOrderStatus(String orderId, String statusName) throws SQLException;
}
