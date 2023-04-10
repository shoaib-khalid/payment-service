package com.kalsym.paymentservice.provider.EasyPaisa;

import com.kalsym.paymentservice.models.dto.PaymentRequest;
import com.kalsym.paymentservice.provider.SyncDispatcher;
import com.kalsym.paymentservice.utils.LogUtil;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class EasyPaisaGeneratePayLink  extends SyncDispatcher {

    private final String generatelink_url;
    private final String generatelink_KalsymKey;
    private final int connectTimeout;
    private final int providerId;
    private final int waitTimeout;
    private final String systemTransactionId;
    private final String merchantId;
    private PaymentRequest order;
    private HashMap productMap;
    private String atxProductCode = "";
    private String sslVersion = "SSL";
    private String logprefix;
    private String location = "EasyPaisaGeneratePayLink";
    private String host;

    public EasyPaisaGeneratePayLink(CountDownLatch latch, HashMap config, PaymentRequest order, String systemTransactionId, Integer providerId) {
        super(latch);
        logprefix = systemTransactionId;
        this.systemTransactionId = systemTransactionId;
        LogUtil.info(logprefix, location, "EasyPaisa GeneratePaymentLink class initiliazed!!", "");
        this.generatelink_url = (String) config.get("easyPaisa_url");
        this.generatelink_KalsymKey = (String) config.get("senangPay_KalsymKey");
        this.connectTimeout = Integer.parseInt((String) config.get("senangPay_connect_timeout"));
        this.waitTimeout = Integer.parseInt((String) config.get("senangPay_wait_timeout"));
        this.merchantId = (String) config.get("senangPay_MerchantId");
        productMap = (HashMap) config.get("productCodeMapping");
        this.host = (String) config.get("host");
        this.order = order;
        this.sslVersion = (String) config.get("ssl_version");
        this.providerId = providerId;

    }
}
