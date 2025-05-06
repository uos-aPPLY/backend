package com.apply.diarypic.global.geocoding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class GeocodingService {

    private final RestTemplate restTemplate;

    @Value("${google.maps.api.key}")
    private String apiKey;

    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    public GeocodingService() {
        this.restTemplate = new RestTemplate(); // RestTemplate 빈으로 등록해서 주입받아도 됩니다.
    }

    public String getAddressFromCoordinates(double latitude, double longitude) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                .queryParam("latlng", latitude + "," + longitude)
                .queryParam("key", apiKey)
                .queryParam("language", "ko"); // 한국어 주소로 받기

        try {
            GeocodingResponse response = restTemplate.getForObject(uriBuilder.toUriString(), GeocodingResponse.class);

            if (response != null && "OK".equals(response.getStatus()) && response.getResults() != null && !response.getResults().isEmpty()) {
                // 가장 첫 번째 결과의 주소를 사용
                return response.getResults().get(0).getFormattedAddress();
            } else {
                log.warn("Geocoding API로부터 유효한 주소를 받지 못했습니다. Status: {}, Lat: {}, Lng: {}",
                        response != null ? response.getStatus() : "N/A", latitude, longitude);
                return null; // 또는 기본 주소 문자열 반환
            }
        } catch (Exception e) {
            log.error("Geocoding API 호출 중 오류 발생: Lat={}, Lng={}", latitude, longitude, e);
            return null;
        }
    }
}