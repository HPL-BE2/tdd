package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointServiceImpl implements  PointService {
    private final UserPointTable userPointTable;

    @Override
    public UserPoint charge(long userId, long amount) {
        return null;
    }

    @Override
    public UserPoint use(long userId, long amount) {
        return null;
    }

    @Override
    public UserPoint getUserPoint(long userId) {
        return null;
    }
}
