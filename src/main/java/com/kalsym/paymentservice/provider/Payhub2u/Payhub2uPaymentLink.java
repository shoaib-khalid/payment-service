package com.kalsym.paymentservice.provider.Payhub2u;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.daos.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class Payhub2uPaymentLink extends SyncDispatcher {

    private final String generateLink_url;

    private final String accessKey;
    private final String callback_url;
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

            String req = merchantId + order.getTransactionId() + order.getPaymentAmount() + date;
            String hash = hash(req, generateLinkKalsymKey);


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
            submitOrderResult.setPaymentLink(paymentUrl);
            submitOrderResult.setHash(hash);
            submitOrderResult.setSysTransactionId(systemTransactionId);
            submitOrderResult.setDescription("Payhub2u Payment [" + order.getTransactionId() + "]");
            submitOrderResult.setToken(accessKey);
            submitOrderResult.setClientId(merchantId);
            orderCreated.setHash(hash);
            orderCreated.setHashDate(date);
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