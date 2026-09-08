package com.uie.enhancer.service;

import com.uie.enhancer.dto.EnhancementMethod;
import com.uie.enhancer.dto.EnhancementOptions;
import com.uie.enhancer.exception.ModelNotAvailableException;
import com.uie.enhancer.service.ai.OnnxEnhancementService;
import com.uie.enhancer.service.processor.ClaheProcessor;
import com.uie.enhancer.service.processor.GammaCorrectionProcessor;
import com.uie.enhancer.service.processor.RedChannelCompensator;
import com.uie.enhancer.service.processor.UnsharpMaskProcessor;
import com.uie.enhancer.service.processor.WhiteBalanceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * Orchestrates the full underwater image enhancement pipeline.
 *
 * CLASSICAL pipeline (always available, deterministic):
 *   1. White balance (Gray-World)      - removes the blue/green color cast.
 *   2. Red channel compensation        - restores attenuated red content.
 *   3. CLAHE on luminance              - recovers local contrast in murky areas.
 *   4. Gamma correction                - brightens midtones.
 *   5. Unsharp mask                    - restores edge detail lost to scattering.
 *
 * AI pipeline (optional): delegates to a loaded ONNX deep-learning model
 * instead of steps 1-5. See OnnxEnhancementService for how to enable it.
 */
@Service
public class ImageEnhancementService {

    private static final Logger log = LoggerFactory.getLogger(ImageEnhancementService.class);

    private final WhiteBalanceProcessor whiteBalanceProcessor;
    private final RedChannelCompensator redChannelCompensator;
    private final ClaheProcessor claheProcessor;
    private final GammaCorrectionProcessor gammaCorrectionProcessor;
    private final UnsharpMaskProcessor unsharpMaskProcessor;
    private final OnnxEnhancementService onnxEnhancementService;

    public ImageEnhancementService(WhiteBalanceProcessor whiteBalanceProcessor,
                                    RedChannelCompensator redChannelCompensator,
                                    ClaheProcessor claheProcessor,
                                    GammaCorrectionProcessor gammaCorrectionProcessor,
                                    UnsharpMaskProcessor unsharpMaskProcessor,
                                    OnnxEnhancementService onnxEnhancementService) {
        this.whiteBalanceProcessor = whiteBalanceProcessor;
        this.redChannelCompensator = redChannelCompensator;
        this.claheProcessor = claheProcessor;
        this.gammaCorrectionProcessor = gammaCorrectionProcessor;
        this.unsharpMaskProcessor = unsharpMaskProcessor;
        this.onnxEnhancementService = onnxEnhancementService;
    }

    public BufferedImage enhance(BufferedImage source, EnhancementOptions options) {
        EnhancementMethod method = options.getMethod() == null ? EnhancementMethod.AUTO : options.getMethod();

        boolean useAi = switch (method) {
            case AI -> true;
            case CLASSICAL -> false;
            case AUTO -> onnxEnhancementService.isAvailable();
        };

        if (useAi) {
            if (!onnxEnhancementService.isAvailable()) {
                if (method == EnhancementMethod.AI) {
                    throw new ModelNotAvailableException(
                            "AI method was requested but no ONNX model is loaded. " +
                                    "Use method=CLASSICAL or method=AUTO instead, or provide a model.");
                }
            } else {
                log.debug("Running AI (ONNX) enhancement pipeline.");
                return onnxEnhancementService.enhance(source);
            }
        }

        log.debug("Running classical CV enhancement pipeline.");
        return runClassicalPipeline(source, options);
    }

    private BufferedImage runClassicalPipeline(BufferedImage source, EnhancementOptions options) {
        BufferedImage stage1 = whiteBalanceProcessor.apply(source);
        BufferedImage stage2 = redChannelCompensator.apply(stage1, options.getRedAlpha());
        BufferedImage stage3 = claheProcessor.apply(stage2, options.getClipLimit(), options.getTileGrid());
        BufferedImage stage4 = gammaCorrectionProcessor.apply(stage3, options.getGamma());
        return unsharpMaskProcessor.apply(stage4, options.getSharpenAmount());
    }
}
