package com.kalsym.paymentservice.service;

import com.kalsym.paymentservice.service.Response.OrderConfirm;
import com.kalsym.paymentservice.service.Response.OrderConfirmData;
import com.kalsym.paymentservice.service.Response.OrderUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@Service
public class OrderPaymentService {


    private static Logger logger = LoggerFactory.getLogger("application");

    //@Autowired
    @Value("${order-service.URL:https://api.symplified.biz/order-service/v1/orders/}")
    String orderUrl;


    @Value("${product-service.token:Bearer accessToken}")
    private String orderServiceToken;

    public OrderConfirm updateStatus(String orderId, String paymentStatus, String modifyBy, String message) {
        String url = orderUrl + orderId + "/completion-status-updates";
        try {
            RestTemplate restTemplate = new RestTemplate();
            Instant instant = Instant.now();
            System.out.println(instant.toString());

//            String formattedDate = myDateObj.format(myFormatObj);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", orderServiceToken);
            OrderUpdate orders = new OrderUpdate();
            orders.setComments(message);
            orders.setCreated(instant.toString());
            orders.setModifiedBy(modifyBy);
            orders.setOrderId(orderId);
            orders.setStatus(paymentStatus);
            System.out.println("payment" + orders.toString());

            HttpEntity<OrderUpdate> httpEntity;
            httpEntity = new HttpEntity(orders, headers);
            logger.info("orderDeliveryConfirmationURL : " + url);
            ResponseEntity<OrderConfirmData> res = restTemplate.exchange(url, HttpMethod.PUT, httpEntity, OrderConfirmData.class);
            logger.info("res : " + res);

            logger.debug("Sending request to product-service: {} to get store group name (liveChatCsrGroupName) against storeId: {} , httpEntity: {}", url, orderId, httpEntity);
//            ResponseEntity res = restTemplate.exchange(url, HttpMethod.PUT, httpEntity, OrderConfirmData.class);
//
            if (res != null) {
                OrderConfirmData orderConfirm = (OrderConfirmData) res.getBody();
                logger.debug("Store orders group (liveChatOrdersGroupName) received: {}, against storeId: {}", orderConfirm.getData().getId(), orderId);
                return orderConfirm.getData();
            } else {
                logger.warn("Cannot get storename against storeId: {}", orderId);
            }

            logger.debug("Request sent to live service, responseCode: {}, responseBody: {}", res.getStatusCode(), res.getBody());
        } catch (RestClientException e) {
            logger.error("Error getting storeName against storeId:{}, url: {}", orderId, orderId, e);
            return null;
        }
        return null;
    }
}

