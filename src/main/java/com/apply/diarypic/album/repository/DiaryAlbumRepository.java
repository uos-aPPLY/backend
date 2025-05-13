package com.apply.diarypic.album.repository;

import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.album.entity.DiaryAlbum;
import com.apply.diarypic.album.entity.DiaryAlbumId;
import com.apply.diarypic.diary.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface DiaryAlbumRepository extends JpaRepository<DiaryAlbum, DiaryAlbumId> {
    List<DiaryAlbum> findByAlbum(Album album);
    List<DiaryAlbum> findByDiary(Diary diary);
    void deleteByDiary(Diary diary);
}