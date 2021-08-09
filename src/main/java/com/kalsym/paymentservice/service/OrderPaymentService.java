package com.kalsym.paymentservice.service;

import com.kalsym.paymentservice.service.Response.*;
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

import java.time.Instant;

@Service
public class OrderPaymentService {


    private static Logger logger = LoggerFactory.getLogger("application");

    @Value("${orderUrl}")
    String orderUrl;

    @Value("${storeUrl}")
    String storeUrl;

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


    public StoreDetails getStoreDeliveryDetails(String storeId) {

        String url = storeUrl + "stores/" + storeId;
        System.err.println("Store " + url);
        try {
            RestTemplate restTemplate = new RestTemplate();
            logger.info("StoreGetDetailsURL : " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", orderServiceToken);

            HttpEntity httpEntity = new HttpEntity(headers);

            logger.debug("Sending request to store-service: {} to get store group name (liveChatCsrGroupName) against storeId: {} , httpEntity: {}", url, storeId, httpEntity);
            ResponseEntity res = restTemplate.exchange(url, HttpMethod.GET, httpEntity, StoreDetailsData.class);

            if (res != null) {
                StoreDetailsData storeResponse = (StoreDetailsData) res.getBody();
                logger.debug("Store orders group (liveChatOrdersGroupName) received: {}, against storeId: {}", storeResponse.getData(), storeId);
                return storeResponse.getData();
            } else {
                logger.warn("Cannot get storename against storeId: {}", storeId);
            }

            logger.debug("Request sent to live service, responseCode: {}, responseBody: {}", res.getStatusCode(), res.getBody());
        } catch (RestClientException e) {
            logger.error("Error getting storeName against storeId:{}, url: {}", storeId, storeUrl, e);
            return null;
        }
        return null;
    }


}

