package team.washer.server.v2.global.common.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** 콘솔 로그에도 CloudWatch와 동일한 민감정보 마스킹 규칙을 적용합니다. */
public class SensitiveLogMaskingLayout extends PatternLayout {

    @Override
    public String doLayout(final ILoggingEvent event) {
        return SensitiveLogSanitizer.sanitize(super.doLayout(event));
    }
}
