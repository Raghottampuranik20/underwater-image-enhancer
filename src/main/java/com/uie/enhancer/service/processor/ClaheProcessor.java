package com.uie.enhancer.service.processor;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Contrast Limited Adaptive Histogram Equalization, applied to the luminance
 * (Y) channel of a YCbCr conversion so color balance from earlier stages is
 * preserved while local contrast (murky, low-contrast underwater midtones)
 * is recovered.
 *
 * Standard CLAHE algorithm:
 *   1. Split the image into a grid of tiles.
 *   2. Build a 256-bin histogram per tile.
 *   3. Clip each bin at clipLimit * (tilePixelCount / 256) and redistribute
 *      the clipped mass uniformly across all bins (prevents noise/edge
 *      over-amplification that plain adaptive HE suffers from).
 *   4. Build a cumulative distribution function (CDF) per tile -> that is
 *      the tile's grayscale remapping function.
 *   5. For each pixel, bilinearly interpolate between the remapping
 *      functions of the four nearest tile centers, so tile boundaries don't
 *      produce visible seams.
 */
@Component
public class ClaheProcessor {

    public BufferedImage apply(BufferedImage src, double clipLimit, int tileGrid) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = src.getRGB(0, 0, width, height, null, 0, width);

        int[] y = new int[pixels.length];
        double[] cb = new double[pixels.length];
        double[] cr = new double[pixels.length];

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;

            double yy = 0.299 * r + 0.587 * g + 0.114 * b;
            double cbb = -0.168736 * r - 0.331264 * g + 0.5 * b + 128;
            double crr = 0.5 * r - 0.418688 * g - 0.081312 * b + 128;

            y[i] = (int) Math.round(yy);
            cb[i] = cbb;
            cr[i] = crr;
        }

        int tilesX = Math.max(1, tileGrid);
        int tilesY = Math.max(1, tileGrid);
        int tileWidth = (int) Math.ceil(width / (double) tilesX);
        int tileHeight = (int) Math.ceil(height / (double) tilesY);

        // Precompute a remapping LUT (0-255 -> 0-255) for every tile.
        int[][][] tileLuts = new int[tilesY][tilesX][];
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                tileLuts[ty][tx] = buildTileLut(y, width, height, tx, ty, tileWidth, tileHeight, clipLimit);
            }
        }

        int[] yEq = new int[y.length];
        for (int py = 0; py < height; py++) {
            // Locate the two nearest tile centers in Y and the interpolation weight.
            double fy = (py + 0.5) / tileHeight - 0.5;
            int ty0 = (int) Math.floor(fy);
            double wy = fy - ty0;
            int ty1 = ty0 + 1;
            ty0 = clampIndex(ty0, tilesY);
            ty1 = clampIndex(ty1, tilesY);

            for (int px = 0; px < width; px++) {
                double fx = (px + 0.5) / tileWidth - 0.5;
                int tx0 = (int) Math.floor(fx);
                double wx = fx - tx0;
                int tx1 = tx0 + 1;
                tx0 = clampIndex(tx0, tilesX);
                tx1 = clampIndex(tx1, tilesX);

                int idx = py * width + px;
                int v = y[idx];

                double v00 = tileLuts[ty0][tx0][v];
                double v01 = tileLuts[ty0][tx1][v];
                double v10 = tileLuts[ty1][tx0][v];
                double v11 = tileLuts[ty1][tx1][v];

                double top = v00 * (1 - wx) + v01 * wx;
                double bottom = v10 * (1 - wx) + v11 * wx;
                double value = top * (1 - wy) + bottom * wy;

                yEq[idx] = (int) Math.max(0, Math.min(255, Math.round(value)));
            }
        }

        int[] result = new int[pixels.length];
        for (int i = 0; i < result.length; i++) {
            double yy = yEq[i];
            double cbb = cb[i] - 128;
            double crr = cr[i] - 128;

            int r = clamp((int) Math.round(yy + 1.402 * crr));
            int g = clamp((int) Math.round(yy - 0.344136 * cbb - 0.714136 * crr));
            int b = clamp((int) Math.round(yy + 1.772 * cbb));
            result[i] = (r << 16) | (g << 8) | b;
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, result, 0, width);
        return out;
    }

    private int[] buildTileLut(int[] y, int width, int height, int tx, int ty,
                                int tileWidth, int tileHeight, double clipLimit) {
        int x0 = tx * tileWidth;
        int y0 = ty * tileHeight;
        int x1 = Math.min(width, x0 + tileWidth);
        int y1 = Math.min(height, y0 + tileHeight);

        int[] hist = new int[256];
        int count = 0;
        for (int py = y0; py < y1; py++) {
            int rowBase = py * width;
            for (int px = x0; px < x1; px++) {
                hist[y[rowBase + px]]++;
                count++;
            }
        }
        if (count == 0) {
            int[] identity = new int[256];
            for (int i = 0; i < 256; i++) identity[i] = i;
            return identity;
        }

        double clipValue = Math.max(1.0, clipLimit * count / 256.0);
        int excess = 0;
        for (int i = 0; i < 256; i++) {
            if (hist[i] > clipValue) {
                excess += (int) (hist[i] - clipValue);
                hist[i] = (int) clipValue;
            }
        }
        int redistribute = excess / 256;
        int remainder = excess - redistribute * 256;
        for (int i = 0; i < 256; i++) {
            hist[i] += redistribute;
            if (i < remainder) {
                hist[i] += 1;
            }
        }

        int[] lut = new int[256];
        long cdf = 0;
        for (int i = 0; i < 256; i++) {
            cdf += hist[i];
            lut[i] = (int) Math.round((cdf / (double) count) * 255.0);
        }
        return lut;
    }

    private int clampIndex(int idx, int size) {
        return Math.max(0, Math.min(size - 1, idx));
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
