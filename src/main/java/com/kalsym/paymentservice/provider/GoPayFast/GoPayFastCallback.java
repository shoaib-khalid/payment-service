package com.kalsym.paymentservice.provider.GoPayFast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SpCallbackResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class GoPayFastCallback extends SyncDispatcher {

    private final HashMap productMap;
    private final String logprefix;
    private final String location = "GoPayFastCallback";
    private final String systemTransactionId;
    private String spOrderId;
    private JsonObject jsonBody;

    private final String merchantId;
    private final String securedKey;

    public GoPayFastCallback(CountDownLatch latch, HashMap config, Object jsonBody, String systemTransactionId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "GoPayFast OrderCallback class initiliazed!!", "");
        productMap = (HashMap) config.get("productCodeMapping");

        this.merchantId = (String) config.get("merchantId");
        this.securedKey = (String) config.get("securedKey");
        String jsonString = jsonBody.toString();
        LogUtil.info(logprefix, location, "Request Body:" + jsonString, "");
        try {
            this.jsonBody = new Gson().fromJson(jsonString, JsonObject.class);
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error read json body ", "", ex);
            this.jsonBody = null;
        }
    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();
        SpCallbackResult result = new SpCallbackResult();


        LogUtil.info(logprefix, location, "Response Body", this.jsonBody.toString());
        String errCode = this.jsonBody.get("err_code").getAsString();
        String basketID = this.jsonBody.get("basket_id").getAsString();
        if (errCode.equals("000")) {
            String validateHash = this.jsonBody.get("validation_hash").getAsString();
            String beforeHash = basketID + "|" + this.securedKey + "|" + this.merchantId + "|" + errCode;
            String hash = "";
            try {

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(
                        beforeHash.getBytes(StandardCharsets.UTF_8));
                hash = bytesToHex(encodedhash);
                LogUtil.info(logprefix, location, "System Generate Hash", hash);
            } catch (Exception ex) {
                System.err.println("LOCAL Exception : " + ex.getMessage());

            }
            System.err.println("ORDER ID : " + this.jsonBody.get("transaction_id").getAsString() );


            if (hash.equals(validateHash)) {

                result.paymentTransactionId = basketID;
                result.providerId = 3;
                result.spErrorCode = "0";
                result.status = "Payment_was_successful";
                result.orderId = this.jsonBody.get("order_id").getAsString();
                result.statusId = this.jsonBody.get("status_id").getAsInt();
                result.paymentChanel = this.jsonBody.get("payment_channel").getAsString();

                response.returnObject = result;
                response.resultCode = 0;
                response.isSuccess = true;
                response.resultString = "Payment_was_successful";


            } else {
                result.paymentTransactionId = basketID;
                result.providerId = 3;
                result.spErrorCode = "-1";
                result.status = "Payment_was_failed";
                result.orderId = this.jsonBody.get("order_id").getAsString();
                result.statusId = this.jsonBody.get("status_id").getAsInt();
                result.paymentChanel = this.jsonBody.get("payment_channel").getAsString();

                response.returnObject = result;
                response.resultCode = 0;
                response.isSuccess = false;
                response.resultString = "Payment_was_failed";

            }
        }
        else{
            result.paymentTransactionId = basketID;
            result.providerId = 3;
            result.spErrorCode = "-1";
            result.status = "Payment_was_failed";
            result.orderId = this.jsonBody.get("transaction_id").getAsString();
            result.statusId = this.jsonBody.get("status_id").getAsInt();
            result.paymentChanel = this.jsonBody.get("payment_channel").getAsString();

            response.returnObject = result;
            response.resultCode = 0;
            response.isSuccess = false;
            response.resultString = "Payment_was_failed";
        }


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
            submitOrderResult.providerId = 3;
            submitOrderResult.paymentLink = respString;
            submitOrderResult.hash = hashValue;
            submitOrderResult.sysTransactionId = systemTransactionId;
            submitOrderResult.token = "";
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }

//    public String hash(String req, String key) {
//        byte[] hmacSha256 = null;
//        System.out.println("hash " + req);
//
//        try {
//            Mac sha256_HMAC = Mac.getInstance("SHA256");
//            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "SHA256");
//            sha256_HMAC.init(secret_key);
//            hmacSha256 = sha256_HMAC.doFinal(req.getBytes("UTF-8"));
//
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to calculate hmac-sha256", e);
//        }
//        return Hex.encodeHexString(hmacSha256);
//    }


    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
