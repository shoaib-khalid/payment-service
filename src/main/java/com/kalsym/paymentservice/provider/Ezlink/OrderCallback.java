/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider.Ezlink;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SpCallbackResult;
import com.kalsym.paymentservice.utils.LogUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.LinkedHashMap;
        
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

public class OrderCallback extends SyncDispatcher {

    private String spOrderId;
    private final HashMap productMap;
    private final String logprefix;
    private final String location="EzlinkCallback";
    private final String systemTransactionId;
    private JsonObject jsonBody;
    
    public OrderCallback(CountDownLatch latch, HashMap config, Map jsonBody, String systemTransactionId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "Ezlink OrderCallback class initiliazed!!", "");
        productMap = (HashMap) config.get("productCodeMapping");
        Gson gson = new Gson();
        String jsonString = gson.toJson(jsonBody,Map.class);
        LogUtil.info(logprefix, location, "Request Body:"+jsonString, "");
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
        SpCallbackResult callbackResult = extractResponseBody();
        response.returnObject=callbackResult;
        LogUtil.info(logprefix, location, "Process finish", "");
        return response;
    }

    
     private SpCallbackResult extractResponseBody() {
        SpCallbackResult callbackResult = new SpCallbackResult();       
        try {
            
            String spErrorCode = "";
            String status = "FAILED";
            String responseCode="";
            String paymentTransactionId="";
            try {
                responseCode = jsonBody.get("responseCode").getAsString();
                spOrderId = jsonBody.get("trxId").getAsString();
                paymentTransactionId = jsonBody.get("invoiceId").getAsString();
                LogUtil.info(logprefix, location, "responseCode:"+responseCode, "");
                spErrorCode = responseCode;
                if (responseCode.equals("0000")) {
                    status="SUCCESS";
                } else {
                    status="FAILED";
                }
            } catch (Exception ex) {
                LogUtil.info(logprefix, location, "Cannot get responseCode", "");
            }
            
            String fpxResponseCode="";
            if (responseCode.equals("")) {
                try {
                    fpxResponseCode = jsonBody.get("fpx_debitAuthCode").getAsString();
                    spErrorCode = fpxResponseCode;
                    spOrderId = jsonBody.get("fpx_fpxTxnId").getAsString();
                    paymentTransactionId = jsonBody.get("fpx_sellerOrderNo").getAsString();
                    String[] temp = paymentTransactionId.split("_");
                    if (temp.length>1) {
                        paymentTransactionId = temp[0];
                    }
                    LogUtil.info(logprefix, location, "fpxResponseCode:"+fpxResponseCode, "");
                    if (fpxResponseCode.equals("00")) {
                        status="SUCCESS";
                    } else {
                        status="FAILED";
                    }
                } catch (Exception ex) {
                    LogUtil.info(logprefix, location, "Cannot get fpx_debitAuthCode", "");
                }
            }
            callbackResult.spErrorCode = spErrorCode;
            callbackResult.spOrderId=spOrderId;
            callbackResult.status=status;
            callbackResult.paymentTransactionId=paymentTransactionId;
            LogUtil.info(logprefix, location, "SpOrderId:"+spOrderId+" Status:"+status, "");
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return callbackResult;
    }

}