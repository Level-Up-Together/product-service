-- LUT-775: 홈 화면 위젯 시간대별 문장(meta_db). 어드민이 관리하고(LUT-776), 위젯은 활성 문장 전체를 받아 기기에서 시간대·완료 여부로 고른다(LUT-777).
-- 시간대는 유저 로컬 시각 [start_hour, end_hour). start_hour > end_hour 면 자정을 넘는 구간(22~2 = 22·23·0·1시).
CREATE TABLE IF NOT EXISTS widget_message (
    id             BIGSERIAL PRIMARY KEY,
    start_hour     INTEGER      NOT NULL,
    end_hour       INTEGER      NOT NULL,
    done_condition VARCHAR(20)  NOT NULL,
    text_ko        VARCHAR(200) NOT NULL,
    text_en        VARCHAR(200),
    text_ja        VARCHAR(200),
    text_ar        VARCHAR(200),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    modified_at    TIMESTAMP,
    CONSTRAINT chk_widget_message_start_hour CHECK (start_hour BETWEEN 0 AND 23),
    CONSTRAINT chk_widget_message_end_hour CHECK (end_hour BETWEEN 1 AND 24),
    CONSTRAINT chk_widget_message_hour_range CHECK (start_hour <> end_hour),
    CONSTRAINT chk_widget_message_condition CHECK (done_condition IN ('NOT_DONE', 'DONE', 'ALL'))
);
COMMENT ON TABLE widget_message IS '홈 화면 위젯 시간대별 문장 (LUT-775). 어드민 관리, 위젯이 기기에서 고른다';
COMMENT ON COLUMN widget_message.start_hour IS '시작 시각(유저 로컬, 0~23, 포함)';
COMMENT ON COLUMN widget_message.end_hour IS '끝 시각(유저 로컬, 1~24, 미포함). start_hour 보다 작으면 자정을 넘는 구간';
COMMENT ON COLUMN widget_message.done_condition IS 'NOT_DONE(오늘 완료 전) / DONE(완료 후) / ALL';
COMMENT ON COLUMN widget_message.text_ko IS '문장(ko, 필수). {streak} 는 위젯이 연속 실천 일수로 바꾼다';
COMMENT ON COLUMN widget_message.text_en IS '문장(en). 비면 ko';
COMMENT ON COLUMN widget_message.text_ja IS '문장(ja). 비면 en → ko';
COMMENT ON COLUMN widget_message.text_ar IS '문장(ar). 비면 en → ko';

-- 초기 문장: 시간대 4개(아침 6~12·낮 12~18·저녁 18~22·밤 22~6) × 조건 2개. 완료 전 문장에는 {streak} 를 넣지 않는다(0 일 수 있다).
INSERT INTO widget_message (start_hour, end_hour, done_condition, text_ko, text_en, text_ja, text_ar, sort_order)
VALUES
    (6, 12, 'NOT_DONE',
     '아침의 한 걸음이 하루를 바꿔요. 오늘 미션을 시작해 볼까요?',
     'One small step this morning can change your day. Shall we start today''s mission?',
     '朝の一歩が一日を変えます。今日のミッションを始めましょうか？',
     'خطوة صغيرة في الصباح تغيّر يومك. هل نبدأ مهمة اليوم؟',
     1),
    (6, 12, 'DONE',
     '아침부터 해냈어요! {streak}일 연속 실천 중이에요.',
     'Done before noon! You''re on a {streak}-day streak.',
     '朝からやり遂げました！{streak}日連続で実践中です。',
     'أنجزتها منذ الصباح! أيامك المتتالية: {streak}',
     2),
    (12, 18, 'NOT_DONE',
     '점심 뒤 10분이면 충분해요. 오늘의 미션 하나만 해 볼까요?',
     'Ten minutes after lunch is enough. How about one mission today?',
     'お昼のあと10分で十分です。今日のミッションをひとつやってみませんか？',
     'عشر دقائق بعد الغداء تكفي. ما رأيك في مهمة واحدة اليوم؟',
     3),
    (12, 18, 'DONE',
     '오늘 미션 완료! {streak}일째 연속 실천이에요.',
     'Today''s mission is done! Day {streak} of your streak.',
     '今日のミッション完了！連続{streak}日目です。',
     'أنجزت مهمة اليوم! أيامك المتتالية: {streak}',
     4),
    (18, 22, 'NOT_DONE',
     '하루가 끝나기 전에, 작은 실천 하나로 연속을 지켜요.',
     'Before the day ends, keep your streak with one small habit.',
     '一日が終わる前に、小さな実践ひとつで連続記録を守りましょう。',
     'قبل أن ينتهي اليوم، حافظ على سلسلتك بعادة صغيرة واحدة.',
     5),
    (18, 22, 'DONE',
     '오늘도 해냈어요. 편안한 저녁 보내세요.',
     'You did it again today. Enjoy your evening.',
     '今日もやり遂げました。ゆっくり過ごしてくださいね。',
     'لقد أنجزتها اليوم أيضًا. استمتع بمسائك.',
     6),
    (22, 6, 'NOT_DONE',
     '아직 늦지 않았어요. 자기 전 5분, 오늘을 채워 볼까요?',
     'It''s not too late. Five minutes before bed to finish today?',
     'まだ間に合います。寝る前の5分で今日を締めくくりませんか？',
     'لم يفت الأوان بعد. خمس دقائق قبل النوم لتكمل يومك؟',
     7),
    (22, 6, 'DONE',
     '{streak}일 연속 실천, 오늘도 지켰어요. 푹 쉬세요.',
     '{streak} days in a row, kept again today. Rest well.',
     '{streak}日連続、今日も守りました。ゆっくり休んでください。',
     'حافظت على سلسلتك اليوم أيضًا. أيامك المتتالية: {streak}',
     8);
