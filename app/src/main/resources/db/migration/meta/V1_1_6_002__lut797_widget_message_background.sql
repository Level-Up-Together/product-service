-- LUT-797: 위젯 문장 배경 이미지. 어드민이 문장마다 이미지를 첨부하면(업로드 API 가 준 절대 URL) 위젯이 그 문장을 보여 줄 때 배경으로 쓴다.
-- 없으면(NULL) 위젯은 하얀 배경 — 기존 문장은 그대로다.
ALTER TABLE widget_message ADD COLUMN IF NOT EXISTS background_image_url VARCHAR(500);
COMMENT ON COLUMN widget_message.background_image_url IS '위젯 배경 이미지 URL(LUT-797, 업로드 API 가 발급한 절대 주소). NULL 이면 하얀 배경';
