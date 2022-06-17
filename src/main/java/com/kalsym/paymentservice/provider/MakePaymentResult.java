/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kalsym.paymentservice.models.daos.PaymentOrder;

/**
 *
 * @author user
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MakePaymentResult {
    public int providerId;
    public String sysTransactionId;
    public PaymentOrder orderCreated;
    public boolean isSuccess;
    public String paymentLink;
    public String hash;
    public String token;
    public String redirectUrl;

    public String hashDate;
}
