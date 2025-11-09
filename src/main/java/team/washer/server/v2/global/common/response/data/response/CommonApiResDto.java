package team.washer.server.v2.global.common.response.data.response;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommonApiResDto<T> {

    @Schema(description = "상태 메시지", example = "OK")
    HttpStatus status;

    @Schema(description = "상태 코드", example = "200")
    int code;

    @Schema(description = "메시지", example = "완료되었습니다.")
    String message;

    @Schema(description = "데이터", nullable = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    T data;

    public static CommonApiResDto success(@Nonnull String message) {
        return new CommonApiResDto(HttpStatus.OK, HttpStatus.OK.value(), message, null);
    }

    public static CommonApiResDto created(@Nonnull String message) {
        return new CommonApiResDto(HttpStatus.CREATED, HttpStatus.CREATED.value(), message, null);
    }

    public static CommonApiResDto error(@Nonnull String message, @Nonnull HttpStatus status) {
        return new CommonApiResDto(status, status.value(), message, null);
    }
}
