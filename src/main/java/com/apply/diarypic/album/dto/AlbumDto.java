package com.apply.diarypic.album.dto;

import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.diary.dto.DiaryResponse; // 앨범 내 일기 목록 표현 시 필요할 수 있음
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlbumDto {
    private Long id;
    private String name;
    private String coverImageUrl;
    private LocalDateTime createdAt;
    private int diaryCount; // 앨범에 포함된 일기 수 (선택적)
    // private List<DiaryResponse> diaries; // 앨범 상세 조회 시 일기 목록 포함 가능

    public static AlbumDto fromEntity(Album album, int diaryCount) {
        return AlbumDto.builder()
                .id(album.getId())
                .name(album.getName())
                .coverImageUrl(album.getCoverImageUrl())
                .createdAt(album.getCreatedAt())
                .diaryCount(diaryCount)
                .build();
    }

    public static AlbumDto fromEntity(Album album) { // diaryCount 없이
        return AlbumDto.builder()
                .id(album.getId())
                .name(album.getName())
                .coverImageUrl(album.getCoverImageUrl())
                .createdAt(album.getCreatedAt())
                .diaryCount(album.getDiaryAlbums() != null ? album.getDiaryAlbums().size() : 0) // 엔티티에서 직접 계산
                .build();
    }
}