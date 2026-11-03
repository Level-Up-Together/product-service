package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.policy;

import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.SignupAttribution;
import java.util.Locale;

/**
 * LUT-599: 가입 채널 결정 규칙 — <b>서버가 정한다</b>. 클라이언트는 UTM 원문·초대 코드만 보내고 채널 이름을 보내지 않는다.
 *
 * <table>
 * <tr><th>우선순위</th><th>조건</th><th>signup_channel</th></tr>
 * <tr><td>1</td><td>초대 코드 있음</td><td>{@code invite}</td></tr>
 * <tr><td>2</td><td>utm_source 있음</td><td>utm_source 소문자 (50자)</td></tr>
 * <tr><td>3</td><td>기기 ios/ipad/android (앱 웹뷰)</td><td>{@code store_ios} / {@code store_android}</td></tr>
 * <tr><td>4</td><td>그 외</td><td>{@code organic_web}</td></tr>
 * </table>
 *
 * <p>값은 trim 후 비면 null, 컬럼 길이에 맞춰 자른다. 기존 유저(배포 전 가입)는 전부 null = 유입 미상.
 */
public final class SignupChannelPolicy {

    public static final String CHANNEL_INVITE = "invite";
    public static final String CHANNEL_STORE_IOS = "store_ios";
    public static final String CHANNEL_STORE_ANDROID = "store_android";
    public static final String CHANNEL_ORGANIC_WEB = "organic_web";

    static final int CHANNEL_MAX_LENGTH = 50;
    static final int UTM_MAX_LENGTH = 100;
    static final int INVITE_CODE_MAX_LENGTH = 50;
    static final int DEVICE_TYPE_MAX_LENGTH = 20;

    private SignupChannelPolicy() {}

    public static SignupAttribution resolve(
            String utmSource,
            String utmMedium,
            String utmCampaign,
            String inviteCode,
            String deviceType) {
        String source = clean(utmSource, UTM_MAX_LENGTH);
        String medium = clean(utmMedium, UTM_MAX_LENGTH);
        String campaign = clean(utmCampaign, UTM_MAX_LENGTH);
        String invite = clean(inviteCode, INVITE_CODE_MAX_LENGTH);
        String device = clean(deviceType, DEVICE_TYPE_MAX_LENGTH);

        String channel;
        if (invite != null) {
            channel = CHANNEL_INVITE;
        } else if (source != null) {
            channel = clean(source.toLowerCase(Locale.ROOT), CHANNEL_MAX_LENGTH);
        } else if ("ios".equals(device) || "ipad".equals(device)) {
            channel = CHANNEL_STORE_IOS;
        } else if ("android".equals(device)) {
            channel = CHANNEL_STORE_ANDROID;
        } else {
            channel = CHANNEL_ORGANIC_WEB;
        }
        return new SignupAttribution(channel, source, medium, campaign, device, invite);
    }

    /** trim → 비면 null → 길이 절단 */
    static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
