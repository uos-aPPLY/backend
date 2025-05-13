package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto;
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

                String locationString = null;
                String countryName = null;
                String adminAreaLevel1 = null;
                String locality = null;

                PhotoUploadItemDto.LocationDto locationDto = metadataItem.getLocation();
                if (locationDto != null && locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
                    double latitude = locationDto.getLatitude();
                    double longitude = locationDto.getLongitude();
                    locationString = latitude + "," + longitude;

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
                } else if (metadataItem.getLocation() != null) {
                    log.warn("userId: {}, 파일: {}, location 객체는 있으나 위도 또는 경도 값이 null입니다.", userId, file.getOriginalFilename());
                }


                DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                        .photoUrl(s3Url)
                        .userId(userId)
                        .shootingDateTime(shootingDateTime)
                        .location(locationString)
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