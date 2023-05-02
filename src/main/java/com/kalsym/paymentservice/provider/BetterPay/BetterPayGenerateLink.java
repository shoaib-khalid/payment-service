package com.kalsym.paymentservice.provider.BetterPay;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kalsym.paymentservice.models.daos.PaymentOrder;
import com.kalsym.paymentservice.models.dto.PaymentRequest;
import com.kalsym.paymentservice.provider.MakePaymentResult;
import com.kalsym.paymentservice.provider.ProcessResult;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;

public class BetterPayGenerateLink extends SyncDispatcher {

    private final String generatelink_url;
    private final String generatelink_ApiKey;
    private final int providerId;
    private final String systemTransactionId;
    private final String merchantId;
    private final String bnplMerchantId;
    private String callbackUrlSuccess;
    private String callbackUrlFail;
    private String callbackBeUrl;

    private PaymentRequest order;
    private String logprefix;
    private String location = "BetterPayPaymentLink";

    private String creditCardRequestUrlProduction;
    private final String generatelink_ApiKeyProduction;
    private final String merchantIdProduction;
    private final String hostProdution;

    private String creditCardRequestUrl;
    private String currency;
    private String bankCode;
    private String respondCode;
    private String skipReceipt;
    private String host;

    // String requestUrlStaging =
    // "https://www.demo.betterpay.me/merchant/api/v2/lite/direct/receiver";//
    // Staging
    // String requestUrlProduction =
    // "https://lite.betterpay.me/api/merchant/v1/direct";
    // String callBackUrlBe =
    // "https://api.symplified.it/payment-service/v1/payments/request/callback";
    // String callBackUrlFeSuccess =
    // "https://paymentv2.dev-my.symplified.ai/thankyou/SUCCESS";
    // String callBackUrlFeFail =
    // "https://paymentv2.dev-my.symplified.ai/thankyou/FAILED";
    // String currency = "MYR";
    // String merchantIdStaging = "10363";// Staging
    // String merchantIdProduction = "R1184";// Staging
    // String desc = "TESTING";
    // String bankCode;
    // String respondCode = "1";
    // String skipReceipt = "0";

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
        // this.bnplGeneratelinkApiKey = (String) config.get("bnplGenerateLinkApiKey");
        // this.mobilePaymentLinkUrl = (String) config.get("mobilePaymentLinkUrl");
        this.callbackUrlSuccess = (String) config.get("callbackUrlSuccess");
        this.callbackUrlFail = (String) config.get("callbackUrlFail");
        this.callbackBeUrl = (String) config.get("callbackBeUrl");

        this.creditCardRequestUrl = (String) config.get("creditCardRequestUrl");
        this.currency = (String) config.get("currency");
        this.bankCode = (String) config.get("bankCode");
        this.respondCode = (String) config.get("respondCode");
        this.skipReceipt = (String) config.get("skipReceipt");
        this.host = (String) config.get("host");
        this.order = order;
        this.providerId = providerId;

