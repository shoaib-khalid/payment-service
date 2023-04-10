/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider.Ezlink;

import com.kalsym.paymentservice.models.dto.PaymentRequest;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.utils.LogUtil;
import com.kalsym.paymentservice.utils.DateTimeUtil;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import com.google.gson.JsonObject;
import com.google.gson.Gson;

public class GeneratePaymentLink extends SyncDispatcher {

    private final String generatelink_url;
    private final String generatelink_loginId;
    private final String generatelink_mobiApiKey;
    private final String generatelink_callbackurl;
    private final String generatelink_KalsymKey;
    private final int connectTimeout;
    private final int waitTimeout;
    private PaymentRequest order;
    private HashMap productMap;
    private String atxProductCode = "";
    private String sessionToken;
    private String sslVersion="SSL";
    private String logprefix;
    private String location="EzlinkGeneratePaymentLink";
    private final String systemTransactionId;
    private final int providerId;

    public GeneratePaymentLink(CountDownLatch latch, HashMap config, PaymentRequest order, String systemTransactionId, Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "Ezlink GeneratePaymentLink class initiliazed!!", "");
        this.generatelink_url = (String) config.get("generatelink_url");
        this.generatelink_loginId = (String) config.get("generatelink_loginId");
        this.generatelink_mobiApiKey = (String) config.get("generatelink_mobiApiKey");
        this.generatelink_callbackurl = (String) config.get("generatelink_callbackurl");
        this.connectTimeout = Integer.parseInt((String) config.get("generatelink_connect_timeout"));
        this.waitTimeout = Integer.parseInt((String) config.get("generatelink_wait_timeout"));
        productMap = (HashMap) config.get("productCodeMapping");
        this.order = order;
        this.sslVersion = (String) config.get("ssl_version");   
        this.generatelink_KalsymKey = (String) config.get("generatelink_KalsymKey");
        this.providerId = providerId;
    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();            
        HashMap httpHeader = new HashMap();
        httpHeader.put("loginId", this.generatelink_loginId);
        httpHeader.put("mobiApiKey", this.generatelink_mobiApiKey);
        httpHeader.put("Content-Type", "application/json");
        httpHeader.put("Connection", "close");
        String requestBody = generateRequestBody();
        HttpResult httpResult = HttpsPostConn.SendHttpsRequest("POST", this.systemTransactionId, this.generatelink_url, httpHeader, requestBody, this.connectTimeout, this.waitTimeout);
        if (httpResult.resultCode==0) {
            LogUtil.info(logprefix, location, "Request successful", "");
            response.resultCode=0;            
            response.returnObject=extractResponseBody(httpResult.responseString);
        } else {
            LogUtil.info(logprefix, location, "Request failed", "");
            response.resultCode=-1;
        }
        LogUtil.info(logprefix, location, "Process finish", "");
        return response;
    }
    
    private String generateRequestBody() {
        JsonObject jsonReq = new JsonObject();
        
        jsonReq.addProperty("service", "EXTERNAL_TXN_LINK_REQ");
        jsonReq.addProperty("customerName", order.getCustomerName());
        jsonReq.addProperty("invoiceId", order.getSystemTransactionId());        
        String encAmount = encodeAmount();
        jsonReq.addProperty("encAmount", encAmount);
        jsonReq.addProperty("callback", this.generatelink_callbackurl);         
        return jsonReq.toString();
    }
    
    private String encodeAmount() {
        double amountInCent = this.order.getPaymentAmount() * 100;
        int amountInCentInt = (int) amountInCent;
        System.out.println("amountInCentInt:"+amountInCentInt);
        String amountInStr = String.valueOf(amountInCentInt);
        
        int remainingZero = 12 - amountInStr.length();
        for (int i=0;i<remainingZero;i++) {
            amountInStr = "0" + amountInStr;
        }
        System.out.println("amountInStr:"+amountInStr);
        String mobiKey = this.generatelink_mobiApiKey.substring(0, 16);
        String iv = this.generatelink_mobiApiKey.substring(16);
        System.out.println("mobiKey:"+mobiKey);
        System.out.println("iv:"+iv);
        String encodedAmount = Encrypter.generateSignatureWithIV(amountInStr, mobiKey, iv);       
        return encodedAmount;
    }
    
    
    private MakePaymentResult extractResponseBody(String respString) {
        MakePaymentResult submitOrderResult = new MakePaymentResult();            
        try {
            JsonObject jsonResp = new Gson().fromJson(respString, JsonObject.class);
            String responseCode = jsonResp.get("responseCode").getAsString();
            String responseMessage = jsonResp.get("responseMessage").getAsString();
            if (responseCode.equalsIgnoreCase("0000")) {
                JsonObject responseData = jsonResp.get("responseData").getAsJsonObject();
                String link = responseData.get("opt").getAsString();
                String invoiceId = responseData.get("invoiceId").getAsString();                
                PaymentOrder orderCreated = new PaymentOrder();
                orderCreated.setSpOrderId(invoiceId);
                orderCreated.setSpErrorCode(responseCode);
                orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                submitOrderResult.orderCreated=orderCreated;
                submitOrderResult.providerId= this.providerId;
                submitOrderResult.isSuccess=true;
                submitOrderResult.paymentLink=link;
            } else {
                submitOrderResult.isSuccess=false;
            }            
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);        
        }
        return submitOrderResult;
    }
    
    private String generateInvoiceId() {
        String amountInStr = String.valueOf(this.order.getPaymentAmount());        
        System.out.println("amountInStr:"+amountInStr);
        String kalsymKey = this.generatelink_KalsymKey;
        byte[] iv = Encrypter.generateIvByte();        
        String encodedAmount = Encrypter.generateSignatureWithIV(amountInStr, kalsymKey, iv);       
        return encodedAmount;
    }

}