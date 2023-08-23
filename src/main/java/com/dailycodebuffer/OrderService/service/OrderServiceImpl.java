package com.dailycodebuffer.OrderService.service;

import com.dailycodebuffer.OrderService.entity.Order;
import com.dailycodebuffer.OrderService.exception.CustomException;
import com.dailycodebuffer.OrderService.external.client.PaymentService;
import com.dailycodebuffer.OrderService.external.client.ProductService;
import com.dailycodebuffer.OrderService.external.request.PaymentRequest;
import com.dailycodebuffer.OrderService.external.response.PaymentResponse;
import com.dailycodebuffer.OrderService.model.OrderRequest;
import com.dailycodebuffer.OrderService.model.OrderResponse;
import com.dailycodebuffer.OrderService.repository.OrderRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
@Log4j2
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public long placeOrder(OrderRequest orderRequest) {
        //Order Entity -> Save the data with Status order Created
        //ProductService -> Block Products(Reduce the quantity)
        //PaymentService -> Payments -> Success -> COMPLETE, ELSE CANCELLED

        log.info("Placing Order Request: {}",orderRequest);

        productService.reduceQuantity(orderRequest.getProductId(), orderRequest.getQuantity());

        log.info("Creating order with status CREATED");
        Order order = Order.builder()
                .amount(orderRequest.getTotalAmount())
                .orderStatus("CREATED")
                .productId(orderRequest.getProductId())
                .orderDate(Instant.now())
                .quantity(orderRequest.getQuantity())
                .build();
        order = orderRepository.save(order);

        String orderStatus = null;
        try {
            PaymentRequest paymentRequest = PaymentRequest.builder()
                    .orderId(order.getId())
                    .paymentMode(orderRequest.getPaymentMode())
                    .amount(orderRequest.getTotalAmount())
                    .build();
            paymentService.doPayment(paymentRequest);
            log.info("Payment Done Successfully. Changing the order status to Placed");
            orderStatus = "PLACED";
        }
        catch (Exception e){
            log.error("Error occurred in payment. Changing order status to failed");
            orderStatus = "PAYMENT_FAILED";
        }
        order.setOrderStatus(orderStatus);
        orderRepository.save(order);

        log.info("Order Places successfully with order id: {}",order.getId());
        return order.getId();
    }

    @Override
    public OrderResponse getOrderDetails(long orderId) {
        Order order =
                orderRepository.findById(orderId).orElseThrow(()->new CustomException("Order not found for the order Id: "+orderId,"NOT_FOUND",404));

        log.info("Invoking Product service to fetch the product by order id {}",orderId);

        OrderResponse.ProductDetails productResponse =
                restTemplate.getForObject("http://PRODUCT-SERVICE/product/"+order.getProductId()
                        ,OrderResponse.ProductDetails.class);

        log.info("Invoking Payment service to fetch the transaction details by order id {}",orderId);

        OrderResponse.PaymentDetails paymentResponse =
                restTemplate.getForObject("http://PAYMENT-SERVICE/payment/order/"+order.getId()
                        ,OrderResponse.PaymentDetails.class);

        OrderResponse orderResponse = OrderResponse.builder()
                .orderId(orderId)
                .orderStatus(order.getOrderStatus())
                .amount(order.getAmount())
                .orderDate(order.getOrderDate())
                .productDetails(productResponse)
                .paymentDetails(paymentResponse)
                .build();
        return orderResponse;
    }
}
