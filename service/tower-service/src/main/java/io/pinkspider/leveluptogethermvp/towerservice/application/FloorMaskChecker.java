package io.pinkspider.leveluptogethermvp.towerservice.application;

import java.io.IOException;

/** LUT-629: 입장 지점(배경 원본 픽셀)이 바닥 마스크(붉은색 = 이동 가능) 안인지 판정한다. 클라이언트·서버가 같은 마스크 파일로 검증한다. */
public interface FloorMaskChecker {

    /**
     * @param maskUrl 마스크 PNG URL (배경과 같은 좌표계, 해상도는 더 낮아도 됨)
     * @param backgroundUrl 배경 이미지 URL — 마스크와 해상도가 다를 때 좌표 비율 계산용
     * @param x 입장 지점 X (배경 원본 픽셀)
     * @param y 입장 지점 Y (배경 원본 픽셀)
     * @return 마스크 안(이동 가능)이면 true
     * @throws IOException 마스크·배경을 읽을 수 없을 때
     */
    boolean isWalkable(String maskUrl, String backgroundUrl, int x, int y) throws IOException;

    /**
     * LUT-725: 마스크가 배경과 가로세로 비율이 같은가(해상도는 달라도 된다 — 4분의 1 로 저장). 앞 가림·효과 레이어 마스크 저장 검증.
     *
     * @throws IOException 마스크·배경을 읽을 수 없을 때
     */
    boolean sameAspectRatio(String maskUrl, String backgroundUrl) throws IOException;

    /**
     * LUT-746: 좌표(원본 픽셀)가 그림 안인가 — 0 ≤ x < 너비, 0 ≤ y < 높이. NPC 자리 저장 검증(이동 불가 영역에도 둘 수 있다).
     *
     * @throws IOException 그림을 읽을 수 없을 때
     */
    boolean isInsideImage(String imageUrl, int x, int y) throws IOException;
}
