package com.uie.enhancer.service.processor;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Unsharp masking: sharpened = original + amount * (original - blurred).
 * Water scattering softens edges, so a mild sharpening pass at the end of
 * the pipeline restores perceived detail without reintroducing color noise
 * (it operates identically per RGB channel, after color correction).
 */
@Component
public class UnsharpMaskProcessor {

    public BufferedImage apply(BufferedImage src, double amount) {
        if (amount <= 0) {
            return src;
        }
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = src.getRGB(0, 0, width, height, null, 0, width);
        int[] blurred = gaussianBlur3x3(pixels, width, height);

        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int orig = pixels[i];
            int blur = blurred[i];

            int or_ = (orig >> 16) & 0xFF, og = (orig >> 8) & 0xFF, ob = orig & 0xFF;
            int br = (blur >> 16) & 0xFF, bg = (blur >> 8) & 0xFF, bb = blur & 0xFF;

            int r = clamp((int) Math.round(or_ + amount * (or_ - br)));
            int g = clamp((int) Math.round(og + amount * (og - bg)));
            int b = clamp((int) Math.round(ob + amount * (ob - bb)));
            result[i] = (r << 16) | (g << 8) | b;
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, result, 0, width);
        return out;
    }

    private int[] gaussianBlur3x3(int[] pixels, int width, int height) {
        // Separable-ish 3x3 approximate Gaussian kernel:
        // 1 2 1
        // 2 4 2   (normalized by 16)
        // 1 2 1
        int[] result = new int[pixels.length];
        int[] kernel = {1, 2, 1, 2, 4, 2, 1, 2, 1};

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int sumR = 0, sumG = 0, sumB = 0, k = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = clampIndex(py + dy, height);
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = clampIndex(px + dx, width);
                        int p = pixels[ny * width + nx];
                        int w = kernel[k++];
                        sumR += ((p >> 16) & 0xFF) * w;
                        sumG += ((p >> 8) & 0xFF) * w;
                        sumB += (p & 0xFF) * w;
                    }
                }
                int r = sumR / 16, g = sumG / 16, b = sumB / 16;
                result[py * width + px] = (r << 16) | (g << 8) | b;
            }
        }
        return result;
    }

    private int clampIndex(int idx, int size) {
        return Math.max(0, Math.min(size - 1, idx));
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
