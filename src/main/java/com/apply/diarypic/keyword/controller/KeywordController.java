package com.apply.diarypic.keyword.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.keyword.dto.KeywordCreateRequest;
import com.apply.diarypic.keyword.dto.KeywordDto;
import com.apply.diarypic.keyword.service.KeywordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Keyword", description = "개인 키워드 관리 API")
@RestController
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywordService;

    @Operation(summary = "사용자 개인 키워드 목록 조회")
    @GetMapping
    public ResponseEntity<List<KeywordDto>> getPersonalKeywords(@CurrentUser UserPrincipal userPrincipal) {
        List<KeywordDto> keywords = keywordService.getPersonalKeywords(userPrincipal.getUserId());
        return ResponseEntity.ok(keywords);
    }

    @Operation(summary = "사용자 개인 키워드 생성")
    @PostMapping
    public ResponseEntity<KeywordDto> createPersonalKeyword(@CurrentUser UserPrincipal userPrincipal,
                                                            @Valid @RequestBody KeywordCreateRequest request) {
        KeywordDto createdKeyword = keywordService.createPersonalKeyword(userPrincipal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdKeyword);
    }

    @Operation(summary = "사용자 개인 키워드 수정")
    @PutMapping("/{keywordId}")
    public ResponseEntity<KeywordDto> updatePersonalKeyword(@CurrentUser UserPrincipal userPrincipal,
                                                            @PathVariable Long keywordId,
                                                            @Valid @RequestBody KeywordCreateRequest request) {
        KeywordDto updatedKeyword = keywordService.updatePersonalKeyword(userPrincipal.getUserId(), keywordId, request);
        return ResponseEntity.ok(updatedKeyword);
    }

    @Operation(summary = "사용자 개인 키워드 삭제")
    @DeleteMapping("/{keywordId}")
    public ResponseEntity<Void> deletePersonalKeyword(@CurrentUser UserPrincipal userPrincipal,
                                                      @PathVariable Long keywordId) {
        keywordService.deletePersonalKeyword(userPrincipal.getUserId(), keywordId);
        return ResponseEntity.noContent().build();
    }
}