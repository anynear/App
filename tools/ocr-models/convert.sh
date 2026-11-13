#!/bin/sh
# Makes the text recognition models of the Android app (ADR 0078 §2): PP-OCRv5 mobile from PaddleOCR (Apache-2.0),
# as ONNX from Hugging Face (fixed revision), fixed to one input size and converted to LiteRT (float32).
# Result: androidApp/src/main/assets/ocr/{det.tflite,rec.tflite,keys.json}. Needs Python 3.12.
set -eu
REPO=nakamura196/ppocrv5-mobile-web-onnx
REV=dc7e2271c00d961a4c0a38dcebe23beb735ba33e
OUT="$(cd "$(dirname "$0")/../.." && pwd)/androidApp/src/main/assets/ocr"
WORK="${TMPDIR:-/tmp}/ocr-models"
mkdir -p "$WORK" && cd "$WORK"
python3 -m venv venv
./venv/bin/pip -q install "numpy<2" onnx==1.20.1 onnxsim==0.8.1 onnx2tf==2.4.3 tensorflow==2.19.1 tf-keras \
  onnx_graphsurgeon sng4onnx psutil ai-edge-litert==2.1.2
for f in PP-OCRv5_mobile_det.onnx latin_PP-OCRv5_mobile_rec.onnx latin_PP-OCRv5_mobile_rec.dict.json; do
  curl -sfL -o "$f" "https://huggingface.co/$REPO/resolve/$REV/$f"
done
shasum -a 256 -c <<SUMS
cdb211d662be32b1f386345c2533dcc059784f81ea2bfe6bd16e59880cbc8669  PP-OCRv5_mobile_det.onnx
da3225d55ba0fc1b43eb970ff24053c084a3b57b57bc1d4e1d063817594d0ce5  latin_PP-OCRv5_mobile_rec.onnx
705592cc40b955ac82cdfdb049ec75f0a9041824d524eeb946a913a34d285409  latin_PP-OCRv5_mobile_rec.dict.json
SUMS
./venv/bin/python - <<'PY'
import onnx
from onnxsim import simplify
for src, dst, shape in [("PP-OCRv5_mobile_det.onnx", "det.onnx", [1, 3, 960, 960]),
                        ("latin_PP-OCRv5_mobile_rec.onnx", "rec.onnx", [1, 3, 48, 960])]:
    model, ok = simplify(onnx.load(src), overwrite_input_shapes={"x": shape})
    assert ok
    onnx.save(model, dst)
PY
./venv/bin/onnx2tf -i det.onnx -o det -n >/dev/null
./venv/bin/onnx2tf -i rec.onnx -o rec -n >/dev/null
cp det/det_float32.tflite "$OUT/det.tflite"
cp rec/rec_float32.tflite "$OUT/rec.tflite"
cp latin_PP-OCRv5_mobile_rec.dict.json "$OUT/keys.json"
# Expected (08.11.2026): det 21d34be1…c7c5, rec 89c26b7d…af7c
shasum -a 256 "$OUT/det.tflite" "$OUT/rec.tflite"
