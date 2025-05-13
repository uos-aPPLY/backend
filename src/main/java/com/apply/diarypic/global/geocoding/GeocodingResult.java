package com.apply.diarypic.global.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class GeocodingResult {

    @JsonProperty("formatted_address")
    private String formattedAddress;

    @JsonProperty("address_components")
    private List<AddressComponent> addressComponents;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    public static class AddressComponent {
        @JsonProperty("long_name")
        private String longName;

        @JsonProperty("short_name")
        private String shortName;

        @JsonProperty("types")
        private List<String> types;
    }
}