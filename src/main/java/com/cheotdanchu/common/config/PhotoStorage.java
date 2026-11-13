package com.cheotdanchu.common.config;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 인증 사진을 서버 로컬 폴더(app.upload-dir, 기본 uploads/)에 저장하고 /uploads/파일명 주소를 돌려준다.
 * 시연용이라 로컬에 둔다. 서버를 옮기면 사진은 따라가지 않는다.
 */
@Component
public class PhotoStorage {

    public static final String URL_PREFIX = "/uploads/";

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/heic", ".heic", "image/heif", ".heif", "image/webp", ".webp");

    /** 아이폰·브라우저에 따라 HEIC를 형식 없이(application/octet-stream) 보내기도 해서, 그때만 파일 이름 확장자로 판단한다 */
    private static final Map<String, String> UNTYPED_EXTENSIONS = Map.of(".heic", ".heic", ".heif", ".heif");

    private final Path dir;

    public PhotoStorage(@Value("${app.upload-dir:uploads}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath();
    }

    public Path dir() {
        return dir;
    }

    /** 이미지가 아니면 400. 여러 장일 때 저장 전에 전부 검사해서, 일부만 저장되고 실패하는 일이 없게 한다 */
    public void validate(MultipartFile photo) {
        extension(photo);
    }

    /** 이미지가 아니면 400. 파일 이름은 겹치지 않게 UUID로 새로 짓는다 (아이가 올린 이름은 쓰지 않음) */
    public String save(MultipartFile photo) {
        String name = UUID.randomUUID() + extension(photo);
        try {
            Files.createDirectories(dir);
            photo.transferTo(dir.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("사진을 저장하지 못했어요: " + name, e);
        }
        return URL_PREFIX + name;
    }

    private String extension(MultipartFile photo) {
        String ext = EXTENSIONS.get(photo.getContentType());
        if (ext == null && isUntyped(photo.getContentType())) ext = UNTYPED_EXTENSIONS.get(fileExtension(photo));
        if (ext == null) throw new GeneralException(ErrorStatus.INVALID_PHOTO);
        return ext;
    }

    private static boolean isUntyped(String contentType) {
        return contentType == null || contentType.isBlank() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType);
    }

    private static String fileExtension(MultipartFile photo) {
        String name = photo.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }
}
