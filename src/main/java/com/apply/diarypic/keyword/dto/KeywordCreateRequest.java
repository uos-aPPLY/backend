package com.apply.diarypic.keyword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KeywordCreateRequest {
    @NotBlank(message = "키워드 이름은 비워둘 수 없습니다.")
    @Size(max = 50, message = "키워드 이름은 최대 50자까지 가능합니다.")
    private String name;
}