package com.kalsym.paymentservice.service.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.criterion.Order;


@Getter
@Setter
@ToString
public class OrderConfirmData {
    OrderConfirm data;
    String message;
}
