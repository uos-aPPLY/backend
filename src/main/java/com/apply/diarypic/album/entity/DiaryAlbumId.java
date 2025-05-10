package com.apply.diarypic.album.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DiaryAlbumId implements Serializable {
    private Long diary; // Diary 엔티티의 id 필드명
    private Long album; // Album 엔티티의 id 필드명
}