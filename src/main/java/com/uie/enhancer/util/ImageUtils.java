package com.uie.enhancer.util;

import com.uie.enhancer.exception.ImageProcessingException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageUtils {

    private ImageUtils() {
    }

    public static BufferedImage readImage(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new ImageProcessingException("Could not decode file as an image. " +
                        "Supported formats: PNG, JPEG, BMP.");
            }
            return toRgbCopy(img);
        } catch (IOException e) {
            throw new ImageProcessingException("Failed to read uploaded image.", e);
        }
    }

    /** Ensures a plain, alpha-free TYPE_INT_RGB copy so all processors can assume the same layout. */
    public static BufferedImage toRgbCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.getGraphics().drawImage(src, 0, 0, null);
        return copy;
    }

    public static byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageProcessingException("Failed to encode enhanced image.", e);
        }
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static int clampToByte(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
