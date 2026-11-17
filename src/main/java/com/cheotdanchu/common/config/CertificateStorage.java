package com.cheotdanchu.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 확인서 PDF 를 서버 로컬 폴더(app.certificate-dir, 기본 certificates/)에 저장한다.
 * 시연용이라 로컬에 둔다. 서버를 옮기면 파일은 따라가지 않는다.
 */
@Component
public class CertificateStorage {

    private final Path dir;

    public CertificateStorage(@Value("${app.certificate-dir:certificates}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath();
    }

    /** PDF 를 저장하고 DB 에 넣을 파일 이름(폴더는 뺀 것)을 돌려준다 */
    public String save(byte[] pdf) {
        String name = UUID.randomUUID() + ".pdf";
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), pdf);
        } catch (IOException e) {
            throw new UncheckedIOException("확인서 PDF 를 저장하지 못했어요", e);
        }
        return name;
    }

    /** 저장했던 PDF 를 지운다 (DB 저장이 롤백됐을 때 가리키는 행 없는 파일이 남지 않게). 못 지워도 발급 결과에는 영향이 없다 */
    public void delete(String name) {
        try {
            Files.deleteIfExists(dir.resolve(name));
        } catch (IOException ignored) {
            // 남은 파일은 다음 정리 때 지운다
        }
    }
}
