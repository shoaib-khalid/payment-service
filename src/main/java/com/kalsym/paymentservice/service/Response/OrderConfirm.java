package com.kalsym.paymentservice.service.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OrderConfirm {

    String id;
    String storeId;
    String subTotal;
    String serviceCharges;
    String deliveryCharges;
    String total;
    String completionStatus;
    String paymentStatus;
    String customerNotes;
    String privateAdminNotes;
    String cartId;
    String customerId;
    String created;
    String updated;
    String invoiceId;

}
