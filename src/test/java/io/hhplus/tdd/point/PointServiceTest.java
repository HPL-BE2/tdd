package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
@DisplayName("PointServiceImpl 단위 테스트")
class PointServiceTest {
    /**
     * 실제 구현체를 사용한 통합 테스트용 서비스 인스턴스 생성
     * Mock 없이 실제 데이터베이스 테이블을 사용하여 전체 플로우 검증
     */
    private PointService newService() {
        return new PointServiceImpl(new UserPointTable(), new PointHistoryTable());
    }

    @Nested
    @DisplayName("Red-Green-Refactor")
    class RedGreenRefactor {
// Red
        /**
         * [작성 이유]
         * 가장 기본적인 조회 기능의 정상 동작을 검증
         * 신규 사용자의 경우 0 포인트로 초기화되는지 확인
         *
         * [검증 내용]
         * - UserPoint 객체가 null이 아님
         * - 올바른 userId 반환
         * - 신규 사용자의 포인트는 0
         */
        @Test
        @DisplayName("given_유효한사용자_when_getUserPoint_then_빈유저_0포인트반환")
        void givenValidUserId_whenGetUserPoint_thenReturnEmptyUserWithZeroPoint() {
            // given: 유효한 사용자 ID
            Long userId = 1L;
            PointService pointService = newService();

            // when: 사용자 포인트 조회
            UserPoint userPoint = pointService.getUserPoint(userId);

            // then
            assertNotNull(userPoint);
            assertEquals(userId, userPoint.id());
            assertEquals(0L, userPoint.point(), "신규 유저는 0 포인트여야 함");
        }

        /**
         * [작성 이유]
         * 포인트 충전의 핵심 기능 검증
         * 충전 후 잔액 증가와 이력 기록이 정상적으로 이루어지는지 확인
         *
         * [검증 내용]
         * - 충전 후 포인트가 정확히 증가
         * - 충전 이력이 1건 기록됨
         * - 이력의 타입이 CHARGE
         * - 이력의 금액이 정확함
         */
        @Test
        @DisplayName("given_유효금액충전_when_charge_then_포인트증가와_히스토리1건기록")
        void givenValidAmount_whenChargePoint_thenPointIncreased_andHistoryRecorded() {
            // given: 유저 ID와 충전 금액
            long userId = 2L;
            long chargeAmount = 100L;
            PointService pointService = newService();

            // when: 포인트 충전 수행
            UserPoint result = pointService.charge(userId, chargeAmount);

            // then: 결과가 null이 아니어야 함
            // 추가적으로 balance 증가 여부 검증 가능
            assertNotNull(result);
            assertEquals(chargeAmount, result.point());
            List<PointHistory> history = pointService.getPointHistory(userId);
            assertEquals(1, history.size());
            assertEquals(TransactionType.CHARGE, history.get(0).type());
            assertEquals(chargeAmount, history.get(0).amount());
        }

        /**
         * [작성 이유]
         * 포인트 사용 기능의 정상 동작 검증
         * 충분한 잔액이 있을 때 포인트 차감이 올바르게 동작하는지 확인
         *
         * [검증 내용]
         * - 사용 후 잔액이 정확히 차감됨
         * - 충전과 사용 이력이 모두 누적 기록됨
         * - 각 이력의 타입과 금액이 정확함
         */
        @Test
        @DisplayName("given_충전선행_when_use_then_잔액차감과_히스토리누적")
        void givenSufficientBalance_whenUsePoint_thenPointDecreased_andHistoryRecorded() {
            // given: 유저 ID, 초기 충전 포인트, 사용 포인트
            Long userId = 1L;
            long initialPoint = 1000L;
            long useAmount = 500L;
            PointService pointService = newService();
            pointService.charge(userId, initialPoint);

            // when
            UserPoint result = pointService.use(userId, useAmount);

            // then
            assertEquals(initialPoint - useAmount, result.point());
            List<PointHistory> history = pointService.getPointHistory(userId);
            assertEquals(2, history.size());
            assertEquals(TransactionType.CHARGE, history.get(0).type());
            assertEquals(TransactionType.USE, history.get(1).type());
            assertEquals(useAmount, history.get(1).amount());
        }

