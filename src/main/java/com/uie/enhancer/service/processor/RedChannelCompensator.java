package com.uie.enhancer.service.processor;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Compensates the red channel, which attenuates fastest with depth in water.
 *
 * Formula (normalized intensities in [0,1]):
 *   Ir'(x) = Ir(x) + alpha * (meanIg - meanIr) * (1 - Ir(x)) * Ig(x)
 *
 * This is the standard "Underwater Dark Channel Prior" style red compensation
 * used ahead of white balancing in several underwater restoration papers -
 * it pulls red back up using the (usually healthier) green channel as a guide,
 * without over-amplifying noise the way a naive channel multiply would.
 */
@Component
public class RedChannelCompensator {

    public BufferedImage apply(BufferedImage src, double alpha) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = src.getRGB(0, 0, width, height, null, 0, width);

        double sumR = 0, sumG = 0;
        for (int p : pixels) {
            sumR += ((p >> 16) & 0xFF) / 255.0;
            sumG += ((p >> 8) & 0xFF) / 255.0;
        }
        int total = width * height;
        double meanR = sumR / total;
        double meanG = sumG / total;

        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            double r = ((p >> 16) & 0xFF) / 255.0;
            double g = ((p >> 8) & 0xFF) / 255.0;
            int b = p & 0xFF;

            double rNew = r + alpha * (meanG - meanR) * (1 - r) * g;
            int rByte = clamp((int) Math.round(rNew * 255));
            int gByte = clamp((int) Math.round(g * 255));
            result[i] = (rByte << 16) | (gByte << 8) | b;
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, result, 0, width);
        return out;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
