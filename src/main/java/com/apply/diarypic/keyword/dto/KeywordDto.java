package com.apply.diarypic.keyword.dto;

import com.apply.diarypic.keyword.entity.Keyword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordDto {
    private Long id;
    private String name;

    public static KeywordDto fromEntity(Keyword keyword) {
        return KeywordDto.builder()
                .id(keyword.getId())
                .name(keyword.getName())
                .build();
    }
}