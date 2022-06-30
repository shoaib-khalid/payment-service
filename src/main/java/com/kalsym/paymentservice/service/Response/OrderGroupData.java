package com.kalsym.paymentservice.service.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class OrderGroupData {
    OrderGroup data;
    String message;
}
