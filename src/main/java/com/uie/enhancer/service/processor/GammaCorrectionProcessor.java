package com.uie.enhancer.service.processor;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Per-channel gamma correction: out = 255 * (in/255)^gamma.
 * gamma &lt; 1 brightens midtones (typical for the dim, murky look of
 * underwater shots); gamma &gt; 1 darkens them.
 */
@Component
public class GammaCorrectionProcessor {

    public BufferedImage apply(BufferedImage src, double gamma) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = src.getRGB(0, 0, width, height, null, 0, width);

        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            double normalized = i / 255.0;
            lut[i] = (int) Math.max(0, Math.min(255, Math.round(255.0 * Math.pow(normalized, gamma))));
        }

        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = lut[(p >> 16) & 0xFF];
            int g = lut[(p >> 8) & 0xFF];
            int b = lut[p & 0xFF];
            result[i] = (r << 16) | (g << 8) | b;
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, result, 0, width);
        return out;
    }
}
