package com.apply.diarypic.diary.repository;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.user.entity.User; // User 임포트
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    List<Diary> findByUserId(Long userId); // UserService에서 사용 중

    // 사용자의 특정 날짜 일기 조회 (선택적, 필요시)
    // List<Diary> findByUserAndDiaryDate(User user, LocalDate diaryDate);

    // 사용자의 좋아요한 일기 목록 조회 (최신순 또는 diaryDate 순)
    List<Diary> findByUserAndIsFavoritedTrueOrderByDiaryDateDesc(User user); // AlbumController에서 사용

    // 사용자 삭제 시 해당 사용자의 모든 일기 삭제 (UserService에서deleteAll(Iterable) 사용 중이므로 별도 필요X)
    // void deleteByUserId(Long userId);
}