package com.kalsym.paymentservice.service.Response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class OrderGroup {

    String id;
    String subTotal;
    Double total;
    String regionCountryId;
}
