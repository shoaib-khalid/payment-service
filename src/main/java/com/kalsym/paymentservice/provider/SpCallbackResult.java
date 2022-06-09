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
public class SpCallbackResult {
    public int providerId;
    public String spOrderId;
    public String status;
    public String spErrorCode;
    public String paymentTransactionId;
    public int statusId;
    public String orderId;
    public String paymentChanel;

}
