# Underwater Image Enhancement — Java Backend

A Spring Boot 3 (Java 17) backend that enhances underwater photos/video
frames. It ships with a fully working **classical computer-vision pipeline**
(no external model needed) plus an **optional AI hook** that runs a real
ONNX deep-learning model (e.g. FUnIE-GAN, UWCNN) when one is provided.

## Why images need this

Water absorbs red light first and scatters blue/green light, so underwater
photos look flat, blue/green-tinted, low-contrast, and hazy. This service
reverses that with a pipeline built specifically for that physics:

| Stage | Purpose |
|---|---|
| 1. Gray-World white balance | Removes the blue/green color cast |
| 2. Red-channel compensation | Restores the attenuated red channel using the green channel as a guide |
| 3. CLAHE (on luminance, YCbCr) | Recovers local contrast in murky/hazy regions without blowing out highlights |
| 4. Gamma correction | Brightens midtones |
| 5. Unsharp masking | Restores edge detail softened by scattering |

Each stage is a separate, independently testable `@Component` in
`service/processor/`.

## Project layout

```
underwater-image-enhancer/
├── pom.xml
├── models/                          # drop an .onnx model here (optional)
├── src/main/java/com/uie/enhancer/
│   ├── UnderwaterImageEnhancerApplication.java
│   ├── controller/
│   │   ├── EnhancementController.java   # POST /api/v1/enhance, /enhance/base64
│   │   └── HealthController.java        # GET  /api/v1/health
│   ├── dto/
│   │   ├── EnhancementMethod.java       # AUTO | AI | CLASSICAL
│   │   ├── EnhancementOptions.java
│   │   └── ErrorResponse.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ImageProcessingException.java
│   │   └── ModelNotAvailableException.java
│   ├── service/
│   │   ├── ImageEnhancementService.java # orchestrator: picks AI vs classical
│   │   ├── ai/OnnxEnhancementService.java
│   │   └── processor/
│   │       ├── WhiteBalanceProcessor.java
│   │       ├── RedChannelCompensator.java
│   │       ├── ClaheProcessor.java
│   │       ├── GammaCorrectionProcessor.java
│   │       └── UnsharpMaskProcessor.java
│   └── util/ImageUtils.java
├── src/main/resources/
│   ├── application.yml
│   └── static/                          # front page (served at "/")
│       ├── index.html
│       ├── styles.css
│       └── app.js
└── src/test/java/.../ImageEnhancementServiceTest.java
```

## Build & run

Requires JDK 17+ and Maven, with normal internet access to Maven Central
(not available in the sandbox this was generated in — build it on your own
machine or CI).

```bash
cd underwater-image-enhancer
mvn clean package
java -jar target/underwater-image-enhancer.jar
# or, for development:
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

## Front page

Open **`http://localhost:8080`** in a browser. Spring Boot serves the static
UI in `src/main/resources/static/` directly — no separate frontend server,
build step, or framework needed:

- Drag-and-drop (or click-to-choose) photo upload
- Method switch: Auto / Classical / AI
- Sliders for local contrast, brightness, red recovery, and sharpening
- Before/after comparison slider over the result
- Live backend status pill (shows whether an AI model is loaded)
- PNG download of the enhanced result

It's plain HTML/CSS/JS (`index.html`, `styles.css`, `app.js`) calling the
same REST API described below via `fetch`, so it's a genuine end-to-end demo
of the Java backend — not a mock.

## API

### `POST /api/v1/enhance` — returns a PNG image

```bash
curl -F "file=@dive_photo.jpg" \
  "http://localhost:8080/api/v1/enhance?method=AUTO&clipLimit=3&gamma=0.85&redAlpha=1.0&sharpenAmount=0.6" \
  -o enhanced.png
```

Query params (all optional):

| Param | Default | Meaning |
|---|---|---|
| `method` | `AUTO` | `AUTO` (AI if loaded, else classical), `AI` (force AI, errors if unavailable), `CLASSICAL` |
| `clipLimit` | `3.0` | CLAHE clip limit — higher = more local contrast/noise |
| `tileGrid` | `8` | CLAHE tile grid size (NxN tiles) |
| `gamma` | `0.85` | Gamma exponent, `<1` brightens |
| `redAlpha` | `1.0` | Strength of red-channel compensation |
| `sharpenAmount` | `0.6` | Unsharp mask strength |

### `POST /api/v1/enhance/base64` — returns JSON

```bash
curl -F "file=@dive_photo.jpg" \
  "http://localhost:8080/api/v1/enhance/base64?method=CLASSICAL"
```

```json
{
  "method": "CLASSICAL",
  "width": 1920,
  "height": 1080,
  "imageBase64": "iVBORw0KGgoAAAANSUhEUgAA..."
}
```

### `GET /api/v1/health`

```json
{ "status": "UP", "aiModelLoaded": false }
```

## Enabling the AI (ONNX) pipeline

The classical pipeline runs standalone with zero configuration. To add real
deep-learning enhancement:

1. Get a model exported to ONNX with a `[1, 3, H, W]` float32 input/output
   in `[0, 1]` range (e.g. export FUnIE-GAN or UWCNN from PyTorch/TF via
   `torch.onnx.export` / `tf2onnx`).
2. Place it at `models/funie-gan.onnx` (or set `uie.ai.model-path`).
3. Set `uie.ai.enabled=true` (in `application.yml`, `--uie.ai.enabled=true`,
   or the `UIE_AI_ENABLED=true` env var) and `uie.ai.input-size` to match
   the model's expected resolution.
4. Restart the app; `/api/v1/health` should report `"aiModelLoaded": true`.
   Call the enhance endpoints with `method=AI` or `method=AUTO`.

If no model is present, the app logs a warning at startup and every request
silently uses the classical pipeline — nothing breaks.

## Testing

```bash
mvn test
```

`ImageEnhancementServiceTest` builds a synthetic blue/green-tinted image and
asserts the pipeline preserves dimensions and measurably boosts the
attenuated red channel — a quick sanity check that the physics-motivated
correction is actually doing something.

## Extending

- Swap `WhiteBalanceProcessor`'s Gray-World assumption for Shades-of-Gray or
  Perfect Reflector if your dataset skews non-neutral.
- Add a batch endpoint that accepts a ZIP/multiple files for video-frame
  pipelines.
- Add async processing (e.g. Spring `@Async` + a job-status endpoint) if you
  plan to enhance large video files rather than single frames.
