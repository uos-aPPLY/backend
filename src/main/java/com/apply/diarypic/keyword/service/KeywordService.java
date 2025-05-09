package com.apply.diarypic.keyword.service;

import com.apply.diarypic.keyword.dto.KeywordCreateRequest;
import com.apply.diarypic.keyword.dto.KeywordDto;
import com.apply.diarypic.keyword.entity.Keyword;
import com.apply.diarypic.keyword.repository.KeywordRepository;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;

    // 사용자의 모든 개인 키워드 목록 조회
    public List<KeywordDto> getPersonalKeywords(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        return keywordRepository.findByUserOrderByNameAsc(user).stream()
                .map(KeywordDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 사용자 개인 키워드 생성 (또는 기존 키워드 반환)
    @Transactional
    public KeywordDto createOrGetPersonalKeyword(Long userId, String keywordName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // 이미 해당 이름의 개인 키워드가 있는지 확인
        Optional<Keyword> existingKeywordOpt = keywordRepository.findByNameAndUser(keywordName, user);
        if (existingKeywordOpt.isPresent()) {
            return KeywordDto.fromEntity(existingKeywordOpt.get());
        }

        // 없다면 새로 생성
        Keyword newKeyword = Keyword.builder()
                .name(keywordName)
                .user(user)
                .build();
        return KeywordDto.fromEntity(keywordRepository.save(newKeyword));
    }

    // (DTO를 받는 createPersonalKeyword 메소드는 유지 가능 - 키워드 설정 화면용)
    @Transactional
    public KeywordDto createPersonalKeyword(Long userId, KeywordCreateRequest request) {
        return createOrGetPersonalKeyword(userId, request.getName());
    }


    // 사용자 개인 키워드 수정
    @Transactional
    public KeywordDto updatePersonalKeyword(Long userId, Long keywordId, KeywordCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new EntityNotFoundException("Keyword not found with id: " + keywordId));

        if (!keyword.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("수정 권한이 없는 키워드입니다.");
        }

        // 수정하려는 이름이 다른 개인 키워드와 중복되는지 확인 (자기 자신은 제외)
        keywordRepository.findByNameAndUser(request.getName(), user)
                .filter(foundKeyword -> !foundKeyword.getId().equals(keywordId))
                .ifPresent(k -> {
                    throw new IllegalArgumentException("이미 사용 중인 다른 개인 키워드 이름입니다: " + request.getName());
                });

        keyword.setName(request.getName());
        return KeywordDto.fromEntity(keywordRepository.save(keyword));
    }

    // 사용자 개인 키워드 삭제
    @Transactional
    public void deletePersonalKeyword(Long userId, Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new EntityNotFoundException("Keyword not found with id: " + keywordId));

        if (!keyword.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("삭제 권한이 없는 키워드입니다.");
        }
        // Keyword 엔티티의 @OneToMany photoKeywords에 orphanRemoval=true 설정으로 자동 삭제됨
        keywordRepository.delete(keyword);
    }

    @Transactional
    public void createInitialRecommendedKeywordsForUser(User user) {
        List<String> initialKeywordNames = Arrays.asList("인물", "풍경", "음식", "동물", "사물");
        for (String name : initialKeywordNames) {
            // 사용자의 개인 키워드로 이미 존재하는지 확인 후 없으면 생성
            if (!keywordRepository.findByNameAndUser(name, user).isPresent()) {
                Keyword initialKeyword = Keyword.builder()
                        .name(name)
                        .user(user)
                        .build();
                keywordRepository.save(initialKeyword);
            }
        }
    }
}