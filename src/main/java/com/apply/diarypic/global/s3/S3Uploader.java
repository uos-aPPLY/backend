package com.apply.diarypic.global.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String dirName) throws IOException {
        String fileName = dirName + "/" + UUID.randomUUID();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return getS3Url(fileName);
    }

    public void delete(String fileUrl) {
        // fileUrl 예: https://{bucket}.s3.ap-northeast-2.amazonaws.com/{fileName}
        String prefix = String.format("https://%s.s3.ap-northeast-2.amazonaws.com/", bucket);
        if (!fileUrl.startsWith(prefix)) {
            log.error("파일 URL 형식이 올바르지 않습니다: {}", fileUrl);
            throw new IllegalArgumentException("파일 URL 형식이 올바르지 않습니다.");
        }
        String key = fileUrl.replace(prefix, "");
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(deleteRequest);
        log.info("S3에서 파일 삭제: key={}", key);
    }

    private String getS3Url(String fileName) {
        return String.format("https://%s.s3.ap-northeast-2.amazonaws.com/%s", bucket, fileName);
    }

    public void deleteFileByUrl(String photoUrl) {
    }
}
