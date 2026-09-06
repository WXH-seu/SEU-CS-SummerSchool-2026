package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.CartUpdateRequest;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.dto.OrderCreateRequest;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderStatusRequest;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.service.BusinessException;
import edu.seu.vcampus.server.service.StoreService;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.EnumSet;

/** Handles protocol operations owned by the campus store module. */
public final class StoreRequestHandler {
    private static final EnumSet<Operation> OPERATIONS = EnumSet.of(
            Operation.STORE_PRODUCT_QUERY, Operation.STORE_PRODUCT_SAVE,
            Operation.STORE_PRODUCT_DELETE, Operation.STORE_CART_QUERY,
            Operation.STORE_CART_UPDATE, Operation.STORE_ORDER_CREATE,
            Operation.STORE_ORDER_QUERY, Operation.STORE_ORDER_STATUS);

    private final StoreService service;

    public StoreRequestHandler(StoreService service) {
        this.service = service;
    }

    public boolean supports(Operation operation) {
        return OPERATIONS.contains(operation);
    }

    public ResponseMessage<? extends Serializable> handle(
            RequestMessage<?> request, UserAccount actor) throws SQLException {
        try {
            switch (request.getOperation()) {
                case STORE_PRODUCT_QUERY:
                    return success(request, service.queryProducts(actor,
                            bodyOrNull(request, StoreQueryRequest.class)));
                case STORE_PRODUCT_SAVE:
                    service.saveProduct(actor, body(request, ProductDto.class));
                    return success(request, body(request, ProductDto.class));
                case STORE_PRODUCT_DELETE:
                    service.deleteProduct(actor, idBody(request));
                    return success(request, "OK");
                case STORE_CART_QUERY:
                    return success(request, service.queryCart(actor));
                case STORE_CART_UPDATE:
                    service.updateCart(actor, body(request, CartUpdateRequest.class));
                    return success(request, "OK");
                case STORE_ORDER_CREATE:
                    OrderDto order = service.createOrder(actor,
                            body(request, OrderCreateRequest.class));
                    return success(request, order);
                case STORE_ORDER_QUERY:
                    return success(request, service.queryOrders(actor));
                case STORE_ORDER_STATUS:
                    OrderStatusRequest status = body(request, OrderStatusRequest.class);
                    service.updateOrderStatus(actor, status.getOrderId(),
                            status.getStatusName());
                    return success(request, "OK");
                default:
                    return ResponseMessage.failure(request.getRequestId(),
                            ResponseCode.NOT_IMPLEMENTED, "不支持的商店操作");
            }
        } catch (BusinessException e) {
            return ResponseMessage.failure(request.getRequestId(),
                    e.getResponseCode(), e.getMessage());
        }
    }

    private String idBody(RequestMessage<?> request) throws BusinessException {
        return body(request, EntityIdRequest.class).getEntityId();
    }

    private <T> T bodyOrNull(RequestMessage<?> request, Class<T> type) throws BusinessException {
        return request.getBody() == null ? null : body(request, type);
    }

    private <T> T body(RequestMessage<?> request, Class<T> type) throws BusinessException {
        if (!type.isInstance(request.getBody())) {
            throw new BusinessException(ResponseCode.INVALID_REQUEST, "请求参数格式错误");
        }
        return type.cast(request.getBody());
    }

    private ResponseMessage<? extends Serializable> success(
            RequestMessage<?> request, Serializable body) {
        return ResponseMessage.success(request.getRequestId(), "操作成功", body);
    }
}
