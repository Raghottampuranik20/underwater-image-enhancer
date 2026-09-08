package com.uie.enhancer.dto;

/**
 * Which pipeline should be used to enhance the image.
 *
 * AUTO       - try the AI/ONNX model if it is enabled and loaded successfully,
 *              otherwise fall back to the classical pipeline.
 * AI         - force the ONNX model; returns an error if no model is loaded.
 * CLASSICAL  - always use the deterministic CV pipeline (white balance,
 *              red-channel compensation, CLAHE, gamma, sharpening).
 */
public enum EnhancementMethod {
    AUTO,
    AI,
    CLASSICAL
}
