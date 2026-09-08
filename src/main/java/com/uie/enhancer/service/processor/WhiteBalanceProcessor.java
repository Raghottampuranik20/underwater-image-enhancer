package com.uie.enhancer.service.processor;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Gray-World white balance.
 *
 * Underwater images pick up a strong blue/green color cast because water
 * absorbs red wavelengths first. Gray-World assumes that, on average, a
 * natural scene's reflectance is neutral gray, so it rescales each channel
 * so the three channel means become equal.
 */
@Component
public class WhiteBalanceProcessor {

    public BufferedImage apply(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = src.getRGB(0, 0, width, height, null, 0, width);

        long sumR = 0, sumG = 0, sumB = 0;
        for (int p : pixels) {
            sumR += (p >> 16) & 0xFF;
            sumG += (p >> 8) & 0xFF;
            sumB += p & 0xFF;
        }
        long total = (long) width * height;
        double avgR = sumR / (double) total;
        double avgG = sumG / (double) total;
        double avgB = sumB / (double) total;
        double avgGray = (avgR + avgG + avgB) / 3.0;

        // Guard against divide-by-zero on degenerate (all-black) channels.
        double scaleR = avgR > 1e-6 ? avgGray / avgR : 1.0;
        double scaleG = avgG > 1e-6 ? avgGray / avgG : 1.0;
        double scaleB = avgB > 1e-6 ? avgGray / avgB : 1.0;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = clamp((int) Math.round(((p >> 16) & 0xFF) * scaleR));
            int g = clamp((int) Math.round(((p >> 8) & 0xFF) * scaleG));
            int b = clamp((int) Math.round((p & 0xFF) * scaleB));
            result[i] = (r << 16) | (g << 8) | b;
        }
        out.setRGB(0, 0, width, height, result, 0, width);
        return out;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
