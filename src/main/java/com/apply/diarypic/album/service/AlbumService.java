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
        if (diary.getDeletedAt() != null) {
            log.info("일기 ID {}는 휴지통 상태이므로 앨범 처리를 건너뜁니다.", diary.getId());
            // 소프트 삭제 시, 해당 일기가 속했던 앨범들의 커버 이미지 업데이트 필요
            diaryAlbumRepository.findByDiary(diary).forEach(da -> updateAlbumCoverImage(da.getAlbum())); // <--- 이 부분 중요
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

        List<DiaryAlbum> existingDiaryAlbums = diaryAlbumRepository.findByDiary(diary);
        Set<Album> affectedAlbums = new HashSet<>(); // 변경이 발생한 앨범들 추적

        // 제거할 앨범 연결
        List<DiaryAlbum> albumsLinksToRemove = existingDiaryAlbums.stream()
                .filter(da -> !newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .collect(Collectors.toList());

        if (!albumsLinksToRemove.isEmpty()) {
            albumsLinksToRemove.forEach(da -> affectedAlbums.add(da.getAlbum()));
            diaryAlbumRepository.deleteAll(albumsLinksToRemove);
            log.info("일기 ID {}에서 다음 앨범 연결 제거: {}", diary.getId(), albumsLinksToRemove.stream().map(da -> da.getAlbum().getName()).collect(Collectors.toList()));
        }

        Set<String> existingAlbumNames = existingDiaryAlbums.stream() // 이전에 연결된 앨범 이름들
                .map(da -> da.getAlbum().getName())
                .collect(Collectors.toSet());

        // 추가할 앨범 연결
        newAlbumNamesForThisDiary.forEach(name -> {
            // Album album = albumRepository.findByNameAndUser(name, user) // findByNameAndUser로 찾고 orElseGet으로 생성
            //         .orElseGet(() -> { // ... 새 앨범 생성 로직 ... });
            // 위 로직은 이미 제공해주신 코드에 잘 반영되어 있음.
            // 중요한 것은 affectedAlbums에 이 album을 추가하는 것.

            // 수정된 로직 (앨범을 가져오거나 생성하고 affectedAlbums에 추가)
            Album album = albumRepository.findByNameAndUser(name, user)
                    .orElseGet(() -> {
                        log.info("새로운 앨범 생성 시도 (processDiaryAlbums): '{}' for user {}", name, user.getId());
                        // 새 앨범의 커버 이미지는 updateAlbumCoverImage에서 설정됨
                        Album newAlbum = Album.builder()
                                .name(name)
                                .user(user)
                                // .coverImageUrl(null) // 초기에는 null 또는 첫번째 사진으로 설정 가능하나, updateAlbumCoverImage에서 최종 결정
                                .build();
                        return albumRepository.save(newAlbum);
                    });
            affectedAlbums.add(album); // 생성되거나 찾아진 앨범을 affectedAlbums에 추가

            // 이미 연결되어 있지 않다면 새로 연결 (기존 코드와 유사)
            // if (existingDiaryAlbums.stream().noneMatch(da -> da.getAlbum().getId().equals(album.getId()))) {
            // 위 조건은 이미 제거된 연결을 고려하지 못하므로, diaryAlbumRepository.findByDiaryAndAlbum 사용이 더 정확
            if (diaryAlbumRepository.findByDiaryAndAlbum(diary, album).isEmpty()) { // 수정: findByDiaryAndAlbum은 Optional을 반환
                DiaryAlbum diaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                diaryAlbumRepository.save(diaryAlbum);
                log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
            }
        });

        // 기존 연결 중 유지되는 앨범도 affectedAlbums에 포함 (커버 이미지 업데이트 대상)
        existingDiaryAlbums.stream()
                .filter(da -> newAlbumNamesForThisDiary.contains(da.getAlbum().getName())) // 새 앨범 목록에도 여전히 존재하는 연결
                .forEach(da -> affectedAlbums.add(da.getAlbum())); // 해당 앨범을 affectedAlbums에 추가

        // 영향을 받은 모든 앨범에 대해 커버 이미지 업데이트 및 빈 앨범 체크
        affectedAlbums.forEach(album -> {
            updateAlbumCoverImage(album); // 커버 이미지 먼저 업데이트
            checkAndRemoveAlbumIfEmpty(album); // 그 다음 빈 앨범인지 체크
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
            // if (StringUtils.hasText(adminAreaLevel1)) return countryName + " - " + adminAreaLevel1; // 해외 - 시/도 주석처리됨
            return countryName;
        }
    }

    @Transactional
    void updateAlbumCoverImage(Album album) { // 접근 제어자는 package-private 또는 public
        if (album == null) {
            log.debug("updateAlbumCoverImage: album이 null이므로 커버 이미지 업데이트를 건너뜁니다.");
            return;
        }
        log.info("앨범 ID {} ('{}')의 커버 이미지 업데이트 로직 시작...", album.getId(), album.getName());

        Optional<Diary> latestDiaryOpt = diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(d -> d != null && d.getDeletedAt() == null) // Diary null 체크 및 활성 일기만
                .max(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder())) // 수정: 날짜 내림차순 (최신순)
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))); // 수정: 생성시간 내림차순 (최신순)

        String newCoverImageUrl = null; // 여기서 초기화

        if (latestDiaryOpt.isPresent()) {
            Diary latestDiary = latestDiaryOpt.get();
            log.info("앨범 ID {} 커버 업데이트: 최신 일기 ID {} (날짜: {}) 찾음.", album.getId(), latestDiary.getId(), latestDiary.getDiaryDate());

            // 1. 최신 일기의 사진들 중 현재 앨범명과 위치가 일치하는 사진 찾기 (새로운 로직)
            if (latestDiary.getDiaryPhotos() != null && !latestDiary.getDiaryPhotos().isEmpty()) {
                Optional<DiaryPhoto> albumSpecificPhotoOpt = latestDiary.getDiaryPhotos().stream()
                        .filter(dp -> StringUtils.hasText(dp.getPhotoUrl()) &&
                                album.getName().equals(determineAlbumName(dp.getCountryName(), dp.getAdminAreaLevel1(), dp.getLocality())))
                        .min(Comparator.comparingInt(dp -> dp.getSequence() != null ? dp.getSequence() : Integer.MAX_VALUE)); // sequence 오름차순 (낮은 순)

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

            // newCoverImageUrl 결정 후 업데이트 로직
            if (StringUtils.hasText(newCoverImageUrl)) {
                if (album.getCoverImageUrl() == null || !newCoverImageUrl.equals(album.getCoverImageUrl())) {
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
            } else {
                // 유효한 새 커버 이미지를 찾지 못한 경우 (최신 일기에 사진이 아예 없거나, 대표 사진도 없는 경우)
                // 이전 요청: "기존 커버 이미지를 유지하고 로그는 불필요"
                // 단, 만약 이전에 커버가 있었는데 이제는 아예 설정할 사진이 없다면 null로 만들어야 할 수도 있음.
                // 여기서는 "찾지 못하면 기존 커버 유지" 정책을 따르므로, 아무 작업도 하지 않습니다.
                // (단, 앨범에 활성 일기가 아예 없는 경우는 아래 else 블록에서 처리)
                log.info("앨범 ID {} - 유효한 새 커버 이미지 URL을 찾지 못함. 기존 커버 유지.", album.getId());
            }
        } else { // 앨범에 활성 일기가 하나도 없는 경우
            log.info("앨범 ID {} ('{}')에 활성 일기가 없어 커버 이미지를 null로 설정 시도.", album.getId(), album.getName());
            if (album.getCoverImageUrl() != null) { // 기존 커버 이미지가 있었다면 null로 업데이트
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