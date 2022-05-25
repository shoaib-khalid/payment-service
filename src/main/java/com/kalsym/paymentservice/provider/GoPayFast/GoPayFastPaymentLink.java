package com.kalsym.paymentservice.provider.GoPayFast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;

import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;

import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpMethod;

import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class GoPayFastPaymentLink extends SyncDispatcher {

    private final String generatelink_url;
    private final String tokenUrl;
    private final String merchantId;
    private final String securedKey;
    private final String grantType;
    private final int connectTimeout;
    private final int providerId;
    private final int waitTimeout;
    private final String systemTransactionId;
    private PaymentRequest order;
    private HashMap productMap;
    private String atxProductCode = "";
    private String sslVersion = "SSL";
    private String logprefix;
    private String location = "GoPayFastPaymentLink";
    private String host;

    public GoPayFastPaymentLink(CountDownLatch latch, HashMap config, PaymentRequest order, String systemTransactionId,
            Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "GoPayFast GeneratePaymentLink class initiliazed!!", "");
        this.generatelink_url = (String) config.get("paymentLink_url");
        this.tokenUrl = (String) config.get("tokenUrl");
        this.connectTimeout = Integer.parseInt((String) config.get("connect_timeout"));
        this.waitTimeout = Integer.parseInt((String) config.get("wait_timeout"));
        this.merchantId = (String) config.get("merchantId");
        this.securedKey = (String) config.get("securedKey");
        this.grantType = (String) config.get("grantType");
        productMap = (HashMap) config.get("productCodeMapping");
        this.host = (String) config.get("host");
        this.order = order;
        this.sslVersion = (String) config.get("ssl_version");
        this.providerId = providerId;

    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();
        HashMap httpHeader = new HashMap();
        httpHeader.put("Host", host);
        String msg = "Payment was successful";

        String token = getToken();

        String reqUrl = this.generatelink_url;
        // String hashValue = hash(parameters, generatelink_KalsymKey);
        LogUtil.info(logprefix, location, "Order Id : ", order.getTransactionId());

        LogUtil.info(logprefix, location, "String url: ", reqUrl);

        response.returnObject = extractResponseBody(this.generatelink_url, "",token);

        return response;
    }

    private MakePaymentResult extractResponseBody(String respString, String hashValue, String token) {
        MakePaymentResult submitOrderResult = new MakePaymentResult();
        try {
            System.out.println("Response : " + respString);
            PaymentOrder orderCreated = new PaymentOrder();
            orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
            submitOrderResult.orderCreated = orderCreated;
            submitOrderResult.isSuccess = true;
            submitOrderResult.providerId = this.providerId;
            submitOrderResult.paymentLink = respString;
            submitOrderResult.hash = hashValue;
            submitOrderResult.sysTransactionId = systemTransactionId;
            submitOrderResult.token = token;
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }

    private String getToken() {
        String token = "";

        MultiValueMap<String, Object> postParameters = new LinkedMultiValueMap<>();
        postParameters.add("merchant_id", merchantId);
        postParameters.add("grant_type", grantType);
        postParameters.add("secured_key", securedKey);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-www-form-urlencoded");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(postParameters, headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> responses = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, String.class);

            int statusCode = responses.getStatusCode().value();
            LogUtil.info(logprefix, location, "Responses", responses.getBody());
            if (statusCode == 200) {
                LogUtil.info(logprefix, location, "Get Token: " + responses.getBody(), "");

                JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                token = jsonResp.get("token").getAsString();

            } else {
                LogUtil.info(logprefix, location, "Request failed", responses.getBody());
                token = "";
            }
        } catch (Exception exception) {
            LogUtil.info(logprefix, location, "Exception : ", exception.getMessage());
            token = "";

        }
        return token;
    }

}
