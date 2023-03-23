package com.kalsym.paymentservice.provider.Payhub2u;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Payhub2uATXResponse {

    @JsonProperty("total")
    int total;

    @JsonProperty("limit")
    int limit;

    @JsonProperty("skip")
    int skip;

    @JsonProperty("data")
    List<Payhub2uResponseData> data;

    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

}