        /**
         * [작성 이유]
         * 이력 조회 기능의 기본 동작 검증
         * 아직 거래 내역이 없는 사용자의 경우 빈 리스트 반환 확인
         *
         * [검증 내용]
         * - null이 아닌 List 객체 반환
         * - 빈 리스트인지 확인
         */
        @Test
        @DisplayName("given_유효사용자_when_getPointHistory_then_비어있으면_빈리스트반환")
        void givenValidUserId_whenGetPointHistory_thenReturnEmptyListIfNoHistory() {
            // given: 유효한 사용자 ID
            Long userId = 1L;
            PointService pointService = newService();

            // when: 포인트 이력 조회
            List<PointHistory> history = pointService.getPointHistory(userId);

            // then: null이 아닌 리스트가 반환되는지 검증
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        /**
         * [작성 이유]
         * 여러 번 충전 시 포인트가 누적되는지 검증
         * 연속적인 충전 작업의 정확성 확인
         *
         * [검증 내용]
         * - 두 번의 충전이 누적되어 총합이 정확함
         * - 두 건의 이력이 모두 기록됨
         */
        @Test
        @DisplayName("given_두번충전_when_charge_then_누적합산반영")
        void givenTwoCharges_when_charge_then_balanceIsAccumulated() {
            long userId = 5L;
            PointService service = newService();

            service.charge(userId, 200L);
            UserPoint afterSecond = service.charge(userId, 300L);

            assertEquals(500L, afterSecond.point());
            assertEquals(2, service.getPointHistory(userId).size());
        }

        /**
         * [작성 이유]
         * 경계값 테스트 - 잔액을 정확히 모두 사용하는 경우
         * 잔액이 0이 되는 상황에서의 정상 동작 확인
         *
         * [검증 내용]
         * - 잔액을 모두 사용한 후 0포인트가 됨
         */
        @Test
        @DisplayName("given_정확히잔액만큼사용_when_use_then_잔액0")
        void givenUseExactlyBalance_when_use_then_balanceBecomesZero() {
            long userId = 6L;
            PointService service = newService();
            service.charge(userId, 700L);

            UserPoint result = service.use(userId, 700L);

            assertEquals(0L, result.point());
        }

        /**
         * [작성 이유]
         * 예외 상황 테스트 - 잔액 부족 시 예외 발생 확인
         * 비즈니스 규칙(잔액 부족 시 사용 불가) 검증
         *
         * [검증 내용]
         * - IllegalStateException 발생
         * - 실패한 거래의 이력이 기록되지 않음 (데이터 일관성)
         */
        @Test
        @DisplayName("given_잔액부족_when_use_then_IllegalStateException_발생하고_히스토리미기록")
        void givenInsufficientBalance_when_use_then_throwAndNoHistoryAdded() {
            long userId = 7L;
            PointService service = newService();

            // 잔액 없이 사용 시도
            assertThrows(IllegalStateException.class, () -> service.use(userId, 50L));

            // then: 히스토리는 비어 있어야 함 (현재 구현은 잔액 검증 후 이력 기록이므로 안전)
            assertTrue(service.getPointHistory(userId).isEmpty());
        }
    }

    @Nested
    @DisplayName("Mock 기반 행위 검증")
    class BehaviorWithMock {
        @Mock
        UserPointTable userPointTable;
        @Mock
        PointHistoryTable pointHistoryTable;

        @InjectMocks
        PointServiceImpl sut; // System Under Test

        /**
         * [작성 이유]
         * 충전 기능의 내부 동작 순서와 파라미터 정확성 검증
         * Mock을 통해 의존성 호출 순서와 전달되는 값들을 세밀하게 검증
         *
         * [검증 내용]
         * - 메서드 호출 순서: select → historyInsert → upsert
         * - 전달되는 파라미터의 정확성
         * - 시간 값이 유효한 범위에 있는지 확인
         */
        @Test
        @DisplayName("given_기존포인트없음_when_charge_then_select→historyInsert→upsert_순서호출과_정확한파라미터")
        void given_noExistingPoint_when_charge_then_callsInOrder_and_withCorrectArgs() {
            long userId = 10L;
            long amount = 500L;

            given(userPointTable.selectById(userId)).willReturn(null);
            given(userPointTable.insertOrUpdate(userId, amount))
                    .willReturn(new UserPoint(userId, amount, System.currentTimeMillis()));

            UserPoint result = sut.charge(userId, amount);

            assertNotNull(result);
            assertEquals(amount, result.point());

            InOrder inOrder = inOrder(userPointTable, pointHistoryTable);
            inOrder.verify(userPointTable).selectById(userId);
            inOrder.verify(pointHistoryTable).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), anyLong());
            inOrder.verify(userPointTable).insertOrUpdate(userId, amount);

            ArgumentCaptor<Long> time = ArgumentCaptor.forClass(Long.class);
            verify(pointHistoryTable).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), time.capture());
            assertTrue(time.getValue() > 0);
        }

        /**
         * [작성 이유]
         * 사용 기능의 내부 동작과 차감 로직 검증
         * 기존 잔액에서 사용 금액을 차감한 값이 정확히 업데이트되는지 확인
         *
         * [검증 내용]
         * - 메서드 호출 순서가 올바름
         * - 차감된 잔액이 정확히 계산되어 업데이트됨
         */
        @Test
        @DisplayName("given_충분한잔액_when_use_then_select→historyInsert→upsert_순서호출과_차감값반영")
        void given_sufficientBalance_when_use_then_callsInOrder_and_decreasedBalanceUpserted() {
            long userId = 11L;
            long current = 800L;
            long use = 300L;
            long expected = current - use;

            given(userPointTable.selectById(userId))
                    .willReturn(new UserPoint(userId, current, System.currentTimeMillis()));
            given(userPointTable.insertOrUpdate(userId, expected))
                    .willReturn(new UserPoint(userId, expected, System.currentTimeMillis()));

            UserPoint result = sut.use(userId, use);

            assertEquals(expected, result.point());

            InOrder inOrder = inOrder(userPointTable, pointHistoryTable);
            inOrder.verify(userPointTable).selectById(userId);
            inOrder.verify(pointHistoryTable).insert(eq(userId), eq(use), eq(TransactionType.USE), anyLong());
            inOrder.verify(userPointTable).insertOrUpdate(userId, expected);
        }

        /**
         * [작성 이유]
         * 조회 기능의 내부 동작 검증
         * 존재하지 않는 사용자에 대한 처리가 올바른지 확인
         *
         * [검증 내용]
         * - selectById 메서드가 정확히 1번 호출됨
         * - null 반환 시 빈 UserPoint 객체 생성
         * - 불필요한 추가 호출이 없음
         */
        @Test
        @DisplayName("given_존재하지않는유저_when_getUserPoint_then_select호출되고_empty반환")
        void given_absentUser_when_getUserPoint_then_selectCalled_and_returnsEmpty() {
            long userId = 12L;
            given(userPointTable.selectById(userId)).willReturn(null);

            UserPoint up = sut.getUserPoint(userId);

            assertNotNull(up);
            assertEquals(0L, up.point());
            verify(userPointTable, times(1)).selectById(userId);
            verifyNoMoreInteractions(userPointTable);
        }

        /**
         * [작성 이유]
         * 이력 조회 기능의 내부 동작 검증
         * 이력이 없는 경우의 처리 로직 확인
         *
         * [검증 내용]
         * - selectAllByUserId 메서드가 호출됨
         * - null 반환 시 빈 리스트로 변환
         */
        @Test
        @DisplayName("given_이력없음_when_getPointHistory_then_selectAllByUserId호출후_빈리스트반환")
        void given_noHistory_when_getPointHistory_then_callsSelectAll_and_returnsEmptyList() {
            long userId = 13L;
            given(pointHistoryTable.selectAllByUserId(userId)).willReturn(null);

            List<PointHistory> histories = sut.getPointHistory(userId);

            assertNotNull(histories);
            assertTrue(histories.isEmpty());
            verify(pointHistoryTable).selectAllByUserId(userId);
        }
    }

    @Nested
    @DisplayName("Stub 기반 상태 검증")
    class StateWithStub {
        @Mock
        UserPointTable userPointTable;
        @Mock
        PointHistoryTable pointHistoryTable;
        @InjectMocks
        PointServiceImpl sut;

        /**
         * [작성 이유]
         * 기존 잔액에 추가 충전 시 누적 계산의 정확성 검증
         * Mock을 통해 특정 상태를 설정하고 결과값만 검증
         *
         * [검증 내용]
         * - 1000 + 500 = 1500의 정확한 계산
         */
        @Test
        @DisplayName("given_기존잔액1000_when_charge500_then_1500반환")
        void given_current1000_when_charge500_then_returns1500() {
            long userId = 20L;
            long current = 1_000L;
            long charge = 500L;
            long expected = 1_500L;

            given(userPointTable.selectById(userId))
                    .willReturn(new UserPoint(userId, current, System.currentTimeMillis()));
            given(userPointTable.insertOrUpdate(userId, expected))
                    .willReturn(new UserPoint(userId, expected, System.currentTimeMillis()));

            UserPoint result = sut.charge(userId, charge);

            assertEquals(expected, result.point());
        }

        /**
         * [작성 이유]
         * 포인트 사용 시 차감 계산의 정확성 검증
         * 특정 잔액에서 특정 금액 차감 후 결과 확인
         *
         * [검증 내용]
         * - 1000 - 400 = 600의 정확한 계산
         */
        @Test
        @DisplayName("given_현재잔액1000_when_use400_then_600반환")
        void given_current1000_when_use400_then_returns600() {
            long userId = 21L;
            long current = 1_000L;
            long use = 400L;
            long expected = 600L;

            given(userPointTable.selectById(userId))
                    .willReturn(new UserPoint(userId, current, System.currentTimeMillis()));
            given(userPointTable.insertOrUpdate(userId, expected))
                    .willReturn(new UserPoint(userId, expected, System.currentTimeMillis()));

            UserPoint result = sut.use(userId, use);

            assertEquals(expected, result.point());
        }

        /**
         * [작성 이유]
         * 이력 조회에서 빈 결과 처리 검증
         * 데이터베이스에서 빈 리스트가 반환되는 경우의 처리
         *
         * [검증 내용]
         * - 빈 리스트가 올바르게 반환됨
         */
        @Test
        @DisplayName("given_결과없음_when_getPointHistory_then_빈리스트")
        void given_noResult_when_getPointHistory_then_emptyList() {
            long userId = 22L;
            given(pointHistoryTable.selectAllByUserId(userId)).willReturn(List.of());

            List<PointHistory> histories = sut.getPointHistory(userId);

            assertTrue(histories.isEmpty());
        }
    }

    @Nested
    @DisplayName("유효성/예외 케이스")
    class ValidationAndExceptions {
        @Mock
        UserPointTable userPointTable;
        @Mock
        PointHistoryTable pointHistoryTable;
        @InjectMocks
        PointServiceImpl sut;

        /**
         * [작성 이유]
         * 입력 유효성 검증 - 잘못된 사용자 ID에 대한 예외 처리 확인
         * 방어적 프로그래밍을 위한 파라미터 검증 로직 테스트
         *
         * [검증 내용]
         * - userId가 0 이하일 때 IllegalArgumentException 발생
         */
        @Test
        @DisplayName("given_userId<=0_when_charge_then_IllegalArgumentException")
        void given_invalidUserId_when_charge_then_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> sut.charge(0L, 100L));
            assertThrows(IllegalArgumentException.class, () -> sut.charge(-1L, 100L));
        }

        /**
         * [작성 이유]
         * 입력 유효성 검증 - 잘못된 금액에 대한 예외 처리 확인
         * 비즈니스 규칙(양수 금액만 허용) 검증
         *
         * [검증 내용]
         * - 금액이 0 이하일 때 IllegalArgumentException 발생
         */
        @Test
        @DisplayName("given_amount<=0_when_charge_then_IllegalArgumentException")
        void given_invalidAmount_when_charge_then_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> sut.charge(1L, 0L));
            assertThrows(IllegalArgumentException.class, () -> sut.charge(1L, -10L));
        }

        /**
         * [작성 이유]
         * 사용 기능에서의 사용자 ID 유효성 검증
         * 일관된 입력 검증 로직 확인
         */
        @Test
        @DisplayName("given_userId<=0_when_use_then_IllegalArgumentException")
        void given_invalidUserId_when_use_then_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> sut.use(0L, 10L));
        }

        /**
         * [작성 이유]
         * 사용 기능에서의 금액 유효성 검증
         * 일관된 입력 검증 로직 확인
         */
        @Test
        @DisplayName("given_amount<=0_when_use_then_IllegalArgumentException")
        void given_invalidAmount_when_use_then_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> sut.use(1L, 0L));
        }

        /**
         * [작성 이유]
         * 비즈니스 규칙 검증 - 잔액 부족 시 예외 발생 및 데이터 일관성 확인
         * 실패한 거래가 데이터에 영향을 주지 않는지 검증
         *
         * [검증 내용]
         * - 잔액 부족 시 IllegalStateException 발생
         * - 실패한 거래의 이력이나 잔액 변경이 발생하지 않음
         */
        @Test
        @DisplayName("given_잔액부족_when_use_then_IllegalStateException")
        void given_insufficientBalance_when_use_then_throwsIllegalState() {
            long userId = 30L;
            given(userPointTable.selectById(userId))
                    .willReturn(new UserPoint(userId, 100L, System.currentTimeMillis()));

            assertThrows(IllegalStateException.class, () -> sut.use(userId, 500L));
            // 업데이트/이력 호출이 절대 일어나지 않아야 함 (현 구현은 이력 insert가 잔액 검증 후라서 안전)
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        }

        /**
         * [작성 이유]
         * 조회 기능에서의 사용자 ID 유효성 검증
         * 전체 API에서 일관된 입력 검증 확인
         */
        @Test
        @DisplayName("given_userId<=0_when_getUserPoint_then_IllegalArgumentException")
        void given_invalidUserId_when_getUserPoint_then_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> sut.getUserPoint(0L));
        }

        /**
         * [작성 이유]
         * 이력 조회 기능에서의 사용자 ID 유효성 검증
         * 전체 API에서 일관된 입력 검증 확인
         */
        @Test
        @DisplayName("given_userId<=0_when_getPointHistory_then_IllegalArgumentException")
        void given_invalidUserId_when_getPointHistory_then_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> sut.getPointHistory(0L));
        }
    }

    @Nested
    @DisplayName("PointServiceImpl 동시성 테스트")
    class PointServiceConcurrencyTest {

        /**
         * [유틸리티 메서드]
         * 동일한 작업을 여러 스레드에서 반복 실행하는 동시성 테스트 유틸
         *
         * [작성 이유]
         * - 부하 테스트나 동일 작업의 동시 실행 시나리오 테스트용
         * - CountDownLatch로 모든 스레드가 동시에 시작하도록 보장
         * - CompletableFuture로 모든 작업 완료까지 대기
         */
        private void runConcurrently(int threads, Runnable task) throws Exception {
            ExecutorService ex = Executors.newFixedThreadPool(threads);
            try {
                // 시작과 완료를 동기화하기 위한 카운트다운 래치 (CountDownLatch (동시 시작/종료 동기화))
                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<?>[] jobs = new CompletableFuture<?>[threads];
                for (int i = 0; i < threads; i++) {
                    jobs[i] = CompletableFuture.runAsync(() -> {
                        try { start.await(); task.run(); } catch (InterruptedException ignored) {}
                    }, ex); // 공용 풀 대신 전용 풀
                }
                start.countDown(); // 동시에 출발
//                CompletableFuture.allOf(jobs).get(60, TimeUnit.SECONDS); // 여유
                CompletableFuture.allOf(jobs).get(); // 완료까지 기다려야 하는 경우
            } finally {
                ex.shutdownNow();
                ex.awaitTermination(10, TimeUnit.SECONDS);
            }
        }

        /**
         * [유틸리티 메서드]
         * 여러 개의 서로 다른 작업을 동시에 실행하는 동시성 테스트 유틸
         *
         * [작성 이유]
         * - 서로 다른 작업들이 동시에 실행되는 시나리오 테스트용
         * - 각 작업이 독립적으로 실행되면서 서로 간섭하지 않는지 확인
         */
        private void runConcurrentlyTasks(List<Runnable> tasks, long timeoutSec) throws Exception {
            ExecutorService ex = Executors.newFixedThreadPool(tasks.size());
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<CompletableFuture<Void>> futures = tasks.stream()
                        .map(r -> CompletableFuture.runAsync(() -> {
                            try { start.await(); } catch (InterruptedException ignored) {}
                            r.run();
                        }, ex))
                        .toList();

                start.countDown();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(timeoutSec, TimeUnit.SECONDS);
            } finally {
                ex.shutdownNow();
                ex.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Nested
        @DisplayName("동일 userId 직렬화")
        class SameUserSerial {
            /**
             * [작성 이유]
             * 동일 사용자에 대한 동시 충전이 안전하게 직렬화되는지 검증
             * 레이스 컨디션 없이 모든 충전이 정확히 누적되는지 확인
             *
             * [검증 내용]
             * - 최종 잔액이 모든 충전의 합과 일치 (8 × 20 × 5 = 800)
             * - 모든 충전 이력이 누락 없이 기록됨 (160건)
             * - 동시성 제어가 올바르게 동작함을 증명
             */
            @Test
            @DisplayName("given_동시에N회충전_when_완료_then_최종잔액은합계와일치_이력개수일치")
            void given_concurrentCharges_onSameUser_when_done_then_balanceEqualsSum_and_historyMatches() throws Exception {
                PointService service = newService();
                long userId = 1L;

                // 8개 스레드가 각각 20번씩 포인트 충전
                int threads = 8;
                int opsPerThread = 20;
                long amount = 5L;
                long expected = (long) threads * opsPerThread * amount;

                runConcurrently(threads, () -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        service.charge(userId, amount);
                    }
                });

                assertEquals(expected, service.getUserPoint(userId).point());
                assertEquals(threads * opsPerThread, service.getPointHistory(userId).size());
            }

            /**
             * [작성 이유]
             * 동시 사용 상황에서의 잔액 부족 처리와 데이터 일관성 검증
             * 한정된 잔액에 대한 동시 경쟁 상황에서 올바른 성공/실패 처리 확인
             *
             * [테스트 시나리오]
             * - 초기 잔액: 100포인트
             * - 20개 스레드가 동시에 각각 10포인트씩 사용 시도
             * - 이론적으로 10번만 성공 가능 (100 ÷ 10 = 10)
             *
             * [검증 내용]
             * - 정확히 10번의 성공과 10번의 실패 발생
             * - 최종 잔액이 0포인트
             * - 이력 기록: 충전 1건 + 성공한 사용 10건 = 총 11건
             * - 실패한 거래는 이력에 기록되지 않음 (원자성 보장)
             * - 동시성 제어로 인한 데이터 무결성 유지
             */
            @Test
            @DisplayName("given_잔액100_when_동시10사용×20명_then_성공10건_잔액0_이력검증")
            void concurrent_use_same_user() throws Exception {
                PointService service = newService();
                long userId = 2L;
                service.charge(userId, 100L); // 초기 100

                int threads = 20;
                long use = 10L;
                AtomicInteger success = new AtomicInteger();
                AtomicInteger fail = new AtomicInteger();

                runConcurrently(threads, () -> {
                    try {
                        service.use(userId, use);
                        success.incrementAndGet();
                    } catch (IllegalStateException e) { // 잔액부족
                        fail.incrementAndGet();
                    }
                });

                // 성공/실패 개수 검증
                assertEquals(10, success.get(), "100포인트로 10포인트씩 사용하면 정확히 10번 성공해야 함");
                assertEquals(10, fail.get(), "나머지 10번은 잔액 부족으로 실패해야 함");
                assertEquals(0L, service.getUserPoint(userId).point(), "모든 포인트가 사용되어 잔액은 0이어야 함");

                var histories = service.getPointHistory(userId);
                long chargeCnt = histories.stream().filter(h -> h.type() == TransactionType.CHARGE).count();
                long useCnt    = histories.stream().filter(h -> h.type() == TransactionType.USE).count();

                assertEquals(1, chargeCnt, "초기 충전 1건이 기록되어야 함");
                assertEquals(10, useCnt, "성공한 사용 10건만 이력에 기록되어야 함");
                assertEquals(11, histories.size(), "총 이력은 충전 1건 + 사용 10건 = 11건");
            }
        }

        @Nested
        @DisplayName("다른 userId: 병렬 허용")
        class DifferentUsers {
            /**
             * [작성 이유]
             * 서로 다른 사용자에 대한 동시 작업이 독립적으로 수행되는지 검증
             * 사용자 간 격리성(Isolation)과 병렬 처리 성능 확인
             *
             * [테스트 시나리오]
             * - 사용자 100과 사용자 200이 동시에 각각 100포인트씩 충전
             * - 두 작업이 서로 간섭하지 않고 병렬로 처리되는지 확인
             *
             * [검증 내용]
             * - 각 사용자의 포인트가 독립적으로 정확히 충전됨 (각각 100포인트)
             * - 각 사용자의 이력이 독립적으로 기록됨 (각각 1건씩)
             * - 서로 다른 사용자 작업이 상호 간섭하지 않음
             * - 동시성 제어가 사용자별로 격리되어 동작함
             *
             * [성능 고려사항]
             * - 다른 사용자 간에는 락 경합이 발생하지 않아야 함
             * - 병렬 처리로 인한 성능 향상 기대
             */
            @Test
            @DisplayName("given_두유저_when_동시충전_then_각자정확히반영(시간 단언 없음)")
            void parallel_different_users_state_only() throws Exception {
                PointService service = newService();
                long a = 100L, b = 200L;

                runConcurrentlyTasks(List.of(
                        () -> service.charge(a, 100L), // 스레드1의 작업
                        () -> service.charge(b, 100L)  // 스레드2의 작업
                ), 10);

                assertEquals(100L, service.getUserPoint(a).point(), "사용자 100은 100포인트를 가져야 함");
                assertEquals(100L, service.getUserPoint(b).point(), "사용자 200은 100포인트를 가져야 함");
                assertEquals(1, service.getPointHistory(a).size(), "사용자 100의 이력은 1건이어야 함");
                assertEquals(1, service.getPointHistory(b).size(), "사용자 200의 이력은 1건이어야 함");
            }
        }
    }
}