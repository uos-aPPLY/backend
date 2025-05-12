package com.apply.diarypic.global.security.jwt;

import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;   // ★
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        log.info("JwtFilter invoked, auth={}  URI={}",
                SecurityContextHolder.getContext().getAuthentication(),
                request.getRequestURI());

        String token = extractToken(request);
        if (token != null && jwtUtils.validateToken(token)) {
            Claims claims = jwtUtils.getClaims(token);
            Long userId   = Long.valueOf(claims.getSubject());
            String provider = claims.get("provider", String.class);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 정보 없음"));

            UserPrincipal principal = new UserPrincipal(user.getId(), provider);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            /* ▼▼ SecurityContext 를 두 군데 모두에 저장 ▼▼ */
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);                                           // 기존 ThreadLocal
            request.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context);                                                       // ★ async 디스패치용
            /* ▲▲ ------------------------------------------------ ▲▲ */

            log.debug("SecurityContext populated for user ID: {}", principal.getUserId());
        } else {
            log.debug("No valid JWT token found in request to {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }
}
