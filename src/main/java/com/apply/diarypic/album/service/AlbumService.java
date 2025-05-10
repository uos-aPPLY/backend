package com.apply.diarypic.album.service;

import com.apply.diarypic.album.dto.AlbumDto;
import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.album.entity.DiaryAlbum;
import com.apply.diarypic.album.repository.AlbumRepository;
import com.apply.diarypic.album.repository.DiaryAlbumRepository;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final DiaryAlbumRepository diaryAlbumRepository;
    private final UserRepository userRepository;

    // ... (getUserAlbums, getDiariesInAlbum, deleteAlbum은 이전과 동일)
    public List<AlbumDto> getUserAlbums(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return albumRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(album -> AlbumDto.fromEntity(album, album.getDiaryAlbums() != null ? album.getDiaryAlbums().size() : 0))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiaryResponse> getDiariesInAlbum(Long userId, Long albumId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));

        if (!album.getUser().getId().equals(userId)) {
            throw new SecurityException("앨범에 대한 접근 권한이 없습니다.");
        }

        return diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .map(DiaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void processDiaryAlbums(Diary diary, List<DiaryPhoto> diaryPhotos) {
        if (diaryPhotos == null || diaryPhotos.isEmpty()) {
            return;
        }
        User user = diary.getUser();
        Set<String> albumNamesForThisDiary = new HashSet<>();

        for (DiaryPhoto photo : diaryPhotos) {
            String albumName = determineAlbumName(photo.getCountryName(), photo.getAdminAreaLevel1(), photo.getLocality());
            if (StringUtils.hasText(albumName)) {
                albumNamesForThisDiary.add(albumName);
            }
        }

        albumNamesForThisDiary.forEach(name -> {
            Optional<Album> existingAlbumOpt = albumRepository.findByNameAndUser(name, user);
            Album album = existingAlbumOpt.orElseGet(() -> {
                log.info("새로운 앨범 생성: '{}' for user {}", name, user.getId());
                Album newAlbum = Album.builder()
                        .name(name)
                        .user(user)
                        .coverImageUrl(!diaryPhotos.isEmpty() ? diaryPhotos.get(0).getPhotoUrl() : null)
                        .build();
                return albumRepository.save(newAlbum);
            });

            if (!diaryAlbumRepository.findByDiaryAndAlbum(diary, album).isPresent()) {
                DiaryAlbum diaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                diaryAlbumRepository.save(diaryAlbum);
                log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
            }
        });
    }

    // 앨범 이름 결정 헬퍼 메소드 (요청사항 반영)
    private String determineAlbumName(String countryName, String adminAreaLevel1, String locality) {
        if (!StringUtils.hasText(countryName)) {
            return "기타 장소"; // 또는 null 반환 후 호출부에서 처리
        }

        // 1. 국내인 경우 (countryName이 "대한민국")
        if ("대한민국".equals(countryName)) {
            if (StringUtils.hasText(locality)) {
                return locality; // 예: "수원시"
            } else if (StringUtils.hasText(adminAreaLevel1)){
                return adminAreaLevel1; // 예: "서울특별시", "경기도"
            } else {
                return countryName; // 모든 하위 지역 정보가 없을 경우 "대한민국"
            }
        } else { // 2. 해외인 경우
            if (StringUtils.hasText(adminAreaLevel1)) {
                return countryName + " - " + adminAreaLevel1; // 예: "일본 - 오사카부", "베트남 - Đà Nẵng"
            }
            return countryName; // adminAreaLevel1 정보가 없으면 국가명만 사용 (예: "몽골")
        }
    }

    @Transactional
    public void deleteAlbum(Long userId, Long albumId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));

        if (!album.getUser().getId().equals(userId)) {
            throw new SecurityException("앨범에 대한 삭제 권한이 없습니다.");
        }
        albumRepository.delete(album);
        log.info("앨범 '{}' (ID: {}) 삭제 완료.", album.getName(), albumId);
    }
}