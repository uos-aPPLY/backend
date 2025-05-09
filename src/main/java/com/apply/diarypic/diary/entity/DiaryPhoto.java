package com.apply.diarypic.diary.entity;

import com.apply.diarypic.keyword.entity.PhotoKeyword;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "diary_photos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiaryPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String photoUrl;

    private LocalDateTime shootingDateTime;
    private String location;

    @Column(name = "detailed_address")
    private String detailedAddress;

    private Boolean isRecommended;
    private Integer sequence;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id")
    private Diary diary;

    // Keyword 관련
    @OneToMany(mappedBy = "diaryPhoto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PhotoKeyword> photoKeywords = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}