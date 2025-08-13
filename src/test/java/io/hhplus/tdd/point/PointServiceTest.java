package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

// 테스트
// 행위, 상태 2가지
// 테스트 메서드명 (given_when_then 명명 규칙)

/**
 * TDD의 기본 사이클 (Red-Green-Refactor)
 *
 * Red: 실패하는 테스트를 먼저 작성 (요구사항을 명확히 이해하고, 실패하는 테스트 케이스를 작성)
 * Green: 테스트를 통과하는 최소한의 코드 작성
 * Refactor: 코드를 개선하고 리팩토링
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    // Red
    @Test
    void givenValidUserId_whenGetUserPoint_thenReturnUserPoint() {
        // given
        Long userId = 1L;
        PointService pointService = new PointServiceImpl(new UserPointTable(), new PointHistoryTable());

        // when
        UserPoint userPoint = pointService.getUserPoint(userId);

        // then
        assertNotNull(userPoint);
    }

    @Test
    void givenValidAmount_whenChargePoint_thenPointIncreased() {
        // given
        Long userId = 1L;
        long chargeAmount = 100L;
        PointService pointService = new PointServiceImpl(new UserPointTable(), new PointHistoryTable());

        // when
        UserPoint result = pointService.charge(userId, chargeAmount);

        // then
        assertNotNull(result);
    }

    @Test
    void givenSufficientBalance_whenUsePoint_thenPointDecreased() {
        // given
        Long userId = 1L;
        long initialPoint = 1000L;
        long useAmount = 500L;
        PointService pointService = new PointServiceImpl(new UserPointTable(), new PointHistoryTable());

        // 포인트 충전 선행
        pointService.charge(userId, initialPoint);

        // when
        UserPoint result = pointService.use(userId, useAmount);

        // then
        assertNotNull(result);
    }

    @Test
    void givenValidUserId_whenGetPointHistory_thenReturnPointHistory() {
        // given
        Long userId = 1L;
        PointService pointService = new PointServiceImpl(new UserPointTable(), new PointHistoryTable());

        // when
        List<PointHistory> history = pointService.getPointHistory(userId);

        // then
        assertNotNull(history);
    }
}