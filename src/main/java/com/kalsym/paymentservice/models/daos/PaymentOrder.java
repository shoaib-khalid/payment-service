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
    String customerId;
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
    String paymentChannel;

    @Override
    public String toString() {
        return "PaymentOrder{" +
                "id=" + id +
                ", customerId=" + customerId +
                ", productCode='" + productCode + '\'' +
                ", clientTransactionId='" + clientTransactionId + '\'' +
                ", systemTransactionId='" + systemTransactionId + '\'' +
                ", itemDescription='" + itemDescription + '\'' +
                ", spId=" + spId +
                ", spOrderId='" + spOrderId + '\'' +
                ", spErrorCode='" + spErrorCode + '\'' +
                ", createdDate='" + createdDate + '\'' +
                ", status='" + status + '\'' +
                ", statusDescription='" + statusDescription + '\'' +
                ", updatedDate='" + updatedDate + '\'' +
                '}';
    }
}
