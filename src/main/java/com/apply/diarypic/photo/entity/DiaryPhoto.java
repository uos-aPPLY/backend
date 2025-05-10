package com.apply.diarypic.photo.entity;

import com.apply.diarypic.diary.entity.Diary;
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

    @Column(length = 100)
    private String countryName;

    @Column(length = 100)
    private String adminAreaLevel1;

    @Column(length = 100)
    private String locality;

    private String location; // GPS 좌표 문자열

    private Integer sequence;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id")
    private Diary diary;

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