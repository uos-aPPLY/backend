package com.apply.diarypic.global.geocoding;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate; // 또는 WebClient
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Slf4j
@Service
public class GeocodingService {

    private final RestTemplate restTemplate;

    @Value("${google.maps.api.key}")
    private String apiKey;

    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    public GeocodingService(RestTemplate restTemplate) { // RestTemplate 주입받도록 변경
        this.restTemplate = restTemplate;
    }

    // 파싱된 위치 정보를 담을 내부 DTO
    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class ParsedAddress {
        private final String formattedAddress; // 전체 주소 (DB에 저장 안 함)
        private final String countryName;      // 국가명 (한국어)
        private final String adminAreaLevel1;  // 시/도 또는 주요 행정구역 (한국어)
        private final String locality;         // 시/군/구 또는 도시 (한국어)
    }

    public ParsedAddress getParsedAddressFromCoordinates(double latitude, double longitude) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                .queryParam("latlng", latitude + "," + longitude)
                .queryParam("key", apiKey)
                .queryParam("language", "ko"); // 한국어 결과 요청

        try {
            GeocodingResponse response = restTemplate.getForObject(uriBuilder.toUriString(), GeocodingResponse.class);

            if (response != null && "OK".equals(response.getStatus()) && response.getResults() != null && !response.getResults().isEmpty()) {
                GeocodingResult topResult = response.getResults().get(0);
                String formattedAddress = topResult.getFormattedAddress();
                List<GeocodingResult.AddressComponent> components = topResult.getAddressComponents();

                String country = null;
                String adminArea1 = null;
                String local = null;

                if (components != null) {
                    for (GeocodingResult.AddressComponent component : components) {
                        List<String> types = component.getTypes();
                        if (types.contains("country")) {
                            country = component.getLongName();
                        }
                        if (types.contains("administrative_area_level_1")) {
                            adminArea1 = component.getLongName();
                        }
                        if (types.contains("locality")) {
                            local = component.getLongName();
                        }
                        // "locality"가 없는 경우, "sublocality_level_1" 등을 차선으로 고려할 수 있음
                        if (local == null && types.contains("sublocality_level_1")) {
                            local = component.getLongName();
                        }
                        // 더 많은 타입 분석 가능 (예: "political", "sublocality" 조합 등)
                    }
                }
                // 만약 locality가 없고 adminAreaLevel1만 있다면, locality에 adminAreaLevel1 값을 사용할 수도 있음(국내 일부 지역)
                // 혹은 더 구체적인 규칙을 적용하여 primaryCityName을 결정

                log.debug("Geocoded Address: Formatted='{}', Country='{}', AdminArea1='{}', Locality='{}'",
                        formattedAddress, country, adminArea1, local);
                return new ParsedAddress(formattedAddress, country, adminArea1, local);
            } else {
                log.warn("Geocoding API로부터 유효한 주소를 받지 못했습니다. Status: {}, Lat: {}, Lng: {}",
                        response != null ? response.getStatus() : "N/A", latitude, longitude);
                return null;
            }
        } catch (Exception e) {
            log.error("Geocoding API 호출 중 오류 발생: Lat={}, Lng={}", latitude, longitude, e);
            return null;
        }
    }
}