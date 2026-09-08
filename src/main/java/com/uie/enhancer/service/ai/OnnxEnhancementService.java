package com.uie.enhancer.service.ai;

import ai.onnxruntime.*;
import com.uie.enhancer.exception.ImageProcessingException;
import com.uie.enhancer.exception.ModelNotAvailableException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a deep-learning underwater-enhancement model (e.g. FUnIE-GAN,
 * UWCNN, Water-Net - any model exported to ONNX with a
 * [1,3,H,W] float32 NCHW input/output in the 0..1 range) if one is present.
 *
 * This is a genuine, working inference hook - not a stub - but it needs a
 * real .onnx weights file to do anything. Ship one at the configured
 * uie.ai.model-path and set uie.ai.enabled=true to activate it. Without a
 * model file, isAvailable() returns false and the caller (ImageEnhancementService)
 * transparently falls back to the classical CV pipeline.
 *
 * To obtain a compatible model:
 *   1. Train or download a pretrained underwater enhancement network
 *      (FUnIE-GAN and UWCNN both have official PyTorch/TF implementations).
 *   2. Export it to ONNX with a fixed or dynamic [1,3,H,W] input.
 *   3. Drop the .onnx file at models/funie-gan.onnx (or point
 *      uie.ai.model-path at it) and set uie.ai.enabled=true.
 */
@Service
public class OnnxEnhancementService {

    private static final Logger log = LoggerFactory.getLogger(OnnxEnhancementService.class);

    @Value("${uie.ai.enabled:false}")
    private boolean enabled;

    @Value("${uie.ai.model-path:models/funie-gan.onnx}")
    private String modelPath;

    @Value("${uie.ai.input-size:256}")
    private int inputSize;

    private OrtEnvironment environment;
    private OrtSession session;
    private volatile boolean modelLoaded = false;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("AI enhancement disabled (uie.ai.enabled=false). Using classical pipeline only.");
            return;
        }
        Path path = Path.of(modelPath);
        if (!Files.exists(path)) {
            log.warn("uie.ai.enabled=true but no model file found at '{}'. " +
                    "Falling back to the classical CV pipeline until a model is provided.", modelPath);
            return;
        }
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            session = environment.createSession(path.toString(), options);
            modelLoaded = true;
            log.info("Loaded ONNX enhancement model from '{}'.", modelPath);
        } catch (OrtException e) {
            log.error("Failed to load ONNX model at '{}': {}", modelPath, e.getMessage());
        }
    }

    public boolean isAvailable() {
        return modelLoaded;
    }

    public BufferedImage enhance(BufferedImage src) {
        if (!modelLoaded) {
            throw new ModelNotAvailableException(
                    "AI model is not loaded. Enable it and provide a valid ONNX file at " + modelPath);
        }
        try {
            int w = inputSize;
            int h = inputSize;

            BufferedImage resized = resize(src, w, h);
            float[] chw = toChwFloatArray(resized);

            long[] shape = {1, 3, h, w};
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(chw), shape)) {
                String inputName = session.getInputNames().iterator().next();
                try (OrtSession.Result result = session.run(java.util.Map.of(inputName, inputTensor))) {
                    float[][][][] output = (float[][][][]) result.get(0).getValue();
                    BufferedImage enhancedSmall = fromChwFloatArray(output[0], w, h);
                    return resize(enhancedSmall, src.getWidth(), src.getHeight());
                }
            }
        } catch (OrtException e) {
            throw new ImageProcessingException("ONNX inference failed: " + e.getMessage(), e);
        }
    }

    private float[] toChwFloatArray(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        float[] chw = new float[3 * w * h];
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        int plane = w * h;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            chw[i] = ((p >> 16) & 0xFF) / 255.0f;              // R plane
            chw[plane + i] = ((p >> 8) & 0xFF) / 255.0f;        // G plane
            chw[2 * plane + i] = (p & 0xFF) / 255.0f;           // B plane
        }
        return chw;
    }

    private BufferedImage fromChwFloatArray(float[][][] chw, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = to255(chw[0][y][x]);
                int g = to255(chw[1][y][x]);
                int b = to255(chw[2][y][x]);
                pixels[y * w + x] = (r << 16) | (g << 8) | b;
            }
        }
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    private int to255(float v) {
        return (int) Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    private BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return resized;
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) session.close();
            if (environment != null) environment.close();
        } catch (OrtException e) {
            log.warn("Error closing ONNX session: {}", e.getMessage());
        }
    }
}