        // production temp
        this.generatelink_ApiKeyProduction = (String) config.get("betterPay_ApiKey_Production");
        this.creditCardRequestUrlProduction = (String) config.get("creditCardRequestUrlProduction");
        this.merchantIdProduction = (String) config.get("merchantIdProduction");
        this.hostProdution = (String) config.get("hostProdution");

    }

    @Override
    public ProcessResult process() {
        LogUtil.info(logprefix, location, "Process start", "");
        ProcessResult response = new ProcessResult();
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

            DecimalFormat df = new DecimalFormat("#.##");
            String formattedNumber = df.format(order.getPaymentAmount());
            double result = Double.parseDouble(formattedNumber);
            if (order.getBrowser().equals("WEBSITE")) {
                callbackUrlSuccess = callbackUrlSuccess + "?channel=DELIVERIN";
                callbackUrlFail = callbackUrlFail + "?channel=DELIVERIN";
                if (order.getStoreVerticalCode().equals("FnB")) {
                    String beforeHash = result + order.getEmail() + order.getCustomerName().replaceAll(" ", "")
                            + callbackUrlFail + callbackUrlSuccess + "MYR"
                            + order.getOrderInvoiceNo() + this.merchantId + order.getPaymentDescription()
                            + order.getPhoneNo();
                    String hashValue = "";
                    LogUtil.info(logprefix, location, "Before hash value", beforeHash);

                    try {
                        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                        SecretKeySpec secretKeySpec;
                        secretKeySpec = new SecretKeySpec(generatelink_ApiKey.getBytes(), "HmacSHA256");

                        hmacSha256.init(secretKeySpec);

                        byte[] hmacBytes = hmacSha256.doFinal(beforeHash.getBytes());

                        // Convert the byte array to a hexadecimal string representation
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hmacBytes) {
                            sb.append(String.format("%02x", b));
                        }
                        hashValue = sb.toString();
                        System.out.println("HMAC-SHA256: " + hashValue);
                    } catch (Exception e) {
                        LogUtil.info(order.getSystemTransactionId(), location, "Better Pay HMAC Exception  ",
                                e.getMessage());
                    }
                    LogUtil.info(logprefix, location, "Hash value", hashValue);

                    orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                    submitOrderResult.setOrderCreated(orderCreated);
                    submitOrderResult.setSuccess(true);
                    submitOrderResult.setProviderId(this.providerId);
                    submitOrderResult.setHash(hashValue);
                    submitOrderResult.setPaymentLink(generatelink_url);
                    submitOrderResult.setClientId(this.merchantId);
                    submitOrderResult.setSysTransactionId(systemTransactionId);
                    submitOrderResult.setInvoiceId(order.getOrderInvoiceNo());
                    submitOrderResult.setRedirectFailUrl(callbackUrlFail);
                    submitOrderResult.setRedirectSuccessUrl(callbackUrlSuccess);

                    submitOrderResult.token = "";
                } else {
                    if (order.getOnlinePayment()) {
                        String beforeHash = result + order.getEmail() + order.getCustomerName().replaceAll(" ", "")
                                + callbackUrlFail + callbackUrlSuccess + "MYR" + order.getOrderInvoiceNo()
                                + this.merchantId
                                + order.getPaymentDescription()
                                + order.getPhoneNo();
                        String hashValue = "";
                        LogUtil.info(logprefix, location, "Before hash value", beforeHash);

                        try {
                            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                            SecretKeySpec secretKeySpec;
                            secretKeySpec = new SecretKeySpec(generatelink_ApiKey.getBytes(), "HmacSHA256");

                            hmacSha256.init(secretKeySpec);

                            byte[] hmacBytes = hmacSha256.doFinal(beforeHash.getBytes());

                            // Convert the byte array to a hexadecimal string representation
                            StringBuilder sb = new StringBuilder();
                            for (byte b : hmacBytes) {
                                sb.append(String.format("%02x", b));
                            }
                            hashValue = sb.toString();

                            System.out.println("HMAC-SHA256: " + hashValue);
                        } catch (Exception e) {
                            LogUtil.info(order.getSystemTransactionId(), location, "Better Pay HMAC Exception  ",
                                    e.getMessage());

                        }
                        LogUtil.info(logprefix, location, "Hash value", hashValue);

                        orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                        submitOrderResult.setOrderCreated(orderCreated);
                        submitOrderResult.setSuccess(true);
                        submitOrderResult.setProviderId(this.providerId);
                        submitOrderResult.setHash(hashValue);
                        submitOrderResult.setPaymentLink(generatelink_url);
                        submitOrderResult.setClientId(this.merchantId);
                        submitOrderResult.setSysTransactionId(systemTransactionId);
                        submitOrderResult.setInvoiceId(order.getOrderInvoiceNo());
                        submitOrderResult.setRedirectFailUrl(callbackUrlFail);
                        submitOrderResult.setRedirectSuccessUrl(callbackUrlSuccess);

                        submitOrderResult.token = "";
                    } else {
                        String beforeHash = result + order.getEmail() + order.getCustomerName().replaceAll(" ", "")
                                + callbackUrlFail + callbackUrlSuccess + "MYR"
                                + order.getOrderInvoiceNo() + this.merchantId + order.getPaymentDescription()
                                + order.getPhoneNo();
                        String hashValue = "";
                        LogUtil.info(logprefix, location, "Before hash value", beforeHash);

                        try {
                            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                            SecretKeySpec secretKeySpec;
                            secretKeySpec = new SecretKeySpec(generatelink_ApiKey.getBytes(), "HmacSHA256");

                            hmacSha256.init(secretKeySpec);

                            byte[] hmacBytes = hmacSha256.doFinal(beforeHash.getBytes());

                            // Convert the byte array to a hexadecimal string representation
                            StringBuilder sb = new StringBuilder();
                            for (byte b : hmacBytes) {
                                sb.append(String.format("%02x", b));
                            }
                            hashValue = sb.toString();

                            System.out.println("HMAC-SHA256: " + hashValue);
                        } catch (Exception e) {
                            LogUtil.info(order.getSystemTransactionId(), location, "Better Pay HMAC Exception  ",
                                    e.getMessage());

                        }
                        LogUtil.info(logprefix, location, "Hash value", hashValue);

                        orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                        submitOrderResult.setOrderCreated(orderCreated);
                        submitOrderResult.setSuccess(true);
                        submitOrderResult.setProviderId(this.providerId);
                        submitOrderResult.setHash(hashValue);
                        submitOrderResult.setPaymentLink(generatelink_url);
                        submitOrderResult.setClientId(this.bnplMerchantId);
                        submitOrderResult.setSysTransactionId(systemTransactionId);
                        submitOrderResult.setInvoiceId(order.getOrderInvoiceNo());
                        submitOrderResult.setRedirectFailUrl(callbackUrlFail);
                        submitOrderResult.setRedirectSuccessUrl(callbackUrlSuccess);
                        submitOrderResult.token = "";
                    }
                }
            } else {

                callbackUrlSuccess = callbackUrlSuccess + "?channel=TERMINAL";
                callbackUrlFail = callbackUrlFail + "?channel=TERMINAL";

                String message = "";
                JsonObject object = new JsonObject();

                if (order.getPaymentType().equals("CREDIT")) {
                    bankCode = "CREDIT";
                    object.addProperty("merchant_id", merchantIdProduction);
                } else {
                    object.addProperty("merchant_id", merchantId);
                    bankCode = order.getPaymentService();
                }

                RestTemplate restTemplate = new RestTemplate();

                object.addProperty("invoice", order.getOrderInvoiceNo());
                object.addProperty("amount", result);
                object.addProperty("payment_desc", order.getPaymentDescription()); // will change
                object.addProperty("currency", currency);
                object.addProperty("buyer_name", order.getCustomerName());
                object.addProperty("buyer_email", order.getEmail());
                object.addProperty("phone", order.getPhoneNo());
                object.addProperty("callback_url_be", callbackBeUrl);
                object.addProperty("callback_url_fe_succ", callbackUrlSuccess);
                object.addProperty("callback_url_fe_fail", callbackUrlFail);
                object.addProperty("bank_code", bankCode);
                object.addProperty("respond", respondCode);
                object.addProperty("skip_receipt", skipReceipt);
                if (bankCode.equals("CREDIT")) {
                    object.addProperty("card_number", order.getCreditCardPaymentOptions().getCardNo());
                    object.addProperty("card_year", order.getCreditCardPaymentOptions().getCardYear());
                    object.addProperty("card_month", order.getCreditCardPaymentOptions().getCardMonth());
                    object.addProperty("card_cvv", order.getCreditCardPaymentOptions().getCardCCV());
                    message = result + bankCode + order.getEmail()
                            + order.getCustomerName().replaceAll(" ", "") + callbackBeUrl
                            + callbackUrlFail + callbackUrlSuccess + order.getCreditCardPaymentOptions().getCardCCV()
                            + order.getCreditCardPaymentOptions().getCardMonth()
                            + order.getCreditCardPaymentOptions().getCardNo()
                            + order.getCreditCardPaymentOptions().getCardYear() + currency
                            + order.getOrderInvoiceNo() + merchantIdProduction
                            + order.getPaymentDescription() + order.getPhoneNo() + respondCode + skipReceipt;
                } else {
                    message = result + bankCode + order.getEmail()
                            + order.getCustomerName().replaceAll(" ", "") + callbackBeUrl
                            + callbackUrlFail + callbackUrlSuccess + currency + order.getOrderInvoiceNo()
                            + merchantId
                            + order.getPaymentDescription() + order.getPhoneNo() + respondCode + skipReceipt;
                }
                String hmacHex = "";
         /*        String secretStaging = "XPePraM9Lsgz";// Staging
                String secretProduction = "MWsREUapAZ";// production */

                try {
                    Mac hmacSha256 = Mac.getInstance("HmacSHA256");
                    SecretKeySpec secretKeySpec;
                    if (order.getPaymentType().equals("CREDIT")) {
                        secretKeySpec = new SecretKeySpec(generatelink_ApiKeyProduction.getBytes(), "HmacSHA256");
                    } else {
                        secretKeySpec = new SecretKeySpec(generatelink_ApiKey.getBytes(), "HmacSHA256");
                    }
                    hmacSha256.init(secretKeySpec);

                    byte[] hmacBytes = hmacSha256.doFinal(message.getBytes());

                    // Convert the byte array to a hexadecimal string representation
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hmacBytes) {
                        sb.append(String.format("%02x", b));
                    }
                    hmacHex = sb.toString();

                    System.out.println("HMAC-SHA256: " + hmacHex);
                } catch (Exception e) {
                    LogUtil.info(order.getSystemTransactionId(), location, "Better Pay HMAC Exception  ",
                            e.getMessage());

                }
                object.addProperty("hash", hmacHex);
                System.err.println(object.toString());
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");
                String url;

                if (order.getPaymentType().equals("CREDIT")) {
                    headers.set("Host", hostProdution);
                    headers.set("Content-Length", "111");
                    headers.set("User-Agent", "PostmanRuntime/7.32.2");
                    url = creditCardRequestUrlProduction;
                } else {
                    url = creditCardRequestUrl;
                }
                HttpEntity<String> data = new HttpEntity<>(object.toString(), headers);
                try {
                    ResponseEntity<String> responses = restTemplate.exchange(url, HttpMethod.POST, data,
                            String.class);
                    int statusCode = responses.getStatusCode().value();
                    LogUtil.info(logprefix, location, "Responses", responses.getBody());
                    if (statusCode == 200) {
                        LogUtil.info(logprefix, location, "Get Token: " + responses.getBody(), "");

                        JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                        LogUtil.info(logprefix, location, "Get Response In Json: " + jsonResp.toString(), "");
                        if (jsonResp.get("response").toString().equals("00")) {

                            orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                            submitOrderResult.setOrderCreated(orderCreated);
                            submitOrderResult.setSuccess(true);
                            submitOrderResult.setProviderId(this.providerId);
                            submitOrderResult.setPaymentLink(jsonResp.get("payment_url").getAsString());
                            submitOrderResult.setHash("");
                            submitOrderResult.setSysTransactionId(systemTransactionId);
                            submitOrderResult.token = "";
                        } else {
                            orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                            submitOrderResult.setOrderCreated(orderCreated);
                            submitOrderResult.setSuccess(false);
                            submitOrderResult.setProviderId(this.providerId);
                            submitOrderResult.setPaymentLink("");
                            submitOrderResult.setHash("");
                            submitOrderResult.setSysTransactionId(systemTransactionId);
                            submitOrderResult.token = "";
                            submitOrderResult.description = "Cannot Process The Payment. Please Verify With Merchant";

                        }
                    } else {
                        JsonObject jsonResp = new Gson().fromJson(responses.getBody(), JsonObject.class);
                        orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                        submitOrderResult.setOrderCreated(orderCreated);
                        submitOrderResult.setSuccess(false);
                        submitOrderResult.setProviderId(this.providerId);
                        submitOrderResult.setPaymentLink("");
                        submitOrderResult.setHash("");
                        submitOrderResult.setSysTransactionId(systemTransactionId);
                        submitOrderResult.token = "";
                        submitOrderResult.description = jsonResp.get("comment").getAsString();

                        LogUtil.info(logprefix, location, "Request failed", responses.getBody());
                    }
                } catch (Exception exception) {

                    orderCreated.setCreatedDate(DateTimeUtil.currentTimestamp());
                    submitOrderResult.setOrderCreated(orderCreated);
                    submitOrderResult.setSuccess(false);
                    submitOrderResult.setProviderId(this.providerId);
                    submitOrderResult.setPaymentLink("");
                    submitOrderResult.setHash("");
                    submitOrderResult.setSysTransactionId(systemTransactionId);
                    submitOrderResult.token = "";
                    submitOrderResult.description = exception.getMessage();

                    LogUtil.info(logprefix, location, "Request failed", exception.getMessage());
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
