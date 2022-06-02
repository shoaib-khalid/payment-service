package com.kalsym.paymentservice.models.requestbodies;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransactionRequest {
    String currencyCode;
    String merchantId;
    String merchantName;
    String token;
    String successUrl;
    String failureUrl;
    String checkoutUrl;
    String customerEmailAdrress;
    String customerMobileNo;
    String txnAmt;
    String basketId;
    String orderDate;
    String signature;
    String version;
    String txnDesc;
    String productCode;
    String tranType;
    String storeId;
}
