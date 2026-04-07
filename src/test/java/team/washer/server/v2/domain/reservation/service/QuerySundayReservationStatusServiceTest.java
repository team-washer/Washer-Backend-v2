package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.reservation.service.impl.QuerySundayReservationStatusServiceImpl;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuerySundayReservationStatusServiceImpl 클래스의")
class QuerySundayReservationStatusServiceTest {

    @InjectMocks
    private QuerySundayReservationStatusServiceImpl querySundayReservationStatusService;

    @Mock
    private SundayReservationRedisUtil sundayReservationRedisUtil;

    @Mock
    private ReservationCycleLogRepository cycleLogRepository;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("일요일 예약이 활성화된 상태에서 조회할 때")
        class Context_when_sunday_active {

            @Test
            @DisplayName("활성화 상태와 히스토리를 반환해야 한다")
            void it_returns_active_status_with_history() {
                // Given
                given(sundayReservationRedisUtil.isSundayActive()).willReturn(true);
                given(cycleLogRepository.findAllOrderByCreatedAtDesc()).willReturn(List.of());

                // When
                var result = querySundayReservationStatusService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.isActive()).isTrue();
                assertThat(result.history()).isEmpty();
            }
        }

        @Nested
        @DisplayName("일요일 예약이 비활성화된 상태에서 조회할 때")
        class Context_when_sunday_inactive {

            @Test
            @DisplayName("비활성화 상태와 히스토리를 반환해야 한다")
            void it_returns_inactive_status_with_history() {
                // Given
                given(sundayReservationRedisUtil.isSundayActive()).willReturn(false);
                given(cycleLogRepository.findAllOrderByCreatedAtDesc()).willReturn(List.of());

                // When
                var result = querySundayReservationStatusService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.isActive()).isFalse();
                assertThat(result.history()).isEmpty();
            }
        }
    }
}
