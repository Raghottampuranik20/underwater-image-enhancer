# Model directory

Place an ONNX underwater-enhancement model here (default expected filename:
`funie-gan.onnx`) and set `uie.ai.enabled=true` in `application.yml` (or via
`--uie.ai.enabled=true` / `UIE_AI_ENABLED=true` env var) to activate the AI
pipeline.

Expected model shape: input `[1, 3, H, W]` float32, values normalized to
`[0, 1]`, output the same shape. `H`/`W` should match `uie.ai.input-size`
(default 256; the service resizes to/from this internally).

Good starting points to train or convert:
- FUnIE-GAN (fast underwater image enhancement GAN)
- UWCNN (underwater CNN, multiple water-type variants)
- Water-Net

Without a model file here, the backend runs perfectly well using only the
classical CV pipeline (white balance, red-channel compensation, CLAHE,
gamma, unsharp mask) — no AI dependency required.
