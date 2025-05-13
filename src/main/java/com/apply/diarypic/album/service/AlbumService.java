package com.apply.diarypic.album.service;

import com.apply.diarypic.album.dto.AlbumDto;
import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.album.entity.DiaryAlbum;
import com.apply.diarypic.album.repository.AlbumRepository;
import com.apply.diarypic.album.repository.DiaryAlbumRepository;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    public List<AlbumDto> getUserAlbums(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return albumRepository.findByUserOrderByCreatedAtDesc(user).stream()
                // 앨범 내 일기 수를 계산하여 DTO 생성
                .map(album -> {
                    long activeDiaryCount = album.getDiaryAlbums().stream()
                            .map(DiaryAlbum::getDiary)
                            .filter(diary -> diary.getDeletedAt() == null)
                            .count();
                    return AlbumDto.fromEntity(album, (int) activeDiaryCount);
                })
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
                .filter(diary -> diary.getDeletedAt() == null)
                .sorted(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder())) // 날짜 최신순 정렬
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(DiaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void processDiaryAlbums(Diary diary, List<DiaryPhoto> diaryPhotos) {
        if (diary.getDeletedAt() != null) {
            log.info("일기 ID {}는 휴지통 상태이므로 앨범 처리를 건너뜁니다.", diary.getId());
            return;
        }

        if (diaryPhotos == null || diaryPhotos.isEmpty()) {
            log.info("일기 ID {}에 사진이 없어 앨범 처리를 진행하지 않습니다.", diary.getId());
            return;
        }

        User user = diary.getUser();
        Set<String> newAlbumNamesForThisDiary = new HashSet<>();

        for (DiaryPhoto photo : diaryPhotos) {
            String albumName = determineAlbumName(photo.getCountryName(), photo.getAdminAreaLevel1(), photo.getLocality());
            if (StringUtils.hasText(albumName)) {
                newAlbumNamesForThisDiary.add(albumName);
            }
        }

        // 기존 앨범 연결 조회
        List<DiaryAlbum> existingDiaryAlbums = diaryAlbumRepository.findByDiary(diary);
        Set<String> existingAlbumNames = existingDiaryAlbums.stream()
                .map(da -> da.getAlbum().getName())
                .collect(Collectors.toSet());

        // 제거할 앨범 연결 (기존 O, 신규 X)
        List<DiaryAlbum> albumsToRemove = existingDiaryAlbums.stream()
                .filter(da -> !newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .collect(Collectors.toList());

        if (!albumsToRemove.isEmpty()) {
            diaryAlbumRepository.deleteAll(albumsToRemove);
            log.info("일기 ID {}에서 다음 앨범 연결 제거: {}", diary.getId(), albumsToRemove.stream().map(da->da.getAlbum().getName()).collect(Collectors.toList()));
        }

        // 추가할 앨범 연결 (기존 X, 신규 O)
        newAlbumNamesForThisDiary.forEach(name -> {
            if (!existingAlbumNames.contains(name)) { // 기존에 연결되지 않은 앨범만 처리
                Album album = albumRepository.findByNameAndUser(name, user)
                        .orElseGet(() -> {
                            log.info("새로운 앨범 생성: '{}' for user {}", name, user.getId());
                            // 새 앨범의 커버 이미지는 이 일기의 첫번째 사진으로 설정
                            String coverImageUrl = diaryPhotos.stream()
                                    .map(DiaryPhoto::getPhotoUrl)
                                    .filter(StringUtils::hasText)
                                    .findFirst()
                                    .orElse(null);
                            Album newAlbum = Album.builder()
                                    .name(name)
                                    .user(user)
                                    .coverImageUrl(coverImageUrl)
                                    .build();
                            return albumRepository.save(newAlbum);
                        });

                DiaryAlbum diaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                diaryAlbumRepository.save(diaryAlbum);
                log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
            }
        });
    }

    private String determineAlbumName(String countryName, String adminAreaLevel1, String locality) {
        if (!StringUtils.hasText(countryName)) {
            return "기타 장소";
        }
        if ("대한민국".equals(countryName)) {
            if (StringUtils.hasText(locality)) return locality;
            if (StringUtils.hasText(adminAreaLevel1)) return adminAreaLevel1;
            return countryName;
        } else {
            if (StringUtils.hasText(adminAreaLevel1)) return countryName + " - " + adminAreaLevel1;
            return countryName;
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
        // DiaryAlbum 연결은 Album 엔티티의 @OneToMany(cascade=CascadeType.ALL, orphanRemoval=true) 설정으로 자동 처리
        albumRepository.delete(album);
        log.info("앨범 '{}' (ID: {}) 삭제 완료.", album.getName(), albumId);
    }
}