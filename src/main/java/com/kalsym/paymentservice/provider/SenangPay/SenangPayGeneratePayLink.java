package com.kalsym.paymentservice.provider.SenangPay;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class SenangPayGeneratePayLink extends SyncDispatcher {

    private final String generatelink_url;
    private final String generatelink_KalsymKey;
    private final int connectTimeout;
    private final int providerId;
    private final int waitTimeout;
    private final String systemTransactionId;
    private final String merchantId;
    private PaymentRequest order;
    private HashMap productMap;
    private String atxProductCode = "";
    private String sslVersion = "SSL";
    private String logprefix;
    private String location = "SenangPayGeneratePaymentLink";
    private String host;

    public SenangPayGeneratePayLink(CountDownLatch latch, HashMap config, PaymentRequest order, String systemTransactionId, Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "SenangPay GeneratePaymentLink class initiliazed!!", "");
        this.generatelink_url = (String) config.get("senangPay_url");
        this.generatelink_KalsymKey = (String) config.get("senangPay_KalsymKey");
        this.connectTimeout = Integer.parseInt((String) config.get("senangPay_connect_timeout"));
        this.waitTimeout = Integer.parseInt((String) config.get("senangPay_wait_timeout"));
        this.merchantId = (String) config.get("senangPay_MerchantId");
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
        String parameters = generatelink_KalsymKey + order.getStoreName() + String.format("%.2f", order.getPaymentAmount()) + order.getTransactionId();
//        String hashV = "?email=" + order.getEmail().replaceAll("@", "%40") + "&amountpaid=" + order.getPaymentAmount() + "&txn_status=" + 1 + "&tx_msg=" + msg.replaceAll(" ", "+") + "&order_id=" + order.getTransactionId() + "hashed_value=[HASH]";
        String reqUrl = this.generatelink_url + this.merchantId;
        String hashValue = hash(parameters, generatelink_KalsymKey);
        LogUtil.info(logprefix, location, "Order Id : ", order.getTransactionId());
        LogUtil.info(logprefix, location, "Hash value", hashValue);

        System.out.println("String url: " + reqUrl);

//        String url = this.generatelink_url + this.merchantId;
//        System.out.println("String url: " + url);
        HttpResult httpResult = HttpConnection.SendHttpsRequest("POST", this.systemTransactionId, reqUrl, httpHeader, null, this.connectTimeout, this.waitTimeout);
        if (httpResult.resultCode == 0) {
            LogUtil.info(logprefix, location, "Request successful", "");
            response.resultCode = 0;
            response.returnObject = extractResponseBody(httpResult.httpResponseCode, this.generatelink_url + this.merchantId, hashValue);
        } else {
            LogUtil.info(logprefix, location, "Request failed", "");
            response.resultCode = -1;
        }
        LogUtil.info(logprefix, location, "Process finish", "");
        return response;
    }

    private MakePaymentResult extractResponseBody(Integer responseCode, String respString, String hashValue) {
        MakePaymentResult submitOrderResult = new MakePaymentResult();
        try {
            System.out.println("Response : " + respString);
            if (responseCode.equals(200)) {
                PaymentOrder orderCreated = new PaymentOrder();
                orderCreated.setSpErrorCode(responseCode.toString());
                orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                submitOrderResult.orderCreated = orderCreated;
                submitOrderResult.isSuccess = true;
                submitOrderResult.providerId = this.providerId;
                submitOrderResult.paymentLink = respString;
                submitOrderResult.hash = hashValue;
            } else {
                submitOrderResult.isSuccess = false;
            }
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }


    public String hash(String req, String key) {
        byte[] hmacSha256 = null;
        System.out.println("hash " + req);

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            hmacSha256 = sha256_HMAC.doFinal(req.getBytes("UTF-8"));


        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hmac-sha256", e);
        }
        return Hex.encodeHexString(hmacSha256);
    }
}

