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
import org.apache.commons.codec.binary.Hex;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class GoPayFastPaymentLink extends SyncDispatcher {

    private final String generatelink_url;
    private final String tokenUrl;
    private final String merchantId;
    private final String securedKey;
    private final int providerId;
    private final String userAgent;
    private final String systemTransactionId;
    private PaymentRequest order;
    private String logprefix;
    private String location = "GoPayFastPaymentLink";
    private String host;
    private String paymentRedirectUrl;
    private String key;

    private static final DecimalFormat df = new DecimalFormat("0.00");


    public GoPayFastPaymentLink(CountDownLatch latch, HashMap config, PaymentRequest order, String systemTransactionId,
                                Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "GoPayFast GeneratePaymentLink class initiliazed!!", "");
        this.generatelink_url = (String) config.get("paymentLink_url");
        this.tokenUrl = (String) config.get("tokenUrl");
        this.merchantId = (String) config.get("merchantId");
        this.securedKey = (String) config.get("securedKey");
        this.userAgent = (String) config.get("userAgent");
        this.host = (String) config.get("host");
        this.order = order;
        this.providerId = providerId;
        this.paymentRedirectUrl = (String) config.get("paymentRedirectUrl");
        this.key = (String) config.get("key");

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
        //merchantid , tranasactionid, amount, datetime
        String date = new Date().toString();
        String req = merchantId + order.getTransactionId() + order.getPaymentAmount() + date;
        String hash = hash(req, key);

        response.returnObject = extractResponseBody(this.generatelink_url, hash, token, date);

        return response;
    }

    private MakePaymentResult extractResponseBody(String respString, String hashValue, String token,String date) {
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
            submitOrderResult.redirectUrl = this.paymentRedirectUrl;
            submitOrderResult.hashDate = date;
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }

    private String getToken() {

        df.setRoundingMode(RoundingMode.DOWN);

        String token = "";//https://apipxyuat.apps.net.pk:8443/api/token
        String requestUrl = "https://ipguat.apps.net.pk/Ecommerce/api/Transaction/GetAccessToken";

        MultiValueMap<String, Object> postParameters = new LinkedMultiValueMap<>();
        postParameters.add("MERCHANT_ID", merchantId);
        postParameters.add("BASKET_ID", order.getSystemTransactionId());
        postParameters.add("TXNAMT", df.format(order.getPaymentAmount().doubleValue()));
        postParameters.add("SECURED_KEY", securedKey);
        LogUtil.info(logprefix, location, "Reqeuest Body : ", postParameters.toString());


        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        headers.add("Host", host);
        headers.add("User-Agent", userAgent);
        headers.add("Cache-Control", "no-cache");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(postParameters, headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> responses = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, String.class);

            int statusCode = responses.getStatusCode().value();
            LogUtil.info(logprefix, location, "Responses", responses.getBody());
            if (statusCode == 200) {
                LogUtil.info(logprefix, location, "Get Token: " + responses.getBody(), "");

                JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                token = jsonResp.get("ACCESS_TOKEN").getAsString();

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
