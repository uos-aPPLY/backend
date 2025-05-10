package com.apply.diarypic.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"snsProvider", "snsUserId"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String snsProvider;   // 'kakao'
    private String snsUserId;     // SNS 제공자 내 유저 ID (PK 아님)

    @Setter
    private String nickname;

    @Lob
    @Setter
    private String writingStylePrompt;

    @Setter
    private Boolean alarmEnabled;

    @Setter
    private LocalTime alarmTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
