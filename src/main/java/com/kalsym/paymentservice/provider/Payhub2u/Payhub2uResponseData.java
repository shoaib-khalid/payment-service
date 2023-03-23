package com.kalsym.paymentservice.provider.Payhub2u;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Payhub2uResponseData {

    @JsonProperty("_id")
    String id;

    @JsonProperty("amount")
    String amount;

    @JsonProperty("transactionId")
    String transactionId;

    @JsonProperty("description")
    String description;

    @JsonProperty("createdAt")
    String createdAt;

    @JsonProperty("updatedAt")
    String updateAt;

    @JsonProperty("topupAmount")
    String topupAmount;

    @JsonProperty("charge")
    String charge;

    @JsonProperty("status")
    String status;

    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

}
