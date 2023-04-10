package com.kalsym.paymentservice.provider.Payhub2u;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.dto.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class Payhub2uPaymentLink extends SyncDispatcher {

    private final String generateLink_url;

    private final String accessKey;
    private final String callback_url;
    private final String redirecturl;
    private final String hosturl;
    private final String generateLinkKalsymKey;
    private final String systemTransactionId;
    private final String merchantId;

    private final PaymentRequest order;
    private final String logprefix;
    private final String location = "Payhub2uPaymentLink";
    private final Integer providerId;

    public Payhub2uPaymentLink(CountDownLatch latch, HashMap config, PaymentRequest order,
                               String systemTransactionId, Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "Payhub2u GeneratePaymentLink class initialized!!", "");
        this.generateLink_url = (String) config.get("payhub2u_url");
        this.generateLinkKalsymKey = (String) config.get("payhub2u_KalsymKey");
        this.accessKey = (String) config.get("accessKey");
        this.merchantId = (String) config.get("payhub2u_MerchantId");
        this.redirecturl = (String) config.get("redirectUr"); //https://dev-my.symplified.ai/thankyou/SUCCESS/ONLINEPAYMENT/Payment_was_successful?orderid=
        this.hosturl = (String) config.get("hostUrl");
        this.order = order;
        this.callback_url = (String) config.get("callback");
        this.providerId = providerId;

    }


    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        MakePaymentResult submitOrderResult = new MakePaymentResult();
        ProcessResult response = new ProcessResult();


        try {
            String date = new Date().toString();
            JSONObject jsonObject = new JSONObject();

            jsonObject.put("user_id", order.getCustomerId());
            jsonObject.put("transaction_id", order.getSystemTransactionId());
            jsonObject.put("name", order.getCustomerName());
            jsonObject.put("email", order.getEmail());
            jsonObject.put("mobile", order.getPhoneNo());
            jsonObject.put("description", "Payhub2u Payment [" + order.getSystemTransactionId() + "]");
            jsonObject.put("redirect_url", redirecturl + order.getTransactionId());
            jsonObject.put("callback_url", callback_url);
            jsonObject.put("callback_token", "756d6b90-7171-4be1-ad7b-13ae71b07253");
            LogUtil.info(logprefix, location, "Request Body ", jsonObject.toString());


            HttpHeaders headers = new HttpHeaders();
            headers.add("Accept", "application/json, text/plain, */*");
            headers.add("Content-Type", "application/json");
            headers.add("Host", hosturl);

            headers.add("Access-Key", accessKey);

            HttpEntity<String> res = new HttpEntity<>(jsonObject.toString(), headers);
            String getPaymentLnk = generateLink_url;


            try {
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> responses = restTemplate.exchange(getPaymentLnk, HttpMethod.POST, res, String.class);

                int statusCode = responses.getStatusCode().value();
                LogUtil.info(logprefix, location, "Responses", responses.getBody());
                if (statusCode == 200) {
                    LogUtil.info(logprefix, location, "Get Body: " + responses.getBody(), "");

                    JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);

                    PaymentOrder orderCreated = new PaymentOrder();
                    orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                    orderCreated.setEmail(order.getEmail());
                    orderCreated.setCustomerId(order.getCustomerId());
                    orderCreated.setCustomerName(order.getCustomerName());
                    orderCreated.setPhoneNo(order.getPhoneNo());
                    orderCreated.setCallbackUrl(callback_url);
                    orderCreated.setHash("");
                    orderCreated.setHashDate(date);
                    submitOrderResult.setOrderCreated(orderCreated);
                    submitOrderResult.setSuccess(true);
                    submitOrderResult.setProviderId(this.providerId);
                    submitOrderResult.setPaymentLink(jsonResp.get("secondary_url").getAsString().replace("{amount}", order.getPaymentAmount().toString()));
                    submitOrderResult.setHash("");
                    submitOrderResult.setSysTransactionId(systemTransactionId);
                    submitOrderResult.setDescription("Payhub2u Payment [" + order.getTransactionId() + "]");
                    submitOrderResult.setToken("");
                    submitOrderResult.setClientId(merchantId);


                } else {

                    LogUtil.info(logprefix, location, "Request failed", responses.getBody());

                }
            } catch (Exception exception) {
                LogUtil.info(logprefix, location, "Exception : ", exception.getMessage());
            }


            response.resultCode = 0;
            response.returnObject = submitOrderResult;
            LogUtil.info(logprefix, location, "Process finish", "");
            return response;
        } catch (Exception e) {
            LogUtil.info(logprefix, location, "Request failed", "");
            response.resultCode = -1;
            return response;
        }
    }

}