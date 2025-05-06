package com.apply.diarypic.diary.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "diary_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiaryPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // S3에 저장된 사진 URL
    @Column(nullable = false)
    private String photoUrl;

    // 촬영일 (날짜만 필요할 경우)
    private LocalDateTime shootingDateTime;

    // 촬영 장소 정보
    private String location;

    // AI 추천 사진 여부
    private Boolean isRecommended;

    // 사진의 순서 (최종 선택된 사진의 순서를 나타냄)
    @Setter
    private Integer sequence;

    // 사진이 업로드된 시각
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 사진을 업로드한 사용자 ID
    @Column(nullable = false)
    private Long userId;

    // 연관관계 설정을 위한 setter (다른 필드는 setter 없이 Builder 혹은 생성자를 사용)
    // 해당 사진이 연결된 일기 (임시 사진은 null)
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id")
    private Diary diary;

}
