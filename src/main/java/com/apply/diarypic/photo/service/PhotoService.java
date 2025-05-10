package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto; // 수정된 DTO 사용
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.global.geocoding.GeocodingService;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId; // UTC 시간을 시스템 기본 시간으로 변환 시 필요할 수 있음
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;
    private final GeocodingService geocodingService;

    @Transactional
    public List<PhotoResponse> uploadPhotosWithMetadata(List<MultipartFile> files,
                                                        List<PhotoUploadItemDto> metadataList,
                                                        Long userId) {
        List<PhotoResponse> responses = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            PhotoUploadItemDto metadataItem = metadataList.get(i);

            String s3Url = null;
            try {
                s3Url = s3Uploader.upload(file, "photos/" + userId);

                LocalDateTime shootingDateTime = metadataItem.getShootingDateTime();
                // 프론트가 UTC (YYYY-MM-DDTHH:mm:ss.SSSZ)로 보냈고, Spring이 LocalDateTime으로 변환 시
                // 시스템 기본 시간대로 변환되었을 수 있습니다. 또는 UTC 그대로일 수도 있습니다.
                // DB에 저장 시 일관된 시간대(예: UTC 또는 서버 기본 시간대)로 저장하는 것이 중요합니다.
                // 만약 shootingDateTime이 항상 UTC여야 한다면, Instant로 받거나, 변환 로직 추가 고려.
                // 여기서는 Spring의 기본 변환을 따른다고 가정.

                String locationString = null; // "위도,경도" 문자열로 변환하여 저장
                String countryName = null;
                String adminAreaLevel1 = null;
                String locality = null;

                PhotoUploadItemDto.LocationDto locationDto = metadataItem.getLocation();
                if (locationDto != null && locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
                    double latitude = locationDto.getLatitude();
                    double longitude = locationDto.getLongitude();
                    locationString = latitude + "," + longitude; // DB 저장을 위한 문자열

                    try {
                        GeocodingService.ParsedAddress parsedAddress = geocodingService.getParsedAddressFromCoordinates(latitude, longitude);
                        if (parsedAddress != null) {
                            countryName = parsedAddress.getCountryName();
                            adminAreaLevel1 = parsedAddress.getAdminAreaLevel1();
                            locality = parsedAddress.getLocality();
                        }
                    } catch (Exception e) {
                        log.warn("userId: {}, 파일: {}, locationDto: {}, 주소 변환 중 오류: {}", userId, file.getOriginalFilename(), locationDto, e.getMessage(), e);
                    }
                } else if (metadataItem.getLocation() != null) { // location 객체는 있는데 위도/경도가 null인 경우
                    log.warn("userId: {}, 파일: {}, location 객체는 있으나 위도 또는 경도 값이 null입니다.", userId, file.getOriginalFilename());
                }


                DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                        .photoUrl(s3Url)
                        .userId(userId)
                        .shootingDateTime(shootingDateTime)
                        .location(locationString) // "위도,경도" 문자열 저장
                        .countryName(countryName)
                        .adminAreaLevel1(adminAreaLevel1)
                        .locality(locality)
                        .build();

                DiaryPhoto savedDiaryPhoto = photoRepository.save(diaryPhoto);
                responses.add(PhotoResponse.from(savedDiaryPhoto));
                log.info("userId: {}, 파일: {} 업로드 및 DB 저장 성공. Photo ID: {}", userId, file.getOriginalFilename(), savedDiaryPhoto.getId());

            } catch (Exception e) {
                log.error("userId: {}, 파일: {} 업로드 중 심각한 오류 발생: {}", userId, file.getOriginalFilename(), e.getMessage(), e);
            }
        }
        return responses;
    }
}