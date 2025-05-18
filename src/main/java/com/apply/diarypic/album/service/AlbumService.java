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

        newAlbumNamesForThisDiary.forEach(name -> {
            if (!existingAlbumNames.contains(name) || albumsLinksToRemove.stream().anyMatch(da -> da.getAlbum().getName().equals(name))) {
                Album album = albumRepository.findByNameAndUser(name, user)
                        .orElseGet(() -> {
                            log.info("새로운 앨범 생성: '{}' for user {}", name, user.getId());
                            String coverImageUrl = (diaryPhotos != null && !diaryPhotos.isEmpty()) ?
                                    diaryPhotos.get(0).getPhotoUrl() : null;
                            Album newAlbum = Album.builder()
                                    .name(name)
                                    .user(user)
                                    .coverImageUrl(coverImageUrl)
                                    .build();
                            return albumRepository.save(newAlbum);
                        });
                affectedAlbums.add(album);

                if (diaryAlbumRepository.findByDiaryAndAlbum(diary, album).isEmpty()) {
                    DiaryAlbum diaryAlbum = DiaryAlbum.builder().diary(diary).album(album).build();
                    diaryAlbumRepository.save(diaryAlbum);
                    log.info("일기 ID {}를 앨범 '{}'(ID:{})에 매핑 완료.", diary.getId(), album.getName(), album.getId());
                }
            } else {
                existingDiaryAlbums.stream()
                        .filter(da -> da.getAlbum().getName().equals(name))
                        .findFirst()
                        .ifPresent(da -> affectedAlbums.add(da.getAlbum()));
            }
        });

        affectedAlbums.forEach(this::checkAndRemoveAlbumIfEmpty);
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
            diaryAlbumRepository.deleteAll(diaryAlbumRepository.findByAlbum(album));
            albumRepository.delete(album);
        } else {
            log.debug("앨범 '{}'(ID:{})에 {}개의 활성 일기가 남아있습니다.", album.getName(), album.getId(), activeDiaryCount);
        }
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public long countActiveDiariesInAlbum(Album album) {
        return diaryAlbumRepository.findByAlbum(album).stream()
                .map(DiaryAlbum::getDiary)
                .filter(diary -> diary.getDeletedAt() == null)
                .count();
    }
}