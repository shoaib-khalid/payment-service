package com.kalsym.paymentservice.provider.Payhub2u;

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
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
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
        ProcessResult response = new ProcessResult();

        response.returnObject = extractResponseBody(this.generateLink_url);
        LogUtil.info(logprefix, location, "Process finish", "");
        return response;
    }

    private MakePaymentResult extractResponseBody(String paymentUrl) {
        MakePaymentResult submitOrderResult = new MakePaymentResult();
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


            HttpHeaders headers = new HttpHeaders();
            headers.add("Accept", "application/json, text/plain, */*");
            headers.add("Content-Type", "application/json");
            headers.add("Host", hosturl);


            headers.add("Access-Key", accessKey);


            HttpEntity<String> res = new HttpEntity<>(jsonObject.toString(), headers);
            String getPaymentLnk = generateLink_url;

            HashMap httpHeader = new HashMap();

            try {
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> responses = restTemplate.exchange(getPaymentLnk, HttpMethod.POST, res, String.class);

                int statusCode = responses.getStatusCode().value();
                LogUtil.info(logprefix, location, "Responses", responses.getBody());
                if (statusCode == 200) {
                    LogUtil.info(logprefix, location, "Get Body: " + responses.getBody(), "");

                    JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
//                    PaymentResponse paymentResponse = new PaymentResponse();
//                    paymentResponse.setError(jsonResp.get("error").getAsBoolean());
//                    paymentResponse.setMessage(jsonResp.get("message").getAsString());
//                    paymentResponse.setUrl(jsonResp.get("url").getAsString());
//                    String seconUrl = jsonResp.get("secondary_url").getAsString().replace("{amount}", getTransction.get().getTransactionAmount().toString());
//                    paymentResponse.setSecondaryUrl(seconUrl);
//
//                    return paymentResponse;


                    PaymentOrder orderCreated = new PaymentOrder();
                    orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                    orderCreated.setEmail(order.getEmail());
                    orderCreated.setCustomerId(order.getCustomerId());
                    orderCreated.setCustomerName(order.getCustomerName());
                    orderCreated.setPhoneNo(order.getPhoneNo());
                    orderCreated.setCallbackUrl(callback_url);
                    submitOrderResult.setOrderCreated(orderCreated);
                    submitOrderResult.setSuccess(true);
                    submitOrderResult.setProviderId(this.providerId);
                    submitOrderResult.setPaymentLink(jsonResp.get("secondary_url").getAsString().replace("{amount}", order.getPaymentAmount().toString()));
                    submitOrderResult.setHash("");
                    submitOrderResult.setSysTransactionId(systemTransactionId);
                    submitOrderResult.setDescription("Payhub2u Payment [" + order.getTransactionId() + "]");
                    submitOrderResult.setToken("");
                    submitOrderResult.setClientId(merchantId);
                    orderCreated.setHash("");
                    orderCreated.setHashDate(date);


                } else {

                    LogUtil.info(logprefix, location, "Request failed", responses.getBody());

                }
            } catch (Exception exception) {
                LogUtil.info(logprefix, location, "Exception : ", exception.getMessage());
            }


//            String req = merchantId + order.getTransactionId() + order.getPaymentAmount() + date;
//            String hash = hash(req, generateLinkKalsymKey);
//
//
//            PaymentOrder orderCreated = new PaymentOrder();
//            orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
//            orderCreated.setEmail(order.getEmail());
//            orderCreated.setCustomerId(order.getCustomerId());
//            orderCreated.setCustomerName(order.getCustomerName());
//            orderCreated.setPhoneNo(order.getPhoneNo());
//            orderCreated.setCallbackUrl(callback_url);
//            submitOrderResult.setOrderCreated(orderCreated);
//            submitOrderResult.setSuccess(true);
//            submitOrderResult.setProviderId(this.providerId);
//            submitOrderResult.setPaymentLink(paymentUrl);
//            submitOrderResult.setHash(hash);
//            submitOrderResult.setSysTransactionId(systemTransactionId);
//            submitOrderResult.setDescription("Payhub2u Payment [" + order.getTransactionId() + "]");
//            submitOrderResult.setToken(accessKey);
//            submitOrderResult.setClientId(merchantId);
//            orderCreated.setHash(hash);
//            orderCreated.setHashDate(date);
        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }


    public String hash(String req, String key) {
        byte[] hmacSha256 = null;
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