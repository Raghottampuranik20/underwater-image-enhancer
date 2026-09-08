package com.uie.enhancer.service;

import com.uie.enhancer.dto.EnhancementMethod;
import com.uie.enhancer.dto.EnhancementOptions;
import com.uie.enhancer.service.ai.OnnxEnhancementService;
import com.uie.enhancer.service.processor.ClaheProcessor;
import com.uie.enhancer.service.processor.GammaCorrectionProcessor;
import com.uie.enhancer.service.processor.RedChannelCompensator;
import com.uie.enhancer.service.processor.UnsharpMaskProcessor;
import com.uie.enhancer.service.processor.WhiteBalanceProcessor;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageEnhancementServiceTest {

    /** Builds a synthetic "underwater" image: strong blue/green cast, weak red. */
    private BufferedImage buildTintedImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 20 + (x % 30);   // weak, low-variance red (typical underwater attenuation)
                int g = 120 + (y % 60);
                int b = 160 + (x % 40);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private long meanChannel(BufferedImage img, int shift) {
        int w = img.getWidth(), h = img.getHeight();
        long sum = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                sum += (p >> shift) & 0xFF;
            }
        }
        return sum / ((long) w * h);
    }

    @Test
    void classicalPipeline_preservesDimensions_andBoostsRedChannel() {
        OnnxEnhancementService onnx = mock(OnnxEnhancementService.class);
        when(onnx.isAvailable()).thenReturn(false);

        ImageEnhancementService service = new ImageEnhancementService(
                new WhiteBalanceProcessor(),
                new RedChannelCompensator(),
                new ClaheProcessor(),
                new GammaCorrectionProcessor(),
                new UnsharpMaskProcessor(),
                onnx
        );

        BufferedImage source = buildTintedImage(64, 64);
        long originalRedMean = meanChannel(source, 16);

        EnhancementOptions options = new EnhancementOptions();
        options.setMethod(EnhancementMethod.CLASSICAL);

        BufferedImage enhanced = service.enhance(source, options);

        assertEquals(source.getWidth(), enhanced.getWidth());
        assertEquals(source.getHeight(), enhanced.getHeight());

        long enhancedRedMean = meanChannel(enhanced, 16);
        assertTrue(enhancedRedMean > originalRedMean,
                "Red channel mean should increase after white balance + red compensation");
    }

    @Test
    void autoMethod_fallsBackToClassical_whenNoModelLoaded() {
        OnnxEnhancementService onnx = mock(OnnxEnhancementService.class);
        when(onnx.isAvailable()).thenReturn(false);

        ImageEnhancementService service = new ImageEnhancementService(
                new WhiteBalanceProcessor(),
                new RedChannelCompensator(),
                new ClaheProcessor(),
                new GammaCorrectionProcessor(),
                new UnsharpMaskProcessor(),
                onnx
        );

        BufferedImage source = buildTintedImage(32, 32);
        EnhancementOptions options = new EnhancementOptions();
        options.setMethod(EnhancementMethod.AUTO);

        assertDoesNotThrow(() -> service.enhance(source, options));
    }
}
