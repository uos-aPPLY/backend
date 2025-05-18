package com.apply.diarypic.global.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String dirName) throws IOException {
        String s3Key = (StringUtils.hasText(dirName) ? dirName + "/" : "") + UUID.randomUUID();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        log.info("S3 파일 업로드 완료: Key='{}'", s3Key);
        return getS3Url(s3Key);
    }

    private String getS3Url(String s3Key) {
        return String.format("https://%s.s3.ap-northeast-2.amazonaws.com/%s", bucket, s3Key);
    }

    /**
     * S3 객체 URL로부터 파일 키(객체 키)를 추출합니다.
     * 예: "https://bucket-name.s3.region.amazonaws.com/path/to/file.jpg" -> "path/to/file.jpg"
     * 이 메소드는 deleteFileByUrl 내부에서만 사용되므로 private으로 변경 가능합니다.
     */
    private String extractFileKeyFromUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            log.warn("파일 URL이 비어있어 키를 추출할 수 없습니다. URL: {}", fileUrl);
            return null;
        }
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath();

            String key = path.startsWith("/") ? path.substring(1) : path;
            return key;
        } catch (MalformedURLException e) {
            log.error("잘못된 형식의 S3 URL 입니다: {}", fileUrl, e);
            return null;
        }
    }

    public void deleteFileByUrl(String photoUrl) {
        if (!StringUtils.hasText(photoUrl)) {
            log.warn("삭제할 사진 URL이 비어있습니다.");
            return;
        }

        String fileKey = extractFileKeyFromUrl(photoUrl);

        if (!StringUtils.hasText(fileKey)) {
            log.error("제공된 URL에서 S3 파일 키를 추출할 수 없습니다: {}", photoUrl);
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 완료: Key='{}', URL='{}'", fileKey, photoUrl);
        } catch (S3Exception e) {
            log.error("S3 파일 삭제 중 오류 발생: Key='{}', URL='{}', Error='{}'", fileKey, photoUrl, e.getMessage(), e);
        }
    }
}