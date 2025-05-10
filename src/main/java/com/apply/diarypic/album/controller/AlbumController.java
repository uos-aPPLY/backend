package com.apply.diarypic.album.controller;

import com.apply.diarypic.album.dto.AlbumDto;
import com.apply.diarypic.album.service.AlbumService;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.service.DiaryService; // DiaryService에 getFavoriteDiaries 메소드 추가 필요
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.user.entity.User; // User 엔티티 임포트
import com.apply.diarypic.user.repository.UserRepository; // UserRepository 임포트
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException; // 예외 임포트
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors; // Collectors 임포트

@Tag(name = "Album", description = "앨범 관련 API")
@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;
    private final DiaryService diaryService; // 좋아요 일기 조회를 위해 DiaryService 사용
    private final UserRepository userRepository; // 사용자 조회를 위해 UserRepository 주입

    @Operation(summary = "사용자의 장소 기반 앨범 목록 조회")
    @GetMapping
    public ResponseEntity<List<AlbumDto>> getUserAlbums(@CurrentUser UserPrincipal userPrincipal) {
        List<AlbumDto> albums = albumService.getUserAlbums(userPrincipal.getUserId());
        return ResponseEntity.ok(albums);
    }

    @Operation(summary = "특정 장소 기반 앨범에 속한 일기 목록 조회")
    @GetMapping("/{albumId}/diaries")
    public ResponseEntity<List<DiaryResponse>> getDiariesInAlbum(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long albumId) {
        List<DiaryResponse> diaries = albumService.getDiariesInAlbum(userPrincipal.getUserId(), albumId);
        return ResponseEntity.ok(diaries);
    }

    @Operation(summary = "좋아요 누른 일기 목록 조회 ('좋아요 앨범')")
    @GetMapping("/favorites")
    public ResponseEntity<List<DiaryResponse>> getFavoriteDiaries(@CurrentUser UserPrincipal userPrincipal) {
        // DiaryService에 해당 사용자의 좋아요 일기만 가져오는 메소드를 호출하도록 변경
        // User 엔티티를 직접 생성하는 대신, userId로 서비스 메소드에 전달합니다.
        List<DiaryResponse> favoriteDiaries = diaryService.getFavoriteDiaries(userPrincipal.getUserId());
        return ResponseEntity.ok(favoriteDiaries);
    }

    @Operation(summary = "앨범 삭제 (사용자 생성 장소 앨범)")
    @DeleteMapping("/{albumId}")
    public ResponseEntity<Void> deleteAlbum(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long albumId) {
        albumService.deleteAlbum(userPrincipal.getUserId(), albumId);
        return ResponseEntity.noContent().build();
    }
}