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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final DiaryAlbumRepository diaryAlbumRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AlbumDto> getUserAlbums(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return albumRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(album -> {
                    long activeDiaryCount = countActiveDiariesInAlbum(album);
                    return AlbumDto.fromEntity(album, (int) activeDiaryCount);
                })
                .filter(albumDto -> albumDto.getDiaryCount() > 0) // 활성 일기가 있는 앨범만 필터링
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
                .filter(diary -> diary != null && diary.getDeletedAt() == null) // null 체크 및 활성 일기 필터링
                .sorted(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(DiaryResponse::from) // DiaryResponse DTO 사용
                .collect(Collectors.toList());
    }

    @Transactional
    public void processDiaryAlbums(Diary diary, List<DiaryPhoto> diaryPhotos) {
        if (diary.getDeletedAt() != null) {
            log.info("일기 ID {}는 휴지통 상태이므로 앨범 처리를 건너뜁니다.", diary.getId());
            // 일기가 휴지통으로 이동된 경우, 해당 일기가 포함되어 있던 앨범들의 커버 이미지 업데이트 및 빈 앨범인지 확인 필요
            Set<Album> affectedAlbumsFromDeletedDiary = diaryAlbumRepository.findByDiary(diary).stream()
                    .map(DiaryAlbum::getAlbum)
                    .collect(Collectors.toSet());
            // 일기와 앨범 연결 정보 우선 삭제
            diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByDiary(diary));

            affectedAlbumsFromDeletedDiary.forEach(album -> {
                updateAlbumCoverImage(album); // 최신 일기가 변경되었을 수 있으므로 커버 업데이트
                checkAndRemoveAlbumIfEmpty(album); // 앨범이 비었는지 확인
            });
            return;
        }

        User user = diary.getUser();
        Set<String> newAlbumNamesForThisDiary = new HashSet<>();

        // 현재 일기의 사진들로부터 앨범 이름 결정
        if (diaryPhotos != null && !diaryPhotos.isEmpty()) {
            for (DiaryPhoto photo : diaryPhotos) {
                if (photo != null) { // 사진 객체 null 체크
                    String albumName = determineAlbumName(photo.getCountryName(), photo.getAdminAreaLevel1(), photo.getLocality());
                    if (StringUtils.hasText(albumName)) {
                        newAlbumNamesForThisDiary.add(albumName);
                    }
                }
            }
        }

        List<DiaryAlbum> existingDiaryAlbumLinks = diaryAlbumRepository.findByDiary(diary);
        Set<Album> albumsCurrentlyAssociatedWithDiary = existingDiaryAlbumLinks.stream()
                .map(DiaryAlbum::getAlbum)
                .collect(Collectors.toSet());
        // 영향을 받는 앨범들 (기존 연결 앨범 + 새로 연결될 앨범 + 연결 해제될 앨범)
        Set<Album> affectedAlbums = new HashSet<>(albumsCurrentlyAssociatedWithDiary);

        // 일기가 더 이상 속하지 않아야 할 앨범들에서 연결 제거
        List<DiaryAlbum> linksToRemove = existingDiaryAlbumLinks.stream()
                .filter(da -> !newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .collect(Collectors.toList());

        if (!linksToRemove.isEmpty()) {
            diaryAlbumRepository.deleteAll(linksToRemove); // deleteAllInBatch 고려 가능
            log.info("일기 ID {}에서 다음 앨범 연결 제거: {}", diary.getId(),
                    linksToRemove.stream().map(da -> da.getAlbum().getName()).collect(Collectors.toList()));
        }

        // 새로운 앨범에 추가하거나, 해당되는 기존 앨범에 연결 보장
        for (String targetAlbumName : newAlbumNamesForThisDiary) {
            Album album = albumRepository.findByNameAndUser(targetAlbumName, user)
                    .orElseGet(() -> {
                        log.info("새로운 앨범 생성 (processDiaryAlbums): '{}' for user {}", targetAlbumName, user.getId());
                        Album newAlbum = Album.builder()
                                .name(targetAlbumName)
                                .user(user)
                                // 커버 이미지는 아래 updateAlbumCoverImage에서 설정/업데이트
                                .build();
                        return albumRepository.save(newAlbum);
                    });
            affectedAlbums.add(album); // 이 앨범은 영향을 받음

            // 해당 일기가 이 앨범에 이미 연결되어 있는지 확인 (findByDiaryAndAlbum 사용)
            if (diaryAlbumRepository.findByDiaryAndAlbum(diary, album).isEmpty()) {
                DiaryAlbum newDiaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                diaryAlbumRepository.save(newDiaryAlbum);
                log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
            }
        }

        // 모든 영향을 받은 앨범들에 대해 커버 이미지 업데이트 및 빈 앨범 여부 확인
        affectedAlbums.forEach(album -> {
            updateAlbumCoverImage(album);
            checkAndRemoveAlbumIfEmpty(album);
        });
    }

    private String determineAlbumName(String countryName, String adminAreaLevel1, String locality) {
        if (!StringUtils.hasText(countryName)) {
            return "기타 장소"; // 위치 정보 없는 경우 기본 앨범명
        }
        if ("대한민국".equals(countryName)) {
            if (StringUtils.hasText(locality)) return locality;
            if (StringUtils.hasText(adminAreaLevel1)) return adminAreaLevel1;
            return countryName; // "대한민국"
        } else {
            return countryName; // 해외는 국가명만 사용 (기존 로직 유지)
        }
    }

    @Transactional
    void updateAlbumCoverImage(Album album) {
        if (album == null) {
            log.warn("updateAlbumCoverImage: album 객체가 null이므로 커버 이미지 업데이트를 건너뜁니다.");
            return;
        }
        log.info("앨범 ID {} ('{}')의 커버 이미지 업데이트 시작...", album.getId(), album.getName());

        Optional<Diary> latestDiaryOpt = diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(d -> d != null && d.getDeletedAt() == null) // Diary 객체 null 체크 및 활성 일기 필터링
                .max(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        String newCoverImageUrl = null;

        if (latestDiaryOpt.isPresent()) {
            Diary latestDiary = latestDiaryOpt.get();
            log.info("앨범 ID {} 커버 이미지 업데이트: 최신 일기 ID {} (날짜: {}) 찾음.", album.getId(), latestDiary.getId(), latestDiary.getDiaryDate());

            if (StringUtils.hasText(latestDiary.getRepresentativePhotoUrl())) {
                newCoverImageUrl = latestDiary.getRepresentativePhotoUrl();
                log.debug("앨범 ID {} - 최신 일기의 대표 사진 URL 사용: {}", album.getId(), newCoverImageUrl);
            } else {
                log.debug("앨범 ID {} - 최신 일기에 대표 사진 URL 없음. 일기 사진 목록에서 검색 시도.", album.getId());
                if (latestDiary.getDiaryPhotos() != null && !latestDiary.getDiaryPhotos().isEmpty()) {
                    Optional<DiaryPhoto> firstPhotoOpt = latestDiary.getDiaryPhotos().stream()
                            .filter(dp -> dp != null && StringUtils.hasText(dp.getPhotoUrl()))
                            .min(Comparator.comparingInt(dp -> dp.getSequence() != null ? dp.getSequence() : Integer.MAX_VALUE));

                    if (firstPhotoOpt.isPresent()) {
                        newCoverImageUrl = firstPhotoOpt.get().getPhotoUrl();
                        log.debug("앨범 ID {} - 최신 일기의 사진 목록에서 첫 번째 사진 URL 사용 (sequence {}): {}",
                                album.getId(), firstPhotoOpt.get().getSequence(), newCoverImageUrl);
                    } else {
                        log.debug("앨범 ID {} - 최신 일기에 유효한 사진이 없음 (URL 없거나 목록 비었음).", album.getId());
                    }
                } else {
                    log.debug("앨범 ID {} - 최신 일기에 DiaryPhotos 목록이 null이거나 비어있음.", album.getId());
                }
            }
        } else {
            log.info("앨범 ID {} ('{}')에 활성 일기가 없어 커버 이미지를 null로 설정합니다.", album.getId(), album.getName());
        }

        String currentCoverImageUrl = album.getCoverImageUrl();
        if (!StringUtils.pathEquals(currentCoverImageUrl, newCoverImageUrl)) {
            log.info("앨범 ID {} - 커버 이미지 변경: 기존 '{}' -> 새 '{}'", album.getId(), currentCoverImageUrl, newCoverImageUrl);
            album.setCoverImageUrl(newCoverImageUrl);
            albumRepository.save(album);
            log.info("앨범 ID {} ('{}') 커버 이미지 업데이트 완료. 새 URL: {}", album.getId(), album.getName(), newCoverImageUrl);
        } else {
            log.debug("앨범 ID {} - 커버 이미지가 이미 최신 ('{}')이거나 변경 사항 없음. 업데이트 불필요.", album.getId(), newCoverImageUrl);
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
        // DiaryAlbum 엔티티에 CascadeType.ALL, orphanRemoval = true가 Album의 diaryAlbums에 설정되어 있으므로,
        // albumRepository.delete(album) 호출 시 연결된 DiaryAlbum도 삭제될 것으로 기대할 수 있습니다.
        // 명시적으로 diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album))를 호출해도 안전합니다.
        // 여기서는 Album 엔티티의 cascade 설정에 의존하거나, 명시적 삭제를 선택할 수 있습니다.
        // 안전을 위해 명시적으로 DiaryAlbum 먼저 삭제하는 것을 유지하거나, cascade 설정을 신뢰한다면 아래 라인 생략 가능.
        diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album)); // 명시적 삭제
        albumRepository.delete(album);
        log.info("앨범 '{}' (ID: {}) 삭제 완료.", album.getName(), albumId);
    }

    @Transactional
    public void checkAndRemoveAlbumIfEmpty(Album album) {
        if (album == null) {
            log.warn("checkAndRemoveAlbumIfEmpty: album 객체가 null이므로 처리를 건너뜁니다.");
            return;
        }
        long activeDiaryCount = countActiveDiariesInAlbum(album);
        if (activeDiaryCount == 0) {
            log.info("앨범 '{}'(ID:{})에 활성 일기가 없어 삭제합니다.", album.getName(), album.getId());
            // deleteAlbum 메소드와 마찬가지로 cascade 설정 또는 명시적 삭제 선택
            diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album)); // 명시적 삭제
            albumRepository.delete(album);
            log.info("앨범 '{}'(ID:{}) 삭제 완료.", album.getName(), album.getId());
        } else {
            log.debug("앨범 '{}'(ID:{})에 {}개의 활성 일기가 남아있습니다. 삭제하지 않습니다.", album.getName(), album.getId(), activeDiaryCount);
        }
    }

    @Transactional(readOnly = true)
    public long countActiveDiariesInAlbum(Album album) {
        if (album == null) return 0;
        return diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(diary -> diary != null && diary.getDeletedAt() == null) // Diary 객체 null 체크
                .count();
    }
}