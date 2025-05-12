package com.apply.diarypic.util.controller; // 예시 패키지

import com.apply.diarypic.util.dto.DailyQuoteResponse;
import com.apply.diarypic.util.service.UtilService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/util") // 엔드포인트 경로 예시
@RequiredArgsConstructor
public class UtilController {

    private final UtilService utilService;

    @Operation(summary = "오늘의 메인 문구 조회")
    @GetMapping("/daily-quote")
    public ResponseEntity<DailyQuoteResponse> getDailyQuote() {
        return ResponseEntity.ok(utilService.getDailyQuote());
    }
}