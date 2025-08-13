package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@RequiredArgsConstructor
public class PointServiceImpl implements  PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // userId -> Lock 매핑 (공정 락: 대기 순서 보장)
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private ReentrantLock lockOf(long userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true));
    }

    private void releaseIfIdle(long userId, ReentrantLock lock) {
        // 대기 스레드가 없으면 맵에서 제거(메모리 누수 완화)
        if (!lock.hasQueuedThreads()) {
            userLocks.remove(userId, lock);
        }
    }

    @Override
    public UserPoint charge(long userId, long amount) {
        /**
         * 진행 프로세스
         * 1. 유저 ID와 충전 금액이 유효한지 검증
         * 2. 기존 포인트 조회
         * 3. 기존 포인트가 없으면 새로 생성
         * 4. 포인트 이력 추가
         * 5. 포인트 충전
         */
        validateUserId(userId);
        validatePositiveAmount(amount);

        ReentrantLock lock = lockOf(userId);
        lock.lock();
        try {
            // 기존 포인트에 더하기
            UserPoint existingUserPoint = userPointTable.selectById(userId);
            if (existingUserPoint == null) {
                // 기존 포인트가 없으면 새로 생성
                existingUserPoint = UserPoint.empty(userId);
            }

            long newAmount = existingUserPoint.point() + amount;

            // 포인트 충전
            log.info("[charge] User ID: {}, Amount to Charge: {}, Existing Point: {}", userId, amount, existingUserPoint.point());

            UserPoint userPoint = userPointTable.insertOrUpdate(userId, newAmount);

            if (userPoint == null) {
                throw new IllegalStateException("Failed to charge user point for user ID: " + userId);
            }
            // 포인트 이력 추가
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

            // 최종 결과 로그
            log.info("[charge] User ID: {}, Charged Amount: {}, New Point Balance: {}", userId, amount, userPoint.point());
            return userPoint;
        } finally {
            try {
                releaseIfIdle(userId, lock);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public UserPoint use(long userId, long amount) {
        /**
         * 진행 프로세스
         * 1. 유저 ID와 충전 금액이 유효한지 검증
         * 2. 기존 포인트 조회
         * 3. 기존 포인트가 없으면 새로 생성
         * 4. 포인트 이력 추가
         * 5. 포인트 차감
         */
        validateUserId(userId);
        validatePositiveAmount(amount);

        ReentrantLock lock = lockOf(userId);
        lock.lock();

        try {
            // 기존 포인트 조회
            UserPoint userPoint = userPointTable.selectById(userId);
            // 기존 포인트가 없으면 새로 생성
            if (userPoint == null) {
                userPoint = UserPoint.empty(userId);
            }

            if (userPoint == null) {
                throw new IllegalStateException("User point not found for user ID: " + userId);
            }

            if (userPoint.point() < amount) {
                throw new IllegalStateException("Insufficient points for user ID: " + userId);
            }

            long newPoint = userPoint.point() - amount;
            // 포인트가 음수로 떨어지지 않도록 검증 (잔고가 부족할 경우 예외 발생)
            if (newPoint < 0) {
                throw new IllegalStateException("New point balance cannot be negative for user ID: " + userId);
            }

            // 포인트 이력 추가
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

            // 포인트 차감
            log.info("[use] User ID: {}, Amount to Use: {}, Existing Point: {}", userId, amount, userPoint.point());

            UserPoint result = userPointTable.insertOrUpdate(userId, newPoint);

            // 최종 결과 로그
            log.info("[use] User ID: {}, Used Amount: {}, New Point Balance: {}", userId, amount, result.point());
            return result;
        } finally {
            try {
                releaseIfIdle(userId, lock);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public UserPoint getUserPoint(long userId) {
        validateUserId(userId);

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
        validateUserId(userId);

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

    private void validateUserId(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("User ID must be greater than 0");
    }
    private void validatePositiveAmount(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be greater than 0");
    }
}
