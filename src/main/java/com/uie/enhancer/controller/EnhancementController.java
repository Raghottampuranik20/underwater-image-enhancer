package com.uie.enhancer.controller;

import com.uie.enhancer.dto.EnhancementMethod;
import com.uie.enhancer.dto.EnhancementOptions;
import com.uie.enhancer.exception.ImageProcessingException;
import com.uie.enhancer.service.ImageEnhancementService;
import com.uie.enhancer.util.ImageUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/enhance")
public class EnhancementController {

    private final ImageEnhancementService enhancementService;

    public EnhancementController(ImageEnhancementService enhancementService) {
        this.enhancementService = enhancementService;
    }

    /**
     * Enhances an uploaded underwater image and returns the result as a raw
     * PNG image body — convenient for &lt;img src&gt; or curl -o.
     *
     * curl -F "file=@dive.jpg" \
     *   "http://localhost:8080/api/v1/enhance?method=AUTO&clipLimit=3&gamma=0.85" \
     *   -o enhanced.png
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> enhance(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "AUTO") EnhancementMethod method,
            @RequestParam(required = false) Double clipLimit,
            @RequestParam(required = false) Integer tileGrid,
            @RequestParam(required = false) Double gamma,
            @RequestParam(required = false) Double redAlpha,
            @RequestParam(required = false) Double sharpenAmount) {

        BufferedImage source = readUpload(file);
        EnhancementOptions options = buildOptions(method, clipLimit, tileGrid, gamma, redAlpha, sharpenAmount);

        BufferedImage enhanced = enhancementService.enhance(source, options);
        byte[] png = ImageUtils.toPngBytes(enhanced);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"enhanced.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /**
     * Same as above, but returns JSON with a base64-encoded PNG — handy for
     * SPA/mobile clients that prefer JSON payloads over raw binary bodies.
     */
    @PostMapping(value = "/base64", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> enhanceBase64(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "AUTO") EnhancementMethod method,
            @RequestParam(required = false) Double clipLimit,
            @RequestParam(required = false) Integer tileGrid,
            @RequestParam(required = false) Double gamma,
            @RequestParam(required = false) Double redAlpha,
            @RequestParam(required = false) Double sharpenAmount) {

        BufferedImage source = readUpload(file);
        EnhancementOptions options = buildOptions(method, clipLimit, tileGrid, gamma, redAlpha, sharpenAmount);

        BufferedImage enhanced = enhancementService.enhance(source, options);
        byte[] png = ImageUtils.toPngBytes(enhanced);
        String base64 = Base64.getEncoder().encodeToString(png);

        return ResponseEntity.ok(Map.of(
                "method", options.getMethod(),
                "width", enhanced.getWidth(),
                "height", enhanced.getHeight(),
                "imageBase64", base64
        ));
    }

    private BufferedImage readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageProcessingException("No file uploaded. Send it as multipart field 'file'.");
        }
        try {
            return ImageUtils.readImage(file.getBytes());
        } catch (Exception e) {
            throw new ImageProcessingException("Failed to read uploaded file: " + e.getMessage(), e);
        }
    }

    private EnhancementOptions buildOptions(EnhancementMethod method, Double clipLimit, Integer tileGrid,
                                             Double gamma, Double redAlpha, Double sharpenAmount) {
        EnhancementOptions options = new EnhancementOptions();
        if (method != null) options.setMethod(method);
        if (clipLimit != null) options.setClipLimit(clipLimit);
        if (tileGrid != null) options.setTileGrid(tileGrid);
        if (gamma != null) options.setGamma(gamma);
        if (redAlpha != null) options.setRedAlpha(redAlpha);
        if (sharpenAmount != null) options.setSharpenAmount(sharpenAmount);
        return options;
    }
}
