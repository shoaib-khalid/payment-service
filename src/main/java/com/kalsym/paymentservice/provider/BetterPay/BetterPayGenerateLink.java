package com.kalsym.paymentservice.provider.BetterPay;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.dto.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class BetterPayGenerateLink extends SyncDispatcher {

    private final String generatelink_url;
    private final String generatelink_ApiKey;
    private final int providerId;
    private final String systemTransactionId;
    private final String merchantId;
    private final String bnplMerchantId;

    private final String bnplGeneratelinkApiKey;
    private PaymentRequest order;
    private HashMap productMap;
    private String logprefix;
    private String location = "BetterPayPaymentLink";

    public BetterPayGenerateLink(CountDownLatch latch, HashMap config, PaymentRequest order,
                                 String systemTransactionId, Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "BetterPay GeneratePaymentLink class initiliazed!!", "");
        this.generatelink_url = (String) config.get("betterPay_url");
        this.generatelink_ApiKey = (String) config.get("betterPay_ApiKey");
        this.merchantId = (String) config.get("betterPay_MerchantId");
        this.bnplMerchantId = (String) config.get("bnplMerchantId");
        this.bnplGeneratelinkApiKey = (String) config.get("bnplGenerateLinkApiKey");
        productMap = (HashMap) config.get("productCodeMapping");
        this.order = order;
        this.providerId = providerId;

    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();

//        String beforeHash = generatelink_ApiKey + "|" + this.merchantId + "|" + systemTransactionId + "|" + order.getPaymentAmount() + "|" + order.getPaymentDescription() + "|MYR";

//        String hashValue = md5(beforeHash);
        LogUtil.info(logprefix, location, "Order Id : ", order.getTransactionId());

        LogUtil.info(logprefix, location, "String url: ", generatelink_url);
        response.returnObject = extractResponseBody(this.generatelink_url);


        LogUtil.info(logprefix, location, "Process finish", "");
        return response;
    }

    private MakePaymentResult extractResponseBody(String respString) {
        MakePaymentResult submitOrderResult = new MakePaymentResult();
        try {
            PaymentOrder orderCreated = new PaymentOrder();
            if (order.getStoreVerticalCode().equals("FnB")) {
                String beforeHash = generatelink_ApiKey + "|" + this.merchantId + "|" + systemTransactionId + "|" + order.getPaymentAmount() + "|" + order.getPaymentDescription() + "|MYR";
                String hashValue = md5(beforeHash);
                LogUtil.info(logprefix, location, "Hash value", hashValue);

                orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                submitOrderResult.setOrderCreated(orderCreated);
                submitOrderResult.setSuccess(true);
                submitOrderResult.setProviderId(this.providerId);
                submitOrderResult.setPaymentLink(respString);
                submitOrderResult.setHash(hashValue);
                submitOrderResult.setPaymentLink(generatelink_url);
                submitOrderResult.setClientId(this.merchantId);
                submitOrderResult.setSysTransactionId(systemTransactionId);
                submitOrderResult.token = "";
            } else {
                if (order.getOnlinePayment()) {
                    String beforeHash = generatelink_ApiKey + "|" + this.merchantId + "|" + systemTransactionId + "|" + order.getPaymentAmount() + "|" + order.getPaymentDescription() + "|MYR";
                    String hashValue = md5(beforeHash);
                    LogUtil.info(logprefix, location, "Hash value", hashValue);

                    orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                    submitOrderResult.setOrderCreated(orderCreated);
                    submitOrderResult.setSuccess(true);
                    submitOrderResult.setProviderId(this.providerId);
                    submitOrderResult.setPaymentLink(respString);
                    submitOrderResult.setHash(hashValue);
                    submitOrderResult.setPaymentLink(generatelink_url);
                    submitOrderResult.setClientId(this.merchantId);
                    submitOrderResult.setSysTransactionId(systemTransactionId);
                    submitOrderResult.token = "";
                } else {
                    String beforeHash = generatelink_ApiKey + "|" + this.merchantId + "|" + systemTransactionId + "|" + order.getPaymentAmount() + "|" + order.getPaymentDescription() + "|MYR";
                    String hashValue = md5(beforeHash);
                    LogUtil.info(logprefix, location, "Hash value", hashValue);

                    orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                    submitOrderResult.setOrderCreated(orderCreated);
                    submitOrderResult.setSuccess(true);
                    submitOrderResult.setProviderId(this.providerId);
                    submitOrderResult.setPaymentLink(respString);
                    submitOrderResult.setHash(hashValue);
                    submitOrderResult.setPaymentLink(generatelink_url);
                    submitOrderResult.setClientId(this.bnplMerchantId);
                    submitOrderResult.setSysTransactionId(systemTransactionId);
                    submitOrderResult.token = "";
                }
            }


        } catch (Exception ex) {
            LogUtil.error(logprefix, location, "Error extracting result", "", ex);
        }
        return submitOrderResult;
    }

    public static String md5(String input) {
        try {
            // Get an instance of MessageDigest for MD5 hash calculation
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Compute the hash of the input string
            byte[] bytes = md.digest(input.getBytes());

            // Convert the hash bytes to hexadecimal format
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
}






