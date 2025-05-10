package com.apply.diarypic.album.repository;

import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.album.entity.DiaryAlbum;
import com.apply.diarypic.album.entity.DiaryAlbumId;
import com.apply.diarypic.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface DiaryAlbumRepository extends JpaRepository<DiaryAlbum, DiaryAlbumId> {
    Optional<DiaryAlbum> findByDiaryAndAlbum(Diary diary, Album album);
    List<DiaryAlbum> findByAlbum(Album album);
    List<DiaryAlbum> findByDiary(Diary diary); // 한 일기가 속한 모든 앨범 매핑 조회 시
    void deleteByDiary(Diary diary); // 일기 삭제 시 해당 일기의 모든 앨범 매핑 삭제
}