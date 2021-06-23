package com.kalsym.paymentservice.service.Response;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class StoreDetails {
    String id;
    String name;
    String city;
    String address;
    String clientId;
    String verticalCode;
    String storeDescription;
    String postcode;
    String domain;
    String liveChatOrdersGroupId;
    String liveChatOrdersGroupName;
    String liveChatCsrGroupId;
    String liveChatCsrGroupName;
    String regionCountryId;
    String phoneNumber;
    String regionCountryStateId;
    String serviceChargesPercentage;

}
