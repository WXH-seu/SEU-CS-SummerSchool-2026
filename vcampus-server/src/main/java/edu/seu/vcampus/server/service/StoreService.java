package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.BalanceRechargeRequest;
import edu.seu.vcampus.common.dto.OrderCreateRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderQueryRequest;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;
import edu.seu.vcampus.server.dao.StoreRepository;
import edu.seu.vcampus.server.dao.UserAccount;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Business rules and permission checks for the campus store. */
public final class StoreService {
    /** 单次充值上限（元），超出视为非法金额。 */
    private static final BigDecimal MAX_RECHARGE = new BigDecimal("10000.00");
    private static final Set<String> RECHARGE_CHANNELS = new HashSet<String>(Arrays.asList(
            "一卡通充值", "微信", "银行卡"));

    private static final Set<String> ORDER_STATUSES = new HashSet<String>(Arrays.asList(
            "已付款", "已发货", "已完成", "已取消"));

    private static final Set<String> TIME_RANGES = new HashSet<String>(Arrays.asList(
            "全部", "今天", "近7天", "近30天"));

    private final StoreRepository repository;

    public StoreService(StoreRepository repository) {
        this.repository = repository;
    }

    public ArrayList<ProductDto> queryProducts(UserAccount actor, StoreQueryRequest query)
            throws BusinessException, SQLException {
        requireActor(actor);
        boolean activeOnly = effectiveRole(actor) == SubSystemRole.ADMIN
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
        if (product.getImagePath() != null && product.getImagePath().length() > 255) {
            throw invalid("图片路径过长（最多 255 个字符）");
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

    public ArrayList<String> queryCategories(UserAccount actor)
            throws BusinessException, SQLException {
        requireActor(actor);
        boolean activeOnly = effectiveRole(actor) != SubSystemRole.ADMIN;
        return new ArrayList<String>(repository.findCategories(activeOnly));
    }

    public BigDecimal queryBalance(UserAccount actor)
            throws BusinessException, SQLException {
        requireShopper(actor);
        return repository.findBalance(actor.getUserId());
    }

    public BigDecimal rechargeBalance(UserAccount actor, BalanceRechargeRequest request)
            throws BusinessException, SQLException {
        requireShopper(actor);
        if (request == null || request.getAmount() == null || request.getChannel() == null) {
            throw invalid("充值金额与支付渠道不能为空");
        }
        BigDecimal amount = request.getAmount();
        String channel = request.getChannel().trim();
        if (amount.signum() <= 0) {
            throw invalid("充值金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw invalid("充值金额最多保留两位小数");
        }
        if (amount.compareTo(MAX_RECHARGE) > 0) {
            throw invalid("单次充值金额不能超过 10000 元");
        }
        if (!RECHARGE_CHANNELS.contains(channel)) {
            throw invalid("支付渠道必须为：一卡通充值、微信或银行卡");
        }
        return repository.rechargeBalance(actor.getUserId(), amount);
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

    public OrderDto createOrder(UserAccount actor, OrderCreateRequest request)
            throws BusinessException, SQLException {
        requireShopper(actor);
        Set<String> selected = new LinkedHashSet<String>();
        if (request != null && request.getProductIds() != null) {
            for (String productId : request.getProductIds()) {
                if (!isBlank(productId)) {
                    selected.add(productId.trim());
                }
            }
        }
        try {
            return repository.createOrder(actor.getUserId(), selected);
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message != null && (message.contains("购物车为空")
                    || message.contains("勾选"))) {
                throw new BusinessException(ResponseCode.INVALID_REQUEST, message);
            }
            if (message != null && (message.contains("库存不足")
                    || message.contains("已下架")
                    || message.contains("余额不足"))) {
                throw new BusinessException(ResponseCode.CONFLICT, message);
            }
            throw e;
        }
    }

    public ArrayList<OrderDto> queryOrders(UserAccount actor, OrderQueryRequest request)
            throws BusinessException, SQLException {
        requireActor(actor);
        if (request != null && !isBlank(request.getTimeRange())
                && !TIME_RANGES.contains(request.getTimeRange().trim())) {
            throw invalid("时间范围必须为：全部、今天、近7天或近30天");
        }
        String userId = effectiveRole(actor) == SubSystemRole.ADMIN
                ? null : actor.getUserId();
        return new ArrayList<OrderDto>(repository.findOrders(userId, request));
    }

    public void updateOrderStatus(UserAccount actor, String orderId, String statusName)
            throws BusinessException, SQLException {
        requireAdmin(actor);
        requireId(orderId);
        if (isBlank(statusName) || !ORDER_STATUSES.contains(statusName.trim())) {
            throw invalid("订单状态必须为已付款、已发货、已完成或已取消");
        }
        if (!repository.orderExists(orderId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "订单不存在");
        }
        repository.updateOrderStatus(orderId, statusName.trim());
    }

    /**
     * 用户取消订单。状态流转约定：
     * 已付款 → 已发货 → 已完成（正常流程）；未发货前用户可取消为「已取消」；
     * 管理员可自行调整状态，但用户只能在「已付款」状态取消本人订单。
     * 取消时在同一事务里退回库存与余额。
     */
    public void cancelOrder(UserAccount actor, String orderId)
            throws BusinessException, SQLException {
        requireShopper(actor);
        requireId(orderId);
        try {
            repository.cancelOrder(orderId.trim(), actor.getUserId());
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message != null && message.contains("订单不存在")) {
                throw new BusinessException(ResponseCode.NOT_FOUND, message);
            }
            if (message != null && message.contains("无权取消")) {
                throw new BusinessException(ResponseCode.FORBIDDEN, message);
            }
            if (message != null && message.contains("仅已付款订单")) {
                throw new BusinessException(ResponseCode.CONFLICT, message);
            }
            throw e;
        }
    }

    private void requireActor(UserAccount actor) throws BusinessException {
        if (actor == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void requireShopper(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) == SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "管理员不参与购物，仅维护商品与订单");
        }
    }

    private void requireAdmin(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) != SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以维护商品与订单状态");
        }
    }

    private SubSystemRole effectiveRole(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(), SubSystem.STORE);
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
