package com.apply.diarypic.terms.dto;

import com.apply.diarypic.terms.entity.Terms;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsDto {
    private Long id;
    private String termsType; // Enum의 name() 값 또는 description() 값을 저장
    private String title;
    private String content;
    private int version;
    private boolean required;
    private LocalDateTime effectiveDate;
    private Boolean agreed;

    public static TermsDto fromEntity(Terms terms) {
        return TermsDto.builder()
                .id(terms.getId())
                .termsType(terms.getTermsType().name())
                .title(terms.getTitle())
                .content(terms.getContent())
                .version(terms.getVersion())
                .required(terms.isRequired())
                .effectiveDate(terms.getEffectiveDate())
                .build();
    }
}