package team.washer.server.v2.global.common.error.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import team.washer.server.v2.global.common.error.exception.ExpectedException;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;

@Slf4j
@EnableWebMvc
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private DiscordErrorNotificationService discordErrorNotificationService;

    @ExceptionHandler(ExpectedException.class)
    private CommonApiResDto expectedException(ExpectedException ex) {
        log.warn("ExpectedException : {} ", ex.getMessage());
        log.trace("ExpectedException 세부사항 : ", ex);
        return CommonApiResDto.error(ex.getMessage(), ex.getStatusCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class})
    public CommonApiResDto validationException(Exception ex) {
        log.warn("검증 실패 : {}", ex.getMessage());
        log.trace("검증 실패 세부사항 : ", ex);
        String errorMessage;
        if (ex instanceof MethodArgumentNotValidException) {
            errorMessage = methodArgumentNotValidExceptionToJson((MethodArgumentNotValidException) ex);
        } else {
            errorMessage = ex.getMessage();
        }
        return CommonApiResDto.error(errorMessage, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public CommonApiResDto unExpectedException(RuntimeException ex) {
        log.error("기타 런타임 예외 발생 : {}", ex.getMessage(), ex);
        if (discordErrorNotificationService != null) {
            discordErrorNotificationService.notifyError(ex);
        }

        return CommonApiResDto.error("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public CommonApiResDto handleException(Exception ex) {
        log.error("핸들링되지 않은 예외 발생 : {}", ex.getMessage(), ex);

        if (discordErrorNotificationService != null) {
            discordErrorNotificationService.notifyError(ex);
        }

        return CommonApiResDto.error("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public CommonApiResDto noHandlerFoundException(NoHandlerFoundException ex) {
        log.warn("요청한 리소스를 찾을 수 없음 : {}", ex.getMessage());
        log.trace("요청한 리소스를 찾을 수 없음 세부사항 : ", ex);
        return CommonApiResDto.error(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonApiResDto maxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("파일 크기 초과 : {}", ex.getMessage());
        log.trace("파일 크기 초과 세부사항 : ", ex);
        return CommonApiResDto.error("파일 크기가 허용된 최대 크기를 초과했습니다.", HttpStatus.CONTENT_TOO_LARGE);
    }

    private static String methodArgumentNotValidExceptionToJson(MethodArgumentNotValidException ex) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> fieldErrors = new HashMap<>();
        Map<String, String> globalErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        });
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            globalErrors.put(error.getObjectName(), error.getDefaultMessage());
        });
        result.put("fieldErrors", fieldErrors);
        if (!globalErrors.isEmpty()) {
            result.put("globalErrors", globalErrors);
        }
        return new JSONObject(result).toString().replace("\"", "'");
    }
}
