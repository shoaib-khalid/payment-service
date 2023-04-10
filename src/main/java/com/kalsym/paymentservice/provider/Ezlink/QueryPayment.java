/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider.Ezlink;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.QueryPaymentResult;
import com.kalsym.paymentservice.utils.LogUtil;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;

public class QueryPayment extends SyncDispatcher {

    private final String queryOrder_url;
    private final String queryOrder_token;
    private final int connectTimeout;
    private final int waitTimeout;
    private String spOrderId;
    private HashMap productMap;
    private String atxProductCode = "";
    private String sessionToken;
    private String sslVersion="SSL";
    private String logprefix;
    private String location="MrSpeedyQueryOrder";
    private final String systemTransactionId;
    
    public QueryPayment(CountDownLatch latch, HashMap config, String spOrderId, String systemTransactionId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId =systemTransactionId;
        LogUtil.info(logprefix, location, "MrSpeedy QueryOrder class initiliazed!!", "");
        this.queryOrder_url = (String) config.get("queryorder_url");
        this.queryOrder_token = (String) config.get("queryorder_token");
        this.connectTimeout = Integer.parseInt((String) config.get("queryorder_connect_timeout"));
        this.waitTimeout = Integer.parseInt((String) config.get("queryorder_wait_timeout"));
        productMap = (HashMap) config.get("productCodeMapping");
        this.spOrderId = spOrderId;
        this.sslVersion = (String) config.get("ssl_version");
    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();            
        HashMap httpHeader = new HashMap();
        httpHeader.put("X-DV-Auth-Token", this.queryOrder_token);
        httpHeader.put("Connection", "close");
        String url = this.queryOrder_url + "?order_id="+this.spOrderId;
        HttpResult httpResult = HttpsGetConn.SendHttpsRequest("GET", systemTransactionId, url, httpHeader, this.connectTimeout, this.waitTimeout);
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
    
     private QueryPaymentResult extractResponseBody(String respString) {
       QueryPaymentResult queryOrderResult = new QueryPaymentResult();       
        try {
            JsonObject jsonResp = new Gson().fromJson(respString, JsonObject.class);
            boolean isSuccess = jsonResp.get("is_successful").getAsBoolean();
            JsonArray orderList = jsonResp.get("orders").getAsJsonArray();
            JsonObject orderObject = orderList.get(0).getAsJsonObject();
            LogUtil.info(logprefix, location, "isSuccess:"+isSuccess, "");
            queryOrderResult.isSuccess=isSuccess;
            //extract order cancelled
            String orderId = orderObject.get("order_id").getAsString();
            String orderName = orderObject.get("order_name").getAsString();
            String status = orderObject.get("status").getAsString();
            String description = orderObject.get("status_description").getAsString();
            String created = orderObject.get("created_datetime").getAsString();  
            PaymentOrder orderFound = new PaymentOrder();
            orderFound.setSpOrderId(orderId);
            orderFound.setStatus(status);
            orderFound.setStatusDescription(description);
            orderFound.setCreatedDate(created);
            queryOrderResult.orderFound=orderFound;
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return queryOrderResult;
    }

}