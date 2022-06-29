package com.kalsym.paymentservice.service.Response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderGroup {

    String id;
    String subTotal;
    Double total;
    String regionCountryId;
}
