package com.apply.diarypic.diary.repository;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    // --- 활성 일기 조회 (deletedAt IS NULL) ---
    Optional<Diary> findByIdAndUserAndDeletedAtIsNull(Long id, User user);
    List<Diary> findByUserIdAndDeletedAtIsNull(Long userId); // UserService의 deleteUser에서 사용 가능

    @Query("SELECT d FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL ORDER BY d.diaryDate DESC, d.createdAt DESC")
    Page<Diary> findActiveDiariesByUser(@Param("user") User user, Pageable pageable);

    List<Diary> findByUserAndIsFavoritedTrueAndDeletedAtIsNullOrderByDiaryDateDesc(User user);

    // --- 모든 일기 조회 (deletedAt 상관 없이 - 주로 관리자용 또는 내부 로직용) ---
    Optional<Diary> findByIdAndUser(Long id, User user); // 복원 시 사용 (휴지통에 있는 것 찾아야 함)
    List<Diary> findByUserId(Long userId); // 사용자의 모든 일기 (활성+휴지통) - 필요시 사용

    // --- 휴지통 관련 조회 (deletedAt IS NOT NULL) ---
    Page<Diary> findByUserAndDeletedAtIsNotNullOrderByDeletedAtDesc(User user, Pageable pageable);
    Optional<Diary> findByIdAndUserAndDeletedAtIsNotNull(Long id, User user); // 개별 영구 삭제 시
    List<Diary> findAllByUserAndDeletedAtIsNotNull(User user); // 휴지통 전체 비우기용
    List<Diary> findAllByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime cutoffDateTime); // 스케줄러 자동 영구삭제용

    // --- Count 쿼리 (deletedAt IS NULL 조건 추가) ---
    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL")
    long countByUserAndDeletedAtIsNull(@Param("user") User user);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND FUNCTION('YEAR', d.diaryDate) = :year")
    long countByUserAndYearAndDeletedAtIsNull(@Param("user") User user, @Param("year") int year);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND FUNCTION('YEAR', d.diaryDate) = :year AND FUNCTION('MONTH', d.diaryDate) = :month")
    long countByUserAndYearAndMonthAndDeletedAtIsNull(@Param("user") User user, @Param("year") int year, @Param("month") int month);

    @Query("SELECT COUNT(d) FROM Diary d WHERE d.user = :user AND d.deletedAt IS NULL AND d.diaryDate BETWEEN :startDate AND :endDate")
    long countByUserAndDiaryDateBetweenAndDeletedAtIsNull(@Param("user") User user, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}