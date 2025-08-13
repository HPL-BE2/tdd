package io.hhplus.tdd.point;

public interface PointService {
    /**
     * 포인트 충전
     *
     * @param userId 사용자 ID
     * @param amount 충전할 포인트 금액
     * @return 충전된 포인트 정보
     */
    UserPoint charge(long userId, long amount);

    /**
     * 포인트 사용
     *
     * @param userId 사용자 ID
     * @param amount 사용할 포인트 금액
     * @return 사용된 포인트 정보
     */
    UserPoint use(long userId, long amount);

    /**
     * 사용자 포인트 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 포인트 정보
     */
    UserPoint getUserPoint(long userId);
}
