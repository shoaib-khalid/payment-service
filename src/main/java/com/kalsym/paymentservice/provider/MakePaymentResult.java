/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.provider;

import com.kalsym.paymentservice.models.daos.PaymentOrder;

/**
 *
 * @author user
 */
public class MakePaymentResult {
    public int providerId;
    public PaymentOrder orderCreated;
    public boolean isSuccess;
    public String paymentLink;
}
