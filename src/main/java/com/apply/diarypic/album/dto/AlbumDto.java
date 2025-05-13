package com.apply.diarypic.album.dto;

import com.apply.diarypic.album.entity.Album;
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
    private int diaryCount;

    public static AlbumDto fromEntity(Album album, int diaryCount) {
        return AlbumDto.builder()
                .id(album.getId())
                .name(album.getName())
                .coverImageUrl(album.getCoverImageUrl())
                .createdAt(album.getCreatedAt())
                .diaryCount(diaryCount)
                .build();
    }
}