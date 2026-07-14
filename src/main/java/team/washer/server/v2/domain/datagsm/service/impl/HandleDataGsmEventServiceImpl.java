package team.washer.server.v2.domain.datagsm.service.impl;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.datagsm.service.HandleDataGsmEventService;
import team.washer.server.v2.domain.datagsm.support.DataGsmEventIdempotencySupport;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class HandleDataGsmEventServiceImpl implements HandleDataGsmEventService {

    private static final String STUDENT_UPDATED_EVENT = "student.updated";

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DataGsmEventIdempotencySupport idempotencySupport;

    @Override
    @Transactional
    public void execute(byte[] rawBody) {
        final var payload = parsePayload(rawBody);
        final var eventId = requiredText(payload, "id");
        if (idempotencySupport.isProcessed(eventId)) {
            log.info("DataGSM event already processed eventId={}", eventId);
            return;
        }

        final var event = requiredText(payload, "event");
        if (STUDENT_UPDATED_EVENT.equals(event)) {
            handleStudentUpdated(payload);
        } else {
            log.info("DataGSM event ignored eventId={} event={}", eventId, event);
        }

        markProcessedAfterCommit(eventId);
    }

    private JsonNode parsePayload(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new IllegalArgumentException("DataGSM 이벤트 페이로드를 파싱할 수 없습니다.", e);
        }
    }

    private void handleStudentUpdated(JsonNode payload) {
        final var newItems = payload.path("data").path("new");
        if (!newItems.isArray()) {
            throw new IllegalArgumentException("DataGSM 학생 이벤트의 new 목록이 올바르지 않습니다.");
        }

        newItems.forEach(item -> {
            final var student = item.path("object");
            if (!student.isObject() || student.isEmpty()) {
                return;
            }

            final var studentId = extractText(student, "student_id", "studentId", "student_number", "studentNumber");
            if (studentId == null) {
                log.warn("DataGSM student event ignored because student id is missing");
                return;
            }

            userRepository.findByStudentId(studentId).ifPresentOrElse(user -> {
                user.updateDataGsmInfo(extractText(student, "name"),
                        extractText(student, "dormitory_room", "dormitoryRoom", "room_number", "roomNumber"),
                        extractInteger(student, "grade"),
                        extractInteger(student, "dormitory_floor", "dormitoryFloor", "floor"),
                        mapRole(extractText(student, "role")));
                log.info("DataGSM student event applied studentId={}", studentId);
            }, () -> log.info("DataGSM student event ignored because user does not exist studentId={}", studentId));
        });
    }

    private String requiredText(JsonNode node, String fieldName) {
        final var value = extractText(node, fieldName);
        if (value == null) {
            throw new IllegalArgumentException("DataGSM 이벤트 필수 필드가 누락되었습니다: " + fieldName);
        }
        return value;
    }

    private void markProcessedAfterCommit(String eventId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            idempotencySupport.markProcessed(eventId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                idempotencySupport.markProcessed(eventId);
            }
        });
    }

    private String extractText(JsonNode node, String... fieldNames) {
        for (final var fieldName : fieldNames) {
            final var value = node.path(fieldName);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
            if (value.isNumber()) {
                return value.asText();
            }
        }
        return null;
    }

    private Integer extractInteger(JsonNode node, String... fieldNames) {
        for (final var fieldName : fieldNames) {
            final var value = node.path(fieldName);
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual() && value.asText().matches("\\d+")) {
                return Integer.valueOf(value.asText());
            }
        }
        return null;
    }

    private UserRole mapRole(String role) {
        if (role == null) {
            return null;
        }
        final var normalizedRole = role.replace("-", "_").toUpperCase();
        if ("DORMITORY_MANAGER".equals(normalizedRole) || "DORMITORYMANAGER".equals(normalizedRole)) {
            return UserRole.DORMITORY_COUNCIL;
        }
        return UserRole.USER;
    }
}
