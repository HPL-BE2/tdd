package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PointServiceImpl implements  PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    @Override
    public UserPoint charge(long userId, long amount) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be greater than 0");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        UserPoint userPoint = userPointTable.insertOrUpdate(userId, amount);

        if (userPoint == null) {
            throw new IllegalStateException("Failed to charge user point for user ID: " + userId);
        }

        // 최종 결과 로그
        log.info("[charge] User ID: {}, Charged Amount: {}, New Point Balance: {}", userId, amount, userPoint.point());
        return userPoint;
    }

    @Override
    public UserPoint use(long userId, long amount) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be greater than 0");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        UserPoint userPoint = userPointTable.selectById(userId);
        if (userPoint == null) {
            throw new IllegalStateException("User point not found for user ID: " + userId);
        }

        if (userPoint.point() < amount) {
            throw new IllegalStateException("Insufficient points for user ID: " + userId);
        }

        long newPoint = userPoint.point() - amount;
        UserPoint result = userPointTable.insertOrUpdate(userId, newPoint);

        // 최종 결과 로그
        log.info("[use] User ID: {}, Used Amount: {}, New Point Balance: {}", userId, amount, result.point());
        return result;
    }

    @Override
    public UserPoint getUserPoint(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be greater than 0");
        }

        UserPoint userPoint = userPointTable.selectById(userId);

        if (userPoint == null) {
            userPoint = UserPoint.empty(userId);
        }

        // 최종 결과 로그
        log.info("[getUserPoint] User ID: {}, Current Point Balance: {}", userId, userPoint.point());
        return userPoint;
    }

    @Override
    public List<PointHistory> getPointHistory(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be greater than 0");
        }

        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);

        if (histories == null || histories.isEmpty()) {
            return List.of();
        }

        // 최종 결과 로그
        log.info("[getPointHistory] User ID: {}, Point History Count: {}", userId, histories.size());
        // 각 이력 로그
        histories.forEach(history -> log.info("[getPointHistory] History ID: {}, Amount: {}, Type: {}, Updated At: {}",
                history.id(), history.amount(), history.type(), history.updateMillis()));
        return histories;
    }
}
