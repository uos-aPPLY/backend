package com.apply.diarypic.global.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true) // 모르는 필드는 무시
public class GeocodingResult {
    @JsonProperty("formatted_address")
    private String formattedAddress;

    // Getters and Setters
    public String getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }
}