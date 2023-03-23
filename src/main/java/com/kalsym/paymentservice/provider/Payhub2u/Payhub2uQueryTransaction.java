//package com.kalsym.paymentservice.provider.Payhub2u;
//
//import com.kalsym.paymentservice.models.daos.PaymentOrder;
//import com.kalsym.paymentservice.provider.Ezlink.HttpResult;
//import com.kalsym.paymentservice.provider.Ezlink.HttpsGetConn;
//import com.kalsym.paymentservice.provider.ProcessResult;
//import com.kalsym.paymentservice.provider.SyncDispatcher;
//import com.kalsym.paymentservice.utils.LogUtil;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.HashMap;
//import java.util.concurrent.CountDownLatch;
//import java.util.logging.Logger;
//
//public class Payhub2uQueryTransaction extends SyncDispatcher {
//
//    private final String queryOrder_url;
//    private final int connectTimeout;
//    private final int waitTimeout;
//    private final String logprefix;
//
//    private final String token;
//    private final String accessToken;
//    private final String location = "SenangPayQueryStatus";
//    private final String systemTransactionId;
//
//    public Payhub2uQueryTransaction(CountDownLatch latch, HashMap config, PaymentOrder paymentOrder, String systemTransactionId) {
//        super(latch);
//        logprefix = systemTransactionId;
//        this.systemTransactionId = systemTransactionId;
//        LogUtil.info(logprefix, location, "SenangPay QueryOrder class initiliazed!!", "");
//        this.queryOrder_url = (String) config.get("queryOrder_url");
//        this.connectTimeout = Integer.parseInt((String) config.get("queryOrder_connect_timeout"));
//        this.waitTimeout = Integer.parseInt((String) config.get("queryOrder_wait_timeout"));
//        this.token = (String) config.get("token");
//        this.accessToken = (String) config.get("accessToken");
//    }
//
//    @Override
//    public ProcessResult process() {
//        LogUtil.info(logprefix, location, "Process start", "");
//        ProcessResult response = new ProcessResult();
//        HashMap httpHeader = new HashMap();
//
//        try {
//            String url;
//            url = queryOrder_url + "?action=queryTransaction&platform=payhub2u" + "&transactionId=" + systemTransactionId;
//
//            RestTemplate restTemplate = new RestTemplate();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.add("Authorization", "Bearer " + token);
//            headers.add("Access-Key", accessToken);
//
//            HttpEntity httpEntity = new HttpEntity(headers);
//
//            LogUtil.info("Sending request to atx-service: {} to get response: {} , httpEntity: {}", url, "admin", httpEntity);
//            ResponseEntity res = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Payhub2uATXResponse.class);
//
//            if (res != null) {
//                Payhub2uATXResponse atxResponse = (Payhub2uATXResponse) res.getBody();
//                assert atxResponse != null;
//                history = atxResponse.getData();
//                LogUtil.info("Response request to atx-service to get response: {} ", atxResponse.getData().toString());
//
//                return history;
//            } else {
//                LogUtil.info("Response request to atx-service to get response code: {} ", res.getStatusCode());
//            }
//
//        } catch (Exception exception) {
//
//            LogUtil.info(Logger.pattern, PayhubApplication.VERSION, "APICLIENT", " Exception Body : " + exception.getMessage());
//            return null;
//        }
//
//        HttpResult httpResult = HttpsGetConn.SendHttpsRequest("GET", this.systemTransactionId, url, httpHeader, this.connectTimeout, this.waitTimeout);
//
//        if (httpResult.resultCode == 0) {
//            response = extractResponseBody(httpResult.responseString);
//        } else {
//            LogUtil.info(logprefix, location, "Request failed", "");
//            response.resultCode = -1;
//        }
//        LogUtil.info(logprefix, location, "Process finish", "");
//        return response;
//    }
//
