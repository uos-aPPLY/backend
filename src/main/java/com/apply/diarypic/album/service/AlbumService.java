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
                .filter(albumDto -> albumDto.getDiaryCount() > 0)
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
                .sorted(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(DiaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void processDiaryAlbums(Diary diary, List<DiaryPhoto> diaryPhotos) {
        log.info("processDiaryAlbums 시작: Diary ID {}", diary.getId());
        if (diary.getDeletedAt() != null) {
            log.info("일기 ID {}는 휴지통 상태이므로 앨범 처리를 건너뜁니다. 단, 기존 연결 앨범들의 커버 업데이트 시도.", diary.getId());
            Set<Album> albumsToUpdateCover = new HashSet<>();
            diaryAlbumRepository.findByDiary(diary).forEach(da -> albumsToUpdateCover.add(da.getAlbum()));
            albumsToUpdateCover.forEach(this::updateAlbumCoverImage); // 커버만 업데이트 시도
            // 휴지통으로 갈 때 checkAndRemoveAlbumIfEmpty는 DiaryService.deleteDiary에서 호출됨
            return;
        }

        User user = diary.getUser();
        Set<String> newAlbumNamesForThisDiary = new HashSet<>();

        if (diaryPhotos != null && !diaryPhotos.isEmpty()) {
            for (DiaryPhoto photo : diaryPhotos) {
                String albumName = determineAlbumName(photo.getCountryName(), photo.getAdminAreaLevel1(), photo.getLocality());
                if (StringUtils.hasText(albumName)) {
                    newAlbumNamesForThisDiary.add(albumName);
                }
            }
        }
        log.debug("Diary ID {}: 생성/업데이트될 앨범 이름 목록: {}", diary.getId(), newAlbumNamesForThisDiary);

        List<DiaryAlbum> existingDiaryAlbums = diaryAlbumRepository.findByDiary(diary);
        Set<Album> affectedAlbums = new HashSet<>();

        // 제거할 앨범 연결
        List<DiaryAlbum> albumsLinksToRemove = existingDiaryAlbums.stream()
                .filter(da -> !newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .collect(Collectors.toList());

        if (!albumsLinksToRemove.isEmpty()) {
            albumsLinksToRemove.forEach(da -> {
                log.debug("Diary ID {}: 앨범 '{}' 연결 제거 대상 추가", diary.getId(), da.getAlbum().getName());
                affectedAlbums.add(da.getAlbum());
            });
            try {
                diaryAlbumRepository.deleteAll(albumsLinksToRemove);
                log.info("일기 ID {}에서 다음 앨범 연결 제거 완료: {}", diary.getId(), albumsLinksToRemove.stream().map(da -> da.getAlbum().getName()).collect(Collectors.toList()));
            } catch (Exception e) {
                log.error("Diary ID {}: 앨범 연결 제거 중 오류 발생", diary.getId(), e);
                // 롤백되도록 예외를 다시 던지거나, 다른 방식으로 처리
                throw new RuntimeException("앨범 연결 제거 중 오류 발생", e);
            }
        }

        Set<String> existingButNotRemovedAlbumNames = existingDiaryAlbums.stream()
                .filter(da -> newAlbumNamesForThisDiary.contains(da.getAlbum().getName())) // 삭제 대상이 아닌 기존 연결
                .map(da -> da.getAlbum().getName())
                .collect(Collectors.toSet());

        // 추가할 앨범 연결 (새로운 이름의 앨범에 대해서만)
        newAlbumNamesForThisDiary.forEach(name -> {
            if (!existingButNotRemovedAlbumNames.contains(name)) { // 기존에 유지되는 연결이 아닌, 순수하게 새로 추가될 앨범 이름
                Album album = albumRepository.findByNameAndUser(name, user)
                        .orElseGet(() -> {
                            log.info("새로운 앨범 생성: '{}' for user {}", name, user.getId());
                            Album newAlbum = Album.builder()
                                    .name(name)
                                    .user(user)
                                    .build();
                            try {
                                return albumRepository.save(newAlbum);
                            } catch (Exception e) {
                                log.error("새로운 앨범 '{}' 저장 중 오류 발생 for user {}", name, user.getId(), e);
                                throw new RuntimeException("새 앨범 저장 중 오류", e);
                            }
                        });
                affectedAlbums.add(album);

                // 중복 연결 방지 (이미 연결 로직이 복잡하므로, findByDiaryAndAlbum으로 한 번 더 확인하는 것이 안전)
                if (diaryAlbumRepository.findByDiaryAndAlbum(diary, album).isEmpty()) {
                    DiaryAlbum diaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                    try {
                        diaryAlbumRepository.save(diaryAlbum);
                        log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
                    } catch (Exception e) {
                        log.error("일기 ID {}를 앨범 '{}'에 매핑 중 오류 발생", diary.getId(), album.getName(), e);
                        throw new RuntimeException("일기-앨범 매핑 저장 중 오류", e);
                    }
                }
            }
        });

        // 기존 연결 중 유지되는 앨범도 affectedAlbums에 포함
        existingDiaryAlbums.stream()
                .filter(da -> newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .forEach(da -> affectedAlbums.add(da.getAlbum()));

        log.info("Diary ID {}: 최종적으로 영향을 받은 앨범들 (커버 업데이트 및 빈 앨범 체크 대상): {}", diary.getId(), affectedAlbums.stream().map(Album::getName).collect(Collectors.toSet()));
        affectedAlbums.forEach(album -> {
            try {
                log.debug("Diary ID {}: 앨범 '{}' 커버 업데이트 및 빈 앨범 체크 시작", diary.getId(), album.getName());
                updateAlbumCoverImage(album);
                checkAndRemoveAlbumIfEmpty(album);
                log.debug("Diary ID {}: 앨범 '{}' 커버 업데이트 및 빈 앨범 체크 완료", diary.getId(), album.getName());
            } catch (Exception e) {
                log.error("Diary ID {}: 앨범 '{}' 처리 중 오류 발생", diary.getId(), album.getName(), e);
                // 이 예외를 어떻게 처리할지 결정 필요. 롤백을 원하면 다시 던져야 함.
                // 개별 앨범 처리 실패가 전체를 롤백해야 하는가? 아니면 일부만 실패로 남길 것인가?
                // 현재는 @Transactional이므로 하나의 실패가 전체 롤백.
                throw new RuntimeException("앨범 처리 중 오류 (" + album.getName() + ")", e);
            }
        });
        log.info("processDiaryAlbums 종료: Diary ID {}", diary.getId());
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
            // if (StringUtils.hasText(adminAreaLevel1)) return countryName + " - " + adminAreaLevel1; // 해외 - 시/도 주석처리됨
            return countryName;
        }
    }

    // AlbumService.java
    @Transactional
    void updateAlbumCoverImage(Album album) {
        if (album == null) {
            log.debug("updateAlbumCoverImage: album이 null이므로 커버 이미지 업데이트를 건너뜁니다.");
            return;
        }
        log.info("앨범 ID {} ('{}')의 커버 이미지 업데이트 로직 시작...", album.getId(), album.getName());

        Optional<Diary> latestDiaryOpt = diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(d -> d.getDeletedAt() == null)
                .max(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        String newCoverImageUrl = null;

        if (latestDiaryOpt.isPresent()) {
            Diary latestDiary = latestDiaryOpt.get();
            log.info("앨범 ID {} 커버 업데이트: 최신 일기 ID {} (날짜: {}) 찾음.", album.getId(), latestDiary.getId(), latestDiary.getDiaryDate());

            // 1. 최신 일기의 사진들 중 현재 앨범명과 위치가 일치하는 사진 찾기
            if (latestDiary.getDiaryPhotos() != null && !latestDiary.getDiaryPhotos().isEmpty()) {
                Optional<DiaryPhoto> albumSpecificPhotoOpt = latestDiary.getDiaryPhotos().stream()
                        .filter(dp -> StringUtils.hasText(dp.getPhotoUrl()) &&
                                album.getName().equals(determineAlbumName(dp.getCountryName(), dp.getAdminAreaLevel1(), dp.getLocality())))
                        .min(Comparator.comparingInt(dp -> dp.getSequence() != null ? dp.getSequence() : Integer.MAX_VALUE));

                if (albumSpecificPhotoOpt.isPresent()) {
                    newCoverImageUrl = albumSpecificPhotoOpt.get().getPhotoUrl();
                    log.debug("앨범 ID {} - 앨범명 '{}'과 일치하는 사진 URL 사용: {}", album.getId(), album.getName(), newCoverImageUrl);
                }
            }

            // 2. 앨범명 일치 사진 없으면, 최신 일기의 대표 사진 사용
            if (!StringUtils.hasText(newCoverImageUrl) && StringUtils.hasText(latestDiary.getRepresentativePhotoUrl())) {
                newCoverImageUrl = latestDiary.getRepresentativePhotoUrl();
                log.debug("앨범 ID {} - 최신 일기의 대표 사진 URL 사용: {}", album.getId(), newCoverImageUrl);
            }

            // 3. 대표 사진도 없으면, 최신 일기의 첫 번째 사진 (sequence 기준) 사용
            if (!StringUtils.hasText(newCoverImageUrl) && latestDiary.getDiaryPhotos() != null && !latestDiary.getDiaryPhotos().isEmpty()) {
                Optional<DiaryPhoto> firstPhotoOpt = latestDiary.getDiaryPhotos().stream()
                        .filter(dp -> StringUtils.hasText(dp.getPhotoUrl()))
                        .min(Comparator.comparingInt(dp -> dp.getSequence() != null ? dp.getSequence() : Integer.MAX_VALUE));
                if (firstPhotoOpt.isPresent()) {
                    newCoverImageUrl = firstPhotoOpt.get().getPhotoUrl();
                    log.debug("앨범 ID {} - 최신 일기의 첫 번째 사진 URL 사용: {}", album.getId(), newCoverImageUrl);
                }
            }

            if (!StringUtils.hasText(newCoverImageUrl)) {
                log.info("앨범 ID {} - 유효한 새 커버 이미지 URL을 찾지 못함. 기존 커버 유지 정책 적용됨.", album.getId());
            } else if (album.getCoverImageUrl() == null || !newCoverImageUrl.equals(album.getCoverImageUrl())) {
                log.info("앨범 ID {} - 커버 이미지 변경 시도: 기존 '{}' -> 새 '{}'", album.getId(), album.getCoverImageUrl(), newCoverImageUrl);
                album.setCoverImageUrl(newCoverImageUrl);
                try {
                    albumRepository.save(album);
                    log.info("앨범 ID {} ('{}') 커버 이미지 업데이트 DB 저장 완료. 새 URL: {}", album.getId(), album.getName(), newCoverImageUrl);
                } catch (Exception e) {
                    log.error("앨범 ID {} ('{}') 커버 이미지 DB 저장 중 오류 발생", album.getId(), album.getName(), e);
                    throw new RuntimeException("앨범 커버 이미지 저장 중 오류", e);
                }
            } else {
                log.debug("앨범 ID {} - 커버 이미지가 이미 최신 ('{}'). 변경 없음.", album.getId(), newCoverImageUrl);
            }
        } else { // 앨범에 활성 일기가 하나도 없는 경우
            log.info("앨범 ID {} ('{}')에 활성 일기가 없어 커버 이미지를 null로 설정 시도.", album.getId(), album.getName());
            if (album.getCoverImageUrl() != null) {
                album.setCoverImageUrl(null);
                try {
                    albumRepository.save(album);
                    log.info("앨범 ID {} ('{}') 커버 이미지 null로 업데이트 DB 저장 완료 (활성 일기 없음).", album.getId(), album.getName());
                } catch (Exception e) {
                    log.error("앨범 ID {} ('{}') 커버 이미지 null로 DB 저장 중 오류 발생", album.getId(), album.getName(), e);
                    throw new RuntimeException("앨범 커버 이미지 null 설정 저장 중 오류", e);
                }
            } else {
                log.debug("앨범 ID {} - 이미 커버 이미지가 null이거나 활성 일기 없음. 변경 없음.", album.getId());
            }
        }
        log.info("앨범 ID {} ('{}')의 커버 이미지 업데이트 로직 종료.", album.getId(), album.getName());
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
        try {
            diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album)); // 연결 먼저 삭제
            albumRepository.delete(album);
            log.info("앨범 '{}' (ID: {}) 삭제 완료.", album.getName(), albumId);
        } catch (Exception e) {
            log.error("앨범 ID {} 삭제 중 오류 발생", albumId, e);
            throw new RuntimeException("앨범 삭제 중 오류", e);
        }
    }

    @Transactional
    public void checkAndRemoveAlbumIfEmpty(Album album) {
        if (album == null) {
            log.debug("checkAndRemoveAlbumIfEmpty: album이 null이므로 건너뜁니다.");
            return;
        }
        log.info("앨범 ID {} ('{}') 빈 앨범 여부 확인 시작...", album.getId(), album.getName());
        long activeDiaryCount = countActiveDiariesInAlbum(album);
        if (activeDiaryCount == 0) {
            log.info("앨범 '{}'(ID:{})에 활성 일기가 없어 삭제합니다.", album.getName(), album.getId());
            try {
                diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album)); // 연결 먼저 삭제
                albumRepository.delete(album);
                log.info("앨범 ID {} ('{}') 빈 앨범 삭제 완료.", album.getId(), album.getName());
            } catch (Exception e) {
                log.error("빈 앨범 ID {} ('{}') 삭제 중 오류 발생", album.getId(), album.getName(), e);
                throw new RuntimeException("빈 앨범 삭제 중 오류", e);
            }
        } else {
            log.info("앨범 '{}'(ID:{})에 {}개의 활성 일기가 남아있어 삭제하지 않습니다.", album.getName(), album.getId(), activeDiaryCount);
        }
        log.info("앨범 ID {} ('{}') 빈 앨범 여부 확인 종료.", album.getId(), album.getName());
    }

    @Transactional(readOnly = true)
    public long countActiveDiariesInAlbum(Album album) {
        if (album == null) return 0; // null 체크
        return diaryAlbumRepository.findByAlbum(album).stream()
                .filter(da -> da.getDiary() != null && da.getDiary().getDeletedAt() == null) // Diary null 체크 추가
                .count();
    }
}