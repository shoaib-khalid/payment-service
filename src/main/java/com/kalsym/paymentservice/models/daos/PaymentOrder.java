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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author user
 */
@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    String hash;
    String hashDate;

    Double paymentAmount;

    String customerName;
    String phoneNo;
    String email;
    String callbackUrl;

    String uniquePaymentId;
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

}