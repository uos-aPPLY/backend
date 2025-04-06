package com.apply.diarypic.diary.entity;

import com.apply.diarypic.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "diaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 일기를 작성한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 일기 제목
    @Column(length = 255, nullable = false)
    private String title;

    // 일기 본문 (Lob으로 긴 텍스트 지원)
    @Lob
    private String content;

    // 감정 아이콘 (예: 이모지 문자열)
    @Column(length = 50)
    private String emotionIcon;

    // 즐겨찾기 여부 (true: 즐겨찾기, false: 일반)
    private Boolean isFavorited;

    // 상태: '확인' 또는 '미확인'
    @Column(length = 20)
    private String status;

    // 생성 시각
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 수정 시각
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 논리 삭제를 위한 필드 (삭제 시점 기록, null이면 삭제되지 않은 상태)
    private LocalDateTime deletedAt;

    // DiaryPhoto와의 일대다 관계 (cascade 및 orphanRemoval 설정)
    @OneToMany(mappedBy = "diary", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DiaryPhoto> diaryPhotos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isFavorited == null) {
            this.isFavorited = false;
        }
        if (this.status == null) {
            this.status = "미확인";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
