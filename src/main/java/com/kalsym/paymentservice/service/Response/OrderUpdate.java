package com.kalsym.paymentservice.service.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OrderUpdate {
    String comments;
    String created;
    String modifiedBy;
    String orderId;

    String status;
}
