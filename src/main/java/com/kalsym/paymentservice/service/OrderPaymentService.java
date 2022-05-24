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

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", orderServiceToken);
            OrderUpdate orders = new OrderUpdate();
            orders.setComments(message);
            orders.setCreated(instant.toString());
            orders.setModifiedBy(modifyBy);
            orders.setOrderId(orderId);
            orders.setStatus(paymentStatus);
            logger.info("payment : " + orders);

            HttpEntity<OrderUpdate> httpEntity;
            httpEntity = new HttpEntity(orders, headers);
            logger.info("orderDeliveryConfirmationURL : " + url);
            ResponseEntity<OrderConfirmData> res = restTemplate.exchange(url, HttpMethod.PUT, httpEntity, OrderConfirmData.class);
            logger.info("res : " + res);

            logger.debug("Sending request to product-service: {} to get Order (liveChatCsrGroupName) against storeId: {} , httpEntity: {}", url, orderId, httpEntity);
//            ResponseEntity res = restTemplate.exchange(url, HttpMethod.PUT, httpEntity, OrderConfirmData.class);
//
            if (res != null) {
                OrderConfirmData orderConfirm = (OrderConfirmData) res.getBody();
                logger.debug("Store orders group (liveChatOrdersGroupName) received: {}, against storeId: {}", orderConfirm.getData().getId(), orderId);
                return orderConfirm.getData();
            } else {
                logger.warn("Cannot get Order against orderId: {}", orderId);
            }

            logger.debug("Request sent to live service, responseCode: {}, responseBody: {}", res.getStatusCode(), res.getBody());
        } catch (RestClientException e) {
            logger.error("Error getting Order id:{}, url: {}", orderId, url, e);
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

    public OrderConfirm getOrderById(String orderId) {

        String url = orderUrl + orderId;
        System.err.println("Order url " + url);
        try {
            RestTemplate restTemplate = new RestTemplate();
            logger.info("OrderGetDetailsURL : " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", orderServiceToken);

            HttpEntity httpEntity = new HttpEntity(headers);

            logger.debug("Sending request to order-service: {} to get order group name (liveChatCsrGroupName) against orderId: {} , httpEntity: {}", url, orderId, httpEntity);
            ResponseEntity res = restTemplate.exchange(url, HttpMethod.GET, httpEntity, OrderConfirmData.class);

            if (res != null) {
                OrderConfirmData orderConfirmData = (OrderConfirmData) res.getBody();
                logger.debug("Orders group (liveChatOrdersGroupName) received: {}, against orderId: {}", orderConfirmData.getData(), orderId);
                return orderConfirmData.getData();
            } else {
                logger.warn("Cannot fine the order for id: {}", orderId);
            }

            logger.debug("Request sent to live service, responseCode: {}, responseBody: {}", res.getStatusCode(), res.getBody());
        } catch (RestClientException e) {
            logger.error("Error getting Order against orderId:{}, url: {}", orderId, orderUrl, e);
            return null;
        }
        return null;
    }

    public StoreDetails getStore(String storeId) {
        String url = storeUrl + "stores/" + storeId;
        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", orderServiceToken);

            HttpEntity httpEntity = new HttpEntity(headers);

            logger.debug("Sending request to product-service: {} to get store group name (liveChatCsrGroupName) against storeId: {} , httpEntity: {}", url, storeId, httpEntity);
            ResponseEntity<StoreDetailsData> res = restTemplate.exchange(url, HttpMethod.GET, httpEntity, StoreDetailsData.class);

            if (res != null) {
                StoreDetailsData storeResponse = (StoreDetailsData) res.getBody();
                assert storeResponse != null;
                String storeName = storeResponse.getData().getLiveChatOrdersGroupName();
                logger.debug("Store orders group (liveChatOrdersGroupName) received: {}, against storeId: {}", storeName, storeId);
                System.out.println("Get Store Detail : " + storeResponse.getData());
                return storeResponse.getData();
            } else {
                logger.warn("Cannot get storename against storeId: {}", storeId);
            }

            logger.debug("Request sent to live service, responseCode: {}, responseBody: {}", res.getStatusCode(), res.getBody());
        } catch (RestClientException e) {
            logger.error("Error getting storeName against storeId:{}, url: {}", storeId, url, e);
            return null;
        }
        return null;
    }



}

