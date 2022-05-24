package com.kalsym.paymentservice.provider.GoPayFast;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SenangPay.HttpConnection;
import com.kalsym.paymentservice.provider.SenangPay.HttpResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;

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


    public GoPayFastPaymentLink(CountDownLatch latch, HashMap config, PaymentRequest order, String systemTransactionId, Integer providerId) {
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
//        String parameters = generatelink_KalsymKey + systemTransactionId + String.format("%.2f", order.getPaymentAmount()) + order.getTransactionId();
//        String hashV = "?email=" + order.getEmail().replaceAll("@", "%40") + "&amountpaid=" + order.getPaymentAmount() + "&txn_status=" + 1 + "&tx_msg=" + msg.replaceAll(" ", "+") + "&order_id=" + order.getTransactionId() + "hashed_value=[HASH]";
//        LogUtil.info(logprefix, location, "parameters: ", parameters);
//
//        //parameter = key+storeName+totalAmount+sysmtransactionID
//        //hash(key,parameter) //HmacSHA256
//
//        String reqUrl = this.generatelink_url + this.merchantId;
//        String token = getToken();
//        LogUtil.info(logprefix, location, "Order Id : ", order.getTransactionId());
//        LogUtil.info(logprefix, location, "Hash value", token);
//
//        LogUtil.info(logprefix, location, "String url: ", reqUrl);
////        response.returnObject = extractResponseBody(this.generatelink_url + this.merchantId, hashValue);
//        String url = this.generatelink_url + this.merchantId;
//        System.out.println("String url: " + url);
////        HttpResult httpResult = HttpConnection.SendHttpsRequest("POST", this.systemTransactionId, reqUrl, httpHeader, null, this.connectTimeout, this.waitTimeout);
//        if (httpResult.resultCode == 0) {
//            LogUtil.info(logprefix, location, "Request successful", "");
//            response.resultCode = 0;
//            response.returnObject = extractResponseBody(httpResult.httpResponseCode, this.generatelink_url + this.merchantId, hashValue);
//        } else {
//            LogUtil.info(logprefix, location, "Request failed", "");
//            response.resultCode = -1;
//        }
//        LogUtil.info(logprefix, location, "Process finish", "");

        String reqUrl = this.generatelink_url ;
//        String hashValue = hash(parameters, generatelink_KalsymKey);
        LogUtil.info(logprefix, location, "Order Id : ", order.getTransactionId());

        LogUtil.info(logprefix, location, "String url: ", reqUrl);

        response.returnObject = extractResponseBody(this.generatelink_url, "");

        return response;
    }

    private MakePaymentResult extractResponseBody(String respString, String hashValue) {
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
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }

    private String getToken() {

//        HashMap httpHeader = new HashMap();
//        httpHeader.put("Host", host);
//        httpHeader.put("Content-type", "application/x-www-form-urlencoded");
//
//        HttpResult httpResult = HttpConnection.SendHttpsRequest("POST", this.systemTransactionId, tokenUrl, httpHeader, null, this.connectTimeout, this.waitTimeout);
//        if (httpResult.resultCode == 0) {
//            LogUtil.info(logprefix, location, "Request successful", "");
//            response.resultCode = 0;
//            response.returnObject = extractResponseBody(httpResult.httpResponseCode, this.generatelink_url + this.merchantId, hashValue);
//        } else {
//            LogUtil.info(logprefix, location, "Request failed", "");
//            response.resultCode = -1;
//        }
        return "";
    }

}
