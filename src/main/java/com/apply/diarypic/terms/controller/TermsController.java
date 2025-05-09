package com.apply.diarypic.terms.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.terms.dto.TermsDto;
import com.apply.diarypic.terms.dto.UserAgreementRequest;
import com.apply.diarypic.terms.service.TermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "사용자 약관 동의 상태 제출", description = "사용자가 동의/비동의한 약관 목록을 받아 처리합니다.")
    @PostMapping("/agreements")
    public ResponseEntity<Void> updateUserAgreements(@CurrentUser UserPrincipal userPrincipal,
                                                     @Valid @RequestBody UserAgreementRequest request) {
        termsService.updateUserAgreements(userPrincipal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "사용자가 모든 필수 약관에 동의했는지 확인")
    @GetMapping("/agreements/check-required")
    public ResponseEntity<Boolean> checkAllRequiredTermsAgreed(@CurrentUser UserPrincipal userPrincipal) {
        boolean agreed = termsService.hasAgreedToAllRequiredTerms(userPrincipal.getUserId());
        return ResponseEntity.ok(agreed);
    }
}