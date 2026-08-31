package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.dao.StoreRepository;
import edu.seu.vcampus.server.dao.UserAccount;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Business rules and permission checks for the campus store. */
public final class StoreService {
    private static final Set<String> ORDER_STATUSES = new HashSet<String>(Arrays.asList(
            "待付款", "已付款", "已发货", "已完成", "已取消"));

    private final StoreRepository repository;

    public StoreService(StoreRepository repository) {
        this.repository = repository;
    }

    public ArrayList<ProductDto> queryProducts(UserAccount actor, StoreQueryRequest query)
            throws BusinessException, SQLException {
        requireActor(actor);
        boolean activeOnly = actor.getRole() == Role.ADMIN
                ? (query != null && query.isActiveOnly()) : true;
        return new ArrayList<ProductDto>(repository.findProducts(new StoreQueryRequest(
                query == null ? null : query.getKeyword(),
                query == null ? null : query.getCategory(),
                activeOnly)));
    }

    public void saveProduct(UserAccount actor, ProductDto product)
            throws BusinessException, SQLException {
        requireAdmin(actor);
        if (product == null || isBlank(product.getProductId())
                || isBlank(product.getProductName())) {
            throw invalid("商品编号和名称不能为空");
        }
        if (product.getPrice() == null || product.getPrice().signum() < 0) {
            throw invalid("商品单价必须大于等于 0");
        }
        if (product.getStock() < 0) {
            throw invalid("商品库存不能为负数");
        }
        repository.saveProduct(product);
    }

    public void deleteProduct(UserAccount actor, String productId)
            throws BusinessException, SQLException {
        requireAdmin(actor);
        requireId(productId);
        if (repository.productIsReferenced(productId)) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "商品仍被购物车或订单引用，请先下架");
        }
        if (!repository.deleteProduct(productId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "商品记录不存在");
        }
    }

    public ArrayList<CartItemDto> queryCart(UserAccount actor)
            throws BusinessException, SQLException {
        requireShopper(actor);
        return new ArrayList<CartItemDto>(repository.findCart(actor.getUserId()));
    }

    public void updateCart(UserAccount actor, CartUpdateRequest request)
            throws BusinessException, SQLException {
        requireShopper(actor);
        if (request == null || isBlank(request.getProductId())) {
            throw invalid("商品编号不能为空");
        }
        ProductDto product = repository.findProduct(request.getProductId().trim());
        if (product == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "商品不存在");
        }
        if (request.getQuantity() <= 0) {
            repository.removeCartItem(actor.getUserId(), product.getProductId());
            return;
        }
        if (!product.isActive()) {
            throw new BusinessException(ResponseCode.CONFLICT, "商品已下架，无法加入购物车");
        }
        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "库存不足（剩余 " + product.getStock() + "）");
        }
        repository.upsertCartItem(actor.getUserId(), product.getProductId(),
                request.getQuantity());
    }

    public OrderDto createOrder(UserAccount actor) throws BusinessException, SQLException {
        requireShopper(actor);
        try {
            return repository.createOrder(actor.getUserId());
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message != null && message.contains("购物车为空")) {
                throw new BusinessException(ResponseCode.INVALID_REQUEST, message);
            }
            if (message != null && (message.contains("库存不足")
                    || message.contains("已下架"))) {
                throw new BusinessException(ResponseCode.CONFLICT, message);
            }
            throw e;
        }
    }

    public ArrayList<OrderDto> queryOrders(UserAccount actor)
            throws BusinessException, SQLException {
        requireActor(actor);
        String userId = actor.getRole() == Role.ADMIN ? null : actor.getUserId();
        return new ArrayList<OrderDto>(repository.findOrders(userId));
    }

    public void updateOrderStatus(UserAccount actor, String orderId, String statusName)
            throws BusinessException, SQLException {
        requireAdmin(actor);
        requireId(orderId);
        if (isBlank(statusName) || !ORDER_STATUSES.contains(statusName.trim())) {
            throw invalid("订单状态必须为待付款、已付款、已发货、已完成或已取消");
        }
        if (!repository.orderExists(orderId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "订单不存在");
        }
        repository.updateOrderStatus(orderId, statusName.trim());
    }

    private void requireActor(UserAccount actor) throws BusinessException {
        if (actor == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void requireShopper(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (actor.getRole() == Role.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "管理员不参与购物，仅维护商品与订单");
        }
    }

    private void requireAdmin(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (actor.getRole() != Role.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以维护商品与订单状态");
        }
    }

    private void requireId(String id) throws BusinessException {
        if (isBlank(id)) {
            throw invalid("记录编号不能为空");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResponseCode.INVALID_REQUEST, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
