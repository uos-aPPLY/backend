package com.apply.diarypic.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User", description = "유저 관련 API")
public class UserController {

    @GetMapping("/ping")
    @Operation(summary = "서버 연결 테스트", description = "서버가 동작하는지 확인합니다.")
    public String ping() {
        return "pong";
    }
}
