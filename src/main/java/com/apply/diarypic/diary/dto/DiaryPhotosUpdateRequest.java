package com.apply.diarypic.diary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size; // import 수정
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DiaryPhotosUpdateRequest {

    @Valid
    @Size(max = 9, message = "사진은 최대 9장까지 등록 가능합니다.")
    private List<PhotoAssignmentDto> photos;

    private Long newRepresentativePhotoId;
}