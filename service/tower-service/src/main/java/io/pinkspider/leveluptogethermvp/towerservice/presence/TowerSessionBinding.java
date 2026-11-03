package io.pinkspider.leveluptogethermvp.towerservice.presence;

/** LUT-631: STOMP 세션 ↔ 점유자 묶음 (끊김 감지용) */
public record TowerSessionBinding(Long instanceId, Long occupantId) {

    public String encode() {
        return instanceId + ":" + occupantId;
    }

    public static TowerSessionBinding decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new TowerSessionBinding(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
