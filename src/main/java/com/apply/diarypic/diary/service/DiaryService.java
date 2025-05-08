package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.diary.dto.AiDiaryCreateRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.diary.repository.DiaryRepository;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository; // DiaryPhoto 정보 조회를 위해 필요
    private final AiServerService aiServerService;
    private final S3Uploader s3Uploader; // 일기 삭제 시 S3 파일 삭제에 사용될 수 있음

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // 기존 createDiary 메소드는 그대로 유지한다고 가정

    /**
     * AI를 활용하여 자동 일기를 생성합니다.
     *
     * @param userId 현재 사용자 ID
     * @param userSpeech 사용자의 말투 프롬프트
     * @param finalizedPhotoPayloads 프론트엔드에서 전달받은 최종 사진 정보 (ID, 키워드, 순서)
     * @return 생성된 일기 정보
     */
    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, String userSpeech, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty()) {
            throw new IllegalArgumentException("AI 일기 생성을 위한 사진 정보가 없습니다.");
        }
        if (finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("AI 일기 생성은 최대 9장의 사진까지만 가능합니다.");
        }

        // 1. AiServerService로 보낼 ImageInfoDto 리스트 생성
        List<ImageInfoDto> imageInfoForAi = finalizedPhotoPayloads.stream()
                .map(payload -> {
                    // photoId로 DB에서 DiaryPhoto 엔티티 조회
                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> new IllegalArgumentException("사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));

                    // 해당 사진이 현재 사용자의 사진인지 검증 (선택 사항이지만 보안상 권장)
                    if (!diaryPhoto.getUserId().equals(userId)) {
                        // 이 검증은 DiaryPhoto에 userId 필드가 있고, 임시 저장 단계에서 잘 관리된다는 가정하에 동작합니다.
                        // 또는, 이미 PhotoSelectionService 등에서 사용자의 사진임이 검증되었다면 생략 가능.
                        throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                    }

                    return new ImageInfoDto(
                            diaryPhoto.getPhotoUrl(),
                            diaryPhoto.getShootingDateTime() != null ? diaryPhoto.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                            diaryPhoto.getDetailedAddress(),
                            payload.getKeyword(), // 프론트에서 받은 키워드
                            payload.getSequence() // 프론트에서 받은 순서
                    );
                })
                .collect(Collectors.toList());

        AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userSpeech, imageInfoForAi);

        // 2. AiServerService를 통해 AI 서버에 일기 생성 요청
        // Mono<AiDiaryResponseDto> monoResponse = aiServerService.requestDiaryGeneration(aiRequest);
        // AiDiaryResponseDto aiResponse = monoResponse.block(); // 실제 운영에서는 비동기 처리 권장

        // WebClient 호출을 동기적으로 처리해야 한다면, Controller 레벨에서부터 비동기 스트림(Mono/Flux)을 다루는 것이 좋습니다.
        // 서비스 계층에서 .block()을 사용하는 것은 간단한 예시이며, 실제로는 다음과 같이 처리할 수 있습니다.
        // (컨트롤러가 Mono<DiaryResponse>를 반환하도록 수정 필요)
        // return aiServerService.requestDiaryGeneration(aiRequest)
        // .flatMap(aiResponse -> {
        // if (aiResponse == null || aiResponse.getDiary_text() == null || aiResponse.getDiary_text().isEmpty()) {
        // return Mono.error(new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다."));
        // }
        // String autoContent = aiResponse.getDiary_text();
        // Diary diary = createAndSaveDiaryEntity(user, autoContent, finalizedPhotoPayloads);
        // return Mono.just(DiaryResponse.from(diary));
        // });

        // 설명을 위해 여기서는 .block()을 사용한 동기적 흐름으로 진행합니다.
        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryGeneration(aiRequest).block();

        if (aiResponse == null || aiResponse.getDiary_text() == null || aiResponse.getDiary_text().isEmpty()) {
            throw new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다.");
        }
        String autoContent = aiResponse.getDiary_text();

        // 3. Diary 엔티티 생성 및 저장
        Diary diary = createAndSaveDiaryEntity(user, autoContent, finalizedPhotoPayloads, userId);
        return DiaryResponse.from(diary);
    }

    // Diary 엔티티 생성 및 저장을 위한 헬퍼 메소드 (중복 로직 방지)
    private Diary createAndSaveDiaryEntity(User user, String content, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads, Long userId) {
        // 최종 선택된 DiaryPhoto 엔티티들을 조회하고, sequence를 설정합니다.
        List<DiaryPhoto> diaryPhotosForDiary = finalizedPhotoPayloads.stream()
                .map(payload -> {
                    DiaryPhoto dp = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                    // 사용자 검증 (이미 위에서 했지만, 여기서 한번 더 할 수도 있음)
                    if (!dp.getUserId().equals(userId)) {
                        throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                    }
                    dp.setSequence(payload.getSequence()); // DiaryPhoto 엔티티에 sequence 설정
                    return dp;
                })
                .collect(Collectors.toList());


        Diary diary = Diary.builder()
                .user(user)
                .title("AI 자동 생성 일기 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) // 제목 형식 변경
                .content(content)
                .emotionIcon("🙂") // 기본값 또는 AI가 추천해줄 수도 있음
                .isFavorited(false)
                .status("미확인") // 초기 상태
                .diaryPhotos(diaryPhotosForDiary) // DiaryPhoto 리스트 연결
                .build();

        // 양방향 연관관계: DiaryPhoto 엔티티에 Diary 객체 설정
        for (DiaryPhoto dp : diaryPhotosForDiary) {
            dp.setDiary(diary); // 이 사진이 어떤 일기에 속하는지 설정
        }
        // 주의: DiaryPhoto 엔티티에 setDiary() 메소드가 있어야 하며,
        // 이전에 임시 상태(diaryId=null)였던 사진들이 이제 특정 일기와 연결됩니다.
        // photoRepository.saveAll(diaryPhotosForDiary)를 호출할 필요는 없습니다.
        // Diary 저장 시 CascadeType.ALL (또는 MERGE, PERSIST) 등에 의해 DiaryPhoto의 변경사항(diary_id 업데이트)도 함께 처리됩니다.
        // (Diary 엔티티의 diaryPhotos 필드에 @OneToMany(cascade = CascadeType.ALL) 등이 설정되어 있어야 함)

        return diaryRepository.save(diary);
    }

    // (deleteDiary 메소드는 기존과 동일)
    @Transactional
    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일기를 찾을 수 없습니다."));
        if (!diary.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일기에 대한 삭제 권한이 없습니다.");
        }
        // 연관된 사진 S3에서 삭제
        diary.getDiaryPhotos().forEach(photo -> {
            try {
                s3Uploader.delete(photo.getPhotoUrl());
            } catch (Exception e) {
                log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
            }
        });
        diaryRepository.delete(diary); // Cascade 설정에 따라 DiaryPhoto도 함께 삭제될 수 있음
    }

    // (사용자 직접 작성 일기 생성 메소드 - 기존 DiaryRequest 처리)
    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<DiaryPhoto> photos = photoRepository.findAllById(request.getPhotoIds());
        if (photos.size() != request.getPhotoIds().size()) {
            throw new IllegalArgumentException("유효하지 않은 photoId가 포함되어 있습니다.");
        }
        // 사용자 사진인지 검증
        for(DiaryPhoto photo : photos){
            if(!photo.getUserId().equals(userId)){
                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + photo.getId());
            }
        }


        // sequence 설정 및 일기 연관
        for (int i = 0; i < photos.size(); i++) {
            // DiaryRequest에 순서 정보가 없다면, ID 리스트 순서대로 sequence 부여
            // 또는 DiaryRequest DTO에 sequence 정보 포함 필요
            photos.get(i).setSequence(i + 1);
        }

        Diary diary = Diary.builder()
                .user(user)
                .title(request.getTitle() != null ? request.getTitle() : "나의 일기")
                .content(request.getContent())
                .emotionIcon(request.getEmotionIcon())
                .diaryPhotos(photos)
                .isFavorited(false) // 기본값
                .status("확인") // 사용자가 직접 작성했으므로 '확인' 상태
                .build();

        photos.forEach(photo -> photo.setDiary(diary));

        Diary saved = diaryRepository.save(diary);
        return DiaryResponse.from(saved);
    }
}