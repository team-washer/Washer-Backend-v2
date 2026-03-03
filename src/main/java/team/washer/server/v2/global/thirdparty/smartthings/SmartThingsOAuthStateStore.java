package team.washer.server.v2.global.thirdparty.smartthings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * SmartThings OAuth state 파라미터 저장소
 *
 * <p>
 * CSRF 공격 방지를 위해 생성된 state 값을 관리합니다.
 */
@Component
public class SmartThingsOAuthStateStore {

    private final Set<String> validStates = Collections.synchronizedSet(new HashSet<>());

    /**
     * state 값을 저장합니다.
     *
     * @param state
     *            저장할 state 값
     */
    public void save(String state) {
        validStates.add(state);
    }

    /**
     * state 값을 검증하고 제거합니다.
     *
     * @param state
     *            검증할 state 값
     * @return state가 유효하면 true, 그렇지 않으면 false
     */
    public boolean validateAndRemove(String state) {
        return validStates.remove(state);
    }
}
