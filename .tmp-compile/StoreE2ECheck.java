import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.Serializable;
import java.util.List;

/** End-to-end store flow over a real Socket connection. */
public final class StoreE2ECheck {
    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("[PASS] " + message);
    }

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = Integer.parseInt(args[0]);
        String studentPassword = args[1];
        String adminPassword = args[2];

        ClientConnection connection = ClientConnection.connect(host, port);
        try {
            // login as student
            ResponseMessage<?> login = connection.request(new RequestMessage<LoginRequest>(
                    Operation.USER_LOGIN, null, new LoginRequest("student", studentPassword)));
            expect(login.isSuccess(), "学生登录成功");
            LoginResponse session = (LoginResponse) login.getBody();
            StoreClientService store = new StoreClientService(connection, session.getSessionToken());

            // browse products
            List<ProductDto> products = store.queryProducts(null);
            expect(!products.isEmpty(), "学生可浏览商品，共 " + products.size() + " 件");
            for (ProductDto p : products) {
                expect(p.isActive(), "学生只能看到上架商品：" + p.getProductId());
            }

            // add to cart
            store.updateCart("P001", 2);
            store.updateCart("P002", 1);
            List<CartItemDto> cart = store.queryCart();
            expect(cart.size() == 2, "购物车中有 2 种商品");

            // create order
            OrderDto order = store.createOrder();
            expect("待付款".equals(order.getStatusName()), "订单创建成功 " + order.getOrderId()
                    + " 总额 " + order.getTotalAmount() + " 明细 " + order.getItems().size() + " 行");
            expect(store.queryCart().isEmpty(), "下单后购物车已清空");
            expect(store.queryOrders().size() == 1, "学生可查询到自己的订单");

            // admin login
            ResponseMessage<?> adminLogin = connection.request(new RequestMessage<LoginRequest>(
                    Operation.USER_LOGIN, null, new LoginRequest("admin", adminPassword)));
            expect(adminLogin.isSuccess(), "管理员登录成功");
            LoginResponse adminSession = (LoginResponse) adminLogin.getBody();
            StoreClientService adminStore = new StoreClientService(connection, adminSession.getSessionToken());

            List<ProductDto> allProducts = adminStore.queryProducts(null);
            boolean sawInactive = false;
            for (ProductDto p : allProducts) {
                if (!p.isActive()) {
                    sawInactive = true;
                }
            }
            expect(sawInactive, "管理员能看到已下架商品");
            expect(adminStore.queryOrders().size() >= 1, "管理员能看到全部订单");

            // admin updates order status
            adminStore.updateOrderStatus(order.getOrderId(), "已发货");
            List<OrderDto> orders = adminStore.queryOrders();
            for (OrderDto o : orders) {
                if (o.getOrderId().equals(order.getOrderId())) {
                    expect("已发货".equals(o.getStatusName()), "管理员已将订单状态改为已发货");
                }
            }

            System.out.println("E2E STORE FLOW OK");
        } finally {
            connection.close();
        }
    }
}
