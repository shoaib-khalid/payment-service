package com.kalsym.paymentservice.provider.SenangPay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.provider.Ezlink.HttpResult;
import com.kalsym.paymentservice.provider.Ezlink.HttpsGetConn;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.QueryPaymentResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.LogUtil;
import org.apache.commons.codec.binary.Hex;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class QueryPayment extends SyncDispatcher {

    private final String queryOrder_url;
    private final int connectTimeout;
    private final int waitTimeout;
    private final String logprefix;

    private final String merchantId;
    private final String generatelink_KalsymKey;
    private final String location = "SenangPayQueryStatus";
    private final String systemTransactionId;
    private final PaymentOrder paymentOrder;
    private final String host;

    public QueryPayment(CountDownLatch latch, HashMap config, PaymentOrder paymentOrder, String systemTransactionId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "SenangPay QueryOrder class initiliazed!!", "");
        this.queryOrder_url = (String) config.get("queryOrder_url");
        this.connectTimeout = Integer.parseInt((String) config.get("queryOrder_connect_timeout"));
        this.waitTimeout = Integer.parseInt((String) config.get("queryOrder_wait_timeout"));
        this.merchantId = (String) config.get("senangPay_MerchantId");
        this.generatelink_KalsymKey = (String) config.get("senangPay_KalsymKey");
        this.host = (String) config.get("Host");

        this.paymentOrder = paymentOrder;
    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();
        HashMap httpHeader = new HashMap();

        String hashValue = hash(merchantId + generatelink_KalsymKey + paymentOrder.getClientTransactionId(), generatelink_KalsymKey);
        String url = this.queryOrder_url + "?merchant_id=" + this.merchantId + "&order_id=" + paymentOrder.getClientTransactionId() + "&hash=" + hashValue;

        httpHeader.put("Content-Type", "text/html; charset=UTF-8");
        httpHeader.put("Host", host);
        httpHeader.put("User-Agent", "PostmanRuntime/7.29.2");

        HttpResult httpResult = HttpsGetConn.SendHttpsRequest("GET", this.systemTransactionId, url, httpHeader, this.connectTimeout, this.waitTimeout);

        if (httpResult.resultCode == 0) {
            response = extractResponseBody(httpResult.responseString);
        } else {
            LogUtil.info(logprefix, location, "Request failed", "");
            response.resultCode = -1;
        }
        LogUtil.info(logprefix, location, "Process finish", "");
        return response;
    }

    private ProcessResult extractResponseBody(String respString) {
        ProcessResult result = new ProcessResult();
        QueryPaymentResult queryOrderResult = new QueryPaymentResult();
        try {
            JsonObject jsonResp = new Gson().fromJson(respString, JsonObject.class);
            LogUtil.info(logprefix, location, "Response : ", jsonResp.get("data").getAsJsonArray().toString());

            int isSuccess = jsonResp.get("status").getAsInt();
            String spOrderId = "";
            String paymentMode = "";
            String status = "";
            String description = "";
            String created = "";
            if (isSuccess == 1) {
                JsonArray orderList = jsonResp.get("data").getAsJsonArray();
                if (orderList.size() != 0) {
                    for (int i = 0; i < orderList.size(); i++) {
                        if (orderList.get(i).equals("payment_info")) {
                            JsonObject orderObject = orderList.get(i).getAsJsonObject();

                            LogUtil.info(logprefix, location, "isSuccess:" + isSuccess, "");
                            queryOrderResult.isSuccess = true;
                            //extract order cancelled
                            spOrderId = orderObject.get("transaction_reference").getAsString();
                            ;
                            paymentMode = orderObject.get("payment_mode").getAsString();
                            status = orderObject.get("status").getAsString();
                            description = orderObject.get("status_description").getAsString();
                            created = orderObject.get("transaction_date").getAsString();
                        }
                    }
                    PaymentOrder orderFound = new PaymentOrder();
                    orderFound.setSpOrderId(spOrderId);
                    orderFound.setStatus(status);
                    orderFound.setPaymentChannel(paymentMode);
                    orderFound.setStatusDescription(description);
                    orderFound.setCreatedDate(created);
                    queryOrderResult.orderFound = orderFound;
                    queryOrderResult.isSuccess = true;
                    result.isSuccess = true;
                    result.resultCode = 0;
                    result.returnObject = queryOrderResult;
                }
                else{
                    PaymentOrder orderFound = new PaymentOrder();
                    orderFound.setSpOrderId(spOrderId);
                    orderFound.setStatus("PENDING");
                    queryOrderResult.orderFound = orderFound;
                    queryOrderResult.isSuccess = true;
                    result.isSuccess = true;
                    result.resultCode = 0;
                    result.returnObject = queryOrderResult;
                }

            } else {

                result.isSuccess = false;
                result.resultCode = -1;
                result.returnObject = jsonResp.get("msg").getAsString();
                LogUtil.info(logprefix, location, "Response Failed : ", jsonResp.get("msg").getAsString());
            }
        } catch (Exception ex) {
            result.isSuccess = false;
            result.resultCode = -2;
            result.returnObject = ex.getMessage();
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return result;
    }

    public String hash(String req, String key) {
        byte[] hmacSha256 = null;
        System.out.println("hash " + req);

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            hmacSha256 = sha256_HMAC.doFinal(req.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hmac-sha256", e);
        }
        return Hex.encodeHexString(hmacSha256);
    }

}
