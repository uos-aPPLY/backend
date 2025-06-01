package com.apply.diarypic.terms.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.terms.dto.TermsDto;
import com.apply.diarypic.terms.dto.UserAgreementRequest;
import com.apply.diarypic.terms.entity.TermsType;
import com.apply.diarypic.terms.service.TermsService;
import com.apply.diarypic.user.dto.UserNicknameResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Terms", description = "서비스 약관 및 동의 API")
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @Operation(summary = "사용자에게 보여줄 최신 약관 목록 조회", description = "각 약관에 대한 현재 사용자의 동의 상태를 포함하여 반환합니다.")
    @GetMapping
    public ResponseEntity<List<TermsDto>> getLatestTermsForUser(@CurrentUser UserPrincipal userPrincipal) {
        List<TermsDto> termsList = termsService.getLatestTermsForUser(userPrincipal.getUserId());
        return ResponseEntity.ok(termsList);
    }

    @Operation(summary = "사용자 약관 동의 상태 제출", description = "사용자가 동의/비동의한 약관 목록을 받아 처리하며, 사용자의 현재 닉네임 상태를 반환합니다.")
    @PostMapping("/agreements")
    public ResponseEntity<UserNicknameResponseDto> updateUserAgreements(@CurrentUser UserPrincipal userPrincipal,
                                                                        @Valid @RequestBody UserAgreementRequest request) {
        UserNicknameResponseDto responseDto = termsService.updateUserAgreements(userPrincipal.getUserId(), request);
        return ResponseEntity.ok(responseDto);
    }

    @Operation(summary = "사용자가 모든 필수 약관에 동의했는지 확인")
    @GetMapping("/agreements/check-required")
    public ResponseEntity<Boolean> checkAllRequiredTermsAgreed(@CurrentUser UserPrincipal userPrincipal) {
        boolean agreed = termsService.hasAgreedToAllRequiredTerms(userPrincipal.getUserId());
        return ResponseEntity.ok(agreed);
    }

    @Operation(summary = "특정 타입의 최신 약관 내용 조회 (HTML)", description = "로그인 없이 누구나 특정 타입의 최신 약관 내용을 HTML 형태로 조회할 수 있습니다. 예: /api/terms/SERVICE_TERMS/content")
    @GetMapping("/{termsTypeString}/content")
    public ResponseEntity<String> getLatestTermsContent(@PathVariable String termsTypeString) {
        TermsType termsType;
        try {
            termsType = TermsType.valueOf(termsTypeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("유효하지 않은 약관 타입입니다: " + termsTypeString);
        }

        String termsContentHtml = termsService.getLatestActiveTermsContentByType(termsType);
        // HTML 내용을 직접 반환하고, Content-Type을 text/html로 설정
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
                .body(termsContentHtml);
    }
}