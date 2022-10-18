package com.kalsym.paymentservice.provider.Payhub2u;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SpCallbackResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.LogUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class Payhub2uCallback extends SyncDispatcher {

    private final HashMap productMap;
    private final String logprefix;
    private final String location = "Payhub2uCallback";
    private final String systemTransactionId;
    private String spOrderId;
    private JsonObject jsonBody;

    private final String merchantId;
    private final String securedKey;
    private final String key;

    public Payhub2uCallback(CountDownLatch latch, HashMap config, Object jsonBody, String systemTransactionId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "Payhub2u OrderCallback class initialized!!", "");
        productMap = (HashMap) config.get("productCodeMapping");

        this.merchantId = (String) config.get("merchantId");
        this.securedKey = (String) config.get("securedKey");
        String jsonString = jsonBody.toString();
        this.key = (String) config.get("key");

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
        String status = this.jsonBody.get("status").getAsString();
        String basketID = this.jsonBody.get("id").getAsString();
        if (status.equals("paid")) {

            result.paymentTransactionId = this.jsonBody.get("transactionId").getAsString();
            result.providerId = 4;
            result.spOrderId = basketID;
            result.spErrorCode = "0";
            result.status = "Payment_was_successful";
            result.paymentChanel = this.jsonBody.get("bankName").getAsString();

            response.returnObject = result;
            response.resultCode = 0;
            response.isSuccess = true;
            response.resultString = "Payment_was_successful";


        } else {
            result.paymentTransactionId = basketID;
            result.providerId = 3;
            result.spErrorCode = "-1";
            result.status = "Payment_was_failed";
            result.paymentChanel = this.jsonBody.get("bankName").getAsString();

            response.returnObject = result;
            response.resultCode = 0;
            response.isSuccess = false;
            response.resultString = "Payment_was_failed";

        }


        return response;
    }


}

