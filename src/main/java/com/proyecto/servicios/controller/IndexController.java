package com.proyecto.servicios.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Controller
@Hidden
public class IndexController {

    private static final MediaType APK_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.android.package-archive");

    private static final List<Path> APK_CANDIDATES = List.of(
            Paths.get("mobile-flutter", "build", "app", "outputs", "flutter-apk", "app-release.apk"),
            Paths.get("frontend-web", "public", "BancoDigital.apk")
    );

    @GetMapping("/")
    public String index() {
        return "redirect:/swagger-ui/index.html";
    }

    @GetMapping({"/BancoDigital.apk", "/api/v1/app/download"})
    public ResponseEntity<Resource> downloadApk() {
        Path apkPath = resolveApkPath();
        if (apkPath == null) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(apkPath);
        long size = apkPath.toFile().length();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APK_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment().filename("BancoDigital.apk").build());
        headers.setContentLength(size);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    private Path resolveApkPath() {
        for (Path candidate : APK_CANDIDATES) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
