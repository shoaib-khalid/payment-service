/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.models.daos;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author user
 */
@Getter
@Setter
public class PaymentRequest {
   Integer customerId;
   String customerName;
   String transactionId;
   String productCode;
   Double paymentAmount;
   String systemTransactionId;
}
