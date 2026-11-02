# models/nsfw.onnx — 이미지 검수(NSFW) 모델 (LUT-651)

| 항목 | 값 |
|---|---|
| 원본 | Yahoo `open_nsfw` (ResNet-50, Caffe) — https://github.com/yahoo/open_nsfw, **BSD-2-Clause** (Copyright 2016 Yahoo Inc.) → `LICENSE-open_nsfw-BSD-2-Clause.txt` |
| ONNX 변환본 출처 | PyPI `opennsfw-onnx` 0.1.0 (2026-07-04, Apache-2.0, Gustavo Gawryszewski) 휠에 동봉된 `opennsfw_onnx/open-nsfw.onnx` 를 그대로 복사 |
| SHA-256 | `864bb37bf8863564b87eb330ab8c785a79a773f4e7c43cb96db52ed8611305fa` (휠의 `open-nsfw.onnx.sha256` 과 동일) |
| 크기 | 23.6 MB (jar 265 MB → 약 289 MB) |
| 입력 | `input:0` float `[N, 224, 224, 3]` — **NHWC**, BGR 순서, 평균 `[104, 117, 123]` 차감 (0~255 스케일, 추가 정규화 없음) |
| 출력 | `outputs` float `[N, 2]` = `[sfw, nsfw]` (softmax, 합 1) |
| 전처리(참조 구현) | 256×256 bilinear 리사이즈 → 가운데 224×224 자르기(오프셋 16) → (선택) JPEG 재인코딩 → BGR 평균 차감. `OnnxNsfwModerationService.preprocessImage` 는 JPEG 재인코딩만 생략 |
| 런타임 | `com.microsoft.onnxruntime:onnxruntime:1.17.3` — jar 에 linux-x64 · linux-aarch64(prod t4g) · osx 네이티브 동봉 |
| 판정 | `moderation.image.onnx.nsfw-threshold`(기본 0.8) 이상이면 부적절. NSFW 한 축만 판정하며 폭력·혐오는 거르지 않는다 |

레이아웃은 기동 시 세션 입력 모양으로 자동 감지한다(`[N,3,224,224]` 면 NCHW). 모델을 바꾸면 이 표의 SHA-256·입출력을 갱신하고 `app` 모듈 `OnnxNsfwModelSmokeTest` 가 통과하는지 확인할 것.
