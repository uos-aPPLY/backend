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
            diaryAlbumRepository.findByDiary(diary).forEach(da -> updateAlbumCoverImage(da.getAlbum()));
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
        Set<Album> affectedAlbums = new HashSet<>();

        List<DiaryAlbum> albumsLinksToRemove = existingDiaryAlbums.stream()
                .filter(da -> !newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .collect(Collectors.toList());

        if (!albumsLinksToRemove.isEmpty()) {
            albumsLinksToRemove.forEach(da -> affectedAlbums.add(da.getAlbum()));
            diaryAlbumRepository.deleteAll(albumsLinksToRemove);
            log.info("일기 ID {}에서 다음 앨범 연결 제거: {}", diary.getId(), albumsLinksToRemove.stream().map(da -> da.getAlbum().getName()).collect(Collectors.toList()));
        }

        Set<String> existingAlbumNames = existingDiaryAlbums.stream()
                .map(da -> da.getAlbum().getName())
                .collect(Collectors.toSet());

        // 추가할 앨범 연결
        newAlbumNamesForThisDiary.forEach(name -> {

            Album album = albumRepository.findByNameAndUser(name, user)
                    .orElseGet(() -> {
                        log.info("새로운 앨범 생성 시도 (processDiaryAlbums): '{}' for user {}", name, user.getId());
                        Album newAlbum = Album.builder()
                                .name(name)
                                .user(user)
                                .build();
                        return albumRepository.save(newAlbum);
                    });
            affectedAlbums.add(album);

            if (existingDiaryAlbums.stream().noneMatch(da -> da.getAlbum().getId().equals(album.getId()))) {
                DiaryAlbum diaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                diaryAlbumRepository.save(diaryAlbum);
                log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
            }
        });

        existingDiaryAlbums.stream()
                .filter(da -> newAlbumNamesForThisDiary.contains(da.getAlbum().getName()))
                .forEach(da -> affectedAlbums.add(da.getAlbum()));

        affectedAlbums.forEach(album -> {
            updateAlbumCoverImage(album);
            checkAndRemoveAlbumIfEmpty(album);
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

    /**
     * 특정 앨범의 커버 이미지를 해당 앨범의 가장 최근 활성 일기의 대표 사진으로 업데이트합니다.
     * 만약 대표 사진이 없으면, 그 일기의 첫 번째 사진을 사용합니다.
     * 활성 일기가 없거나 사진이 전혀 없으면 커버 이미지를 null로 설정합니다.
     */
    @Transactional
    void updateAlbumCoverImage(Album album) {
        if (album == null) {
            log.debug("updateAlbumCoverImage: album이 null이므로 커버 이미지 업데이트를 건너뜁니다.");
            return;
        }
        log.info("앨범 ID {} ('{}')의 커버 이미지 업데이트 시작...", album.getId(), album.getName());

        Optional<Diary> latestDiaryOpt = diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(d -> d.getDeletedAt() == null)
                .max(Comparator.comparing(Diary::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder())) // 수정된 정렬
                        .thenComparing(Diary::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        if (latestDiaryOpt.isPresent()) {
            Diary latestDiary = latestDiaryOpt.get();
            log.info("앨범 ID {} 커버 이미지 업데이트: 최신 일기 ID {} (날짜: {}) 찾음.", album.getId(), latestDiary.getId(), latestDiary.getDiaryDate());
            String newCoverImageUrl = latestDiary.getRepresentativePhotoUrl();
            log.debug("앨범 ID {} - 최신 일기의 대표 사진 URL: {}", album.getId(), newCoverImageUrl);

            if (!StringUtils.hasText(newCoverImageUrl) && latestDiary.getDiaryPhotos() != null && !latestDiary.getDiaryPhotos().isEmpty()) {
                Optional<DiaryPhoto> firstPhotoOpt = latestDiary.getDiaryPhotos().stream()
                        .filter(dp -> StringUtils.hasText(dp.getPhotoUrl()))
                        .min(Comparator.comparingInt(dp -> dp.getSequence() != null ? dp.getSequence() : Integer.MAX_VALUE));
                if (firstPhotoOpt.isPresent()) {
                    newCoverImageUrl = firstPhotoOpt.get().getPhotoUrl();
                    log.debug("앨범 ID {} - 대표 사진 없음. 최신 일기의 첫 번째 사진 URL 사용: {}", album.getId(), newCoverImageUrl);
                } else {
                    log.debug("앨범 ID {} - 최신 일기에 유효한 사진이 없음 (대표/일반 모두).", album.getId());
                }
            }

            if (!StringUtils.hasText(newCoverImageUrl)) {
                log.info("앨범 ID {} - 유효한 새 커버 이미지 URL을 찾지 못함. 기존 커버 유지.", album.getId());
                // 기존 커버 유지 (아무 작업 안 함)
            } else if (album.getCoverImageUrl() == null || !newCoverImageUrl.equals(album.getCoverImageUrl())) {
                log.info("앨범 ID {} - 커버 이미지 변경 시도: 기존 '{}' -> 새 '{}'", album.getId(), album.getCoverImageUrl(), newCoverImageUrl);
                album.setCoverImageUrl(newCoverImageUrl);
                albumRepository.save(album);
                log.info("앨범 ID {} ('{}') 커버 이미지 업데이트 완료. 새 URL: {}", album.getId(), album.getName(), newCoverImageUrl);
            } else {
                log.debug("앨범 ID {} - 커버 이미지가 이미 최신 ('{}'). 변경 없음.", album.getId(), newCoverImageUrl);
            }
        } else {
            log.info("앨범 ID {} ('{}')에 활성 일기가 없어 커버 이미지를 null로 설정 시도.", album.getId(), album.getName());
            if (album.getCoverImageUrl() != null) {
                album.setCoverImageUrl(null);
                albumRepository.save(album);
                log.info("앨범 ID {} ('{}') 커버 이미지 null로 업데이트 완료 (활성 일기 없음).", album.getId(), album.getName());
            } else {
                log.debug("앨범 ID {} - 이미 커버 이미지가 null이거나 활성 일기 없음. 변경 없음.", album.getId());
            }
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
        diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album));
        albumRepository.delete(album);
        log.info("앨범 '{}' (ID: {}) 삭제 완료.", album.getName(), albumId);
    }

    @Transactional
    public void checkAndRemoveAlbumIfEmpty(Album album) {
        if (album == null) return;
        long activeDiaryCount = countActiveDiariesInAlbum(album);
        if (activeDiaryCount == 0) {
            log.info("앨범 '{}'(ID:{})에 활성 일기가 없어 삭제합니다.", album.getName(), album.getId());
            // DiaryAlbum 연결은 이미 위에서 처리되었거나, Album 엔티티의 Cascade 설정으로 처리될 수 있음
            // 명시적으로 여기서 한 번 더 DiaryAlbum 연결을 삭제하는 것이 안전할 수 있음
            diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album));
            albumRepository.delete(album);
        } else {
            log.debug("앨범 '{}'(ID:{})에 {}개의 활성 일기가 남아있습니다.", album.getName(), album.getId(), activeDiaryCount);
            // 일기가 남아있다면, 커버 이미지 업데이트 로직 호출 (checkAndRemoveAlbumIfEmpty 호출 전에 updateAlbumCoverImage가 먼저 호출되도록 변경)
            // updateAlbumCoverImage(album); // checkAndRemoveAlbumIfEmpty 호출 전에 커버가 업데이트 되도록 processDiaryAlbums에서 순서 조정
        }
    }

    @Transactional(readOnly = true)
    public long countActiveDiariesInAlbum(Album album) {
        return diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(diary -> diary.getDeletedAt() == null)
                .count();
    }
}