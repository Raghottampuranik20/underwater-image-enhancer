(() => {
  const dropzone = document.getElementById('dropzone');
  const fileInput = document.getElementById('fileInput');
  const enhanceBtn = document.getElementById('enhanceBtn');
  const consoleMsg = document.getElementById('consoleMsg');

  const compareEmpty = document.getElementById('compareEmpty');
  const compareFrame = document.getElementById('compareFrame');
  const imgBefore = document.getElementById('imgBefore');
  const imgAfter = document.getElementById('imgAfter');
  const compareClip = document.getElementById('compareClip');
  const compareRange = document.getElementById('compareRange');
  const resultActions = document.getElementById('resultActions');
  const downloadLink = document.getElementById('downloadLink');
  const resultDims = document.getElementById('resultDims');

  const comparisonExplanation = document.getElementById('comparisonExplanation');

  const methodGroup = document.getElementById('methodGroup');
  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');

  const sliders = {
    clipLimit: document.getElementById('clipLimit'),
    gamma: document.getElementById('gamma'),
    redAlpha: document.getElementById('redAlpha'),
    sharpen: document.getElementById('sharpen'),
  };
  const sliderOutputs = {
    clipLimit: document.getElementById('clipLimitVal'),
    gamma: document.getElementById('gammaVal'),
    redAlpha: document.getElementById('redAlphaVal'),
    sharpen: document.getElementById('sharpenVal'),
  };

  let selectedFile = null;
  let selectedMethod = 'AUTO';

  // --- health check -------------------------------------------------
  fetch('/api/v1/health')
    .then((r) => r.json())
    .then((data) => {
      if (data.aiModelLoaded) {
        statusDot.className = 'status-dot ok';
        statusText.textContent = 'AI model loaded';
      } else {
        statusDot.className = 'status-dot off';
        statusText.textContent = 'classical pipeline only';
      }
    })
    .catch(() => {
      statusDot.className = 'status-dot err';
      statusText.textContent = 'backend unreachable';
    });

  // --- method segmented control --------------------------------------
  methodGroup.addEventListener('click', (e) => {
    const btn = e.target.closest('.segmented-btn');
    if (!btn) return;
    methodGroup.querySelectorAll('.segmented-btn').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    selectedMethod = btn.dataset.value;
  });

  // --- sliders ---------------------------------------------------------
  Object.entries(sliders).forEach(([key, el]) => {
    el.addEventListener('input', () => {
      const decimals = key === 'gamma' ? 2 : 1;
      sliderOutputs[key].textContent = Number(el.value).toFixed(decimals);
    });
  });

  // --- file selection ----------------------------------------------------
  dropzone.addEventListener('click', () => fileInput.click());
  dropzone.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      fileInput.click();
    }
  });
  ['dragenter', 'dragover'].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.add('dragover');
    })
  );
  ['dragleave', 'drop'].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
    })
  );
  dropzone.addEventListener('drop', (e) => {
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) handleFile(file);
  });
  fileInput.addEventListener('change', () => {
    if (fileInput.files[0]) handleFile(fileInput.files[0]);
  });

  function handleFile(file) {
    if (!file.type.startsWith('image/')) {
      setMessage('Please choose an image file.', 'err');
      return;
    }
    selectedFile = file;
    enhanceBtn.disabled = false;
    setMessage(`Loaded ${file.name} — ready to enhance.`, '');

    const reader = new FileReader();
    reader.onload = () => {
      imgBefore.src = reader.result;
    };
    reader.readAsDataURL(file);
  }

  // --- compare slider -----------------------------------------------------
  compareRange.addEventListener('input', () => {
    compareClip.style.width = compareRange.value + '%';
    document.getElementById('compareHandle').style.left = compareRange.value + '%';
  });

  // --- enhance request --------------------------------------------------
  enhanceBtn.addEventListener('click', async () => {
    if (!selectedFile) return;
    enhanceBtn.disabled = true;
    setMessage('Enhancing…', '');

    const params = new URLSearchParams({
      method: selectedMethod,
      clipLimit: sliders.clipLimit.value,
      gamma: sliders.gamma.value,
      redAlpha: sliders.redAlpha.value,
      sharpenAmount: sliders.sharpen.value,
    });

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const res = await fetch(`/api/v1/enhance/base64?${params.toString()}`, {
        method: 'POST',
        body: formData,
      });

      if (!res.ok) {
        const err = await safeJson(res);
        throw new Error(err?.message || `Request failed (${res.status})`);
      }

      const data = await res.json();
      imgAfter.src = `data:image/png;base64,${data.imageBase64}`;
      compareEmpty.hidden = true;
      compareFrame.hidden = false;
      resultActions.hidden = false;
      downloadLink.href = imgAfter.src;
      resultDims.textContent = `${data.width}×${data.height} · ${data.method}`;
      comparisonExplanation.hidden = false;
      setMessage('Done.', 'ok');
    } catch (err) {
      setMessage(err.message || 'Something went wrong.', 'err');
    } finally {
      enhanceBtn.disabled = false;
    }
  });

  async function safeJson(res) {
    try {
      return await res.json();
    } catch {
      return null;
    }
  }

  function setMessage(text, kind) {
    consoleMsg.textContent = text;
    consoleMsg.className = 'console-msg' + (kind ? ' ' + kind : '');
  }
})();
