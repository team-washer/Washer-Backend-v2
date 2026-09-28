package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationCreationSupport는")
class ReservationCreationSupportTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private WashingBanRepository washingBanRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("활성 조회에서 제외된 만료 RESERVED를 개인·호실 중복으로 판정하지 않는다")
    void ignoresExpiredReservedReservationsUsingCurrentActiveQueries() {
        final var support = new ReservationCreationSupport(reservationRepository,
                machineRepository,
                washingBanRepository,
                userRepository);
        final var user = mock(User.class);
        final var machine = mock(Machine.class);
        given(user.getRoomNumber()).willReturn("301");
        given(machine.getAvailability()).willReturn(MachineAvailability.AVAILABLE);
        given(reservationRepository.findCurrentlyActiveByMachine(machine)).willReturn(List.of());
        given(reservationRepository.findCurrentlyActiveByUser(user)).willReturn(List.of());
        given(reservationRepository.findCurrentlyActiveByRoomNumber("301")).willReturn(List.of());

        assertThatCode(() -> support.validateMachineAndReservations(user, machine)).doesNotThrowAnyException();

        verify(reservationRepository).findCurrentlyActiveByUser(user);
        verify(reservationRepository).findCurrentlyActiveByRoomNumber("301");
    }
}
