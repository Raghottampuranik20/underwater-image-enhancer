package com.uie.enhancer.dto;

/**
 * Tunable parameters for the enhancement pipeline. All fields have sane
 * defaults sourced from application.yml so every field is optional on the
 * request.
 */
public class EnhancementOptions {

    private EnhancementMethod method = EnhancementMethod.AUTO;

    /** CLAHE clip limit; higher = more local contrast, more noise amplification. */
    private double clipLimit = 3.0;

    /** CLAHE tile grid size (tileGrid x tileGrid tiles). */
    private int tileGrid = 8;

    /** Gamma correction exponent; <1 brightens midtones, >1 darkens them. */
    private double gamma = 0.85;

    /** Strength of the red-channel attenuation compensation, typically 0-2. */
    private double redAlpha = 1.0;

    /** Strength of the unsharp-mask sharpening pass, typically 0-1.5. */
    private double sharpenAmount = 0.6;

    public EnhancementMethod getMethod() {
        return method;
    }

    public void setMethod(EnhancementMethod method) {
        this.method = method;
    }

    public double getClipLimit() {
        return clipLimit;
    }

    public void setClipLimit(double clipLimit) {
        this.clipLimit = clipLimit;
    }

    public int getTileGrid() {
        return tileGrid;
    }

    public void setTileGrid(int tileGrid) {
        this.tileGrid = tileGrid;
    }

    public double getGamma() {
        return gamma;
    }

    public void setGamma(double gamma) {
        this.gamma = gamma;
    }

    public double getRedAlpha() {
        return redAlpha;
    }

    public void setRedAlpha(double redAlpha) {
        this.redAlpha = redAlpha;
    }

    public double getSharpenAmount() {
        return sharpenAmount;
    }

    public void setSharpenAmount(double sharpenAmount) {
        this.sharpenAmount = sharpenAmount;
    }
}
