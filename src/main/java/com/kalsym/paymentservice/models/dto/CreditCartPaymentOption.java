package com.kalsym.paymentservice.models.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreditCartPaymentOption {

    String cardNo;
    String cardYear;
    String cardMonth;
    String cardCCV;

}
