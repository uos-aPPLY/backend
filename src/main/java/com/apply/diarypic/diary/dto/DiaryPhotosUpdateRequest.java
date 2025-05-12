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

    // 일기에 포함될 최종 사진 목록 및 순서. 비어있는 리스트를 보내면 모든 사진이 삭제됩니다.
    @Valid
    @Size(max = 9, message = "사진은 최대 9장까지 등록 가능합니다.") // 일기 생성 시 제약조건과 동일하게
    private List<PhotoAssignmentDto> photos;

    // 새로운 대표 사진 ID (선택 사항). photos 리스트에 포함된 사진 ID여야 합니다.
    private Long newRepresentativePhotoId;
}