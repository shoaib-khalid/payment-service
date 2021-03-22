/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package com.kalsym.paymentservice.models.daos;

import com.fasterxml.jackson.annotation.JsonInclude;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author user
 */
@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentOrder {
    
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    Long id;
    Integer customerId;
    String productCode;
    String clientTransactionId;
    String systemTransactionId;
    String itemDescription;
    Integer spId;
    String spOrderId;
    String spErrorCode;
    String createdDate;
    String status;
    String statusDescription;
    String updatedDate;    
}
