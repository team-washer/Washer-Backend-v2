package team.washer.server.v2.global.config.swagger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Resource;
import team.washer.server.v2.global.common.error.dto.response.CommonErrorResponseResDto;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@WebMvcTest(controllers = OpenApiErrorResponsesIntegrationTest.ErrorTestController.class)
@ImportAutoConfiguration({org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class})
@Import({CommonErrorResponsesCustomizer.class, OpenApiErrorResponsesIntegrationTest.ErrorTestController.class})
@DisplayName("OpenAPI 공통 오류 응답 문서는")
class OpenApiErrorResponsesIntegrationTest {

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("전역 409·503을 추가하고 엔드포인트 전용 설명은 유지한다")
    void preservesEndpointSpecificResponsesAndAddsGlobalResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/test/explicit'].get.responses['409'].description").value("엔드포인트 전용 충돌 설명"))
                .andExpect(jsonPath("$.paths['/test/explicit'].get.responses['503'].description")
                        .value("Redis 또는 외부 서비스 일시 장애"))
                .andExpect(jsonPath("$.paths['/test/generic'].get.responses['409'].description")
                        .value("동시성 충돌 또는 현재 상태와 충돌하는 요청"))
                .andExpect(jsonPath("$.paths['/test/generic'].get.responses['503'].description")
                        .value("Redis 또는 외부 서비스 일시 장애"));
    }

    @RestController
    @RequestMapping("/test")
    @CommonErrorResponses
    static class ErrorTestController {

        @GetMapping("/explicit")
        @ApiResponses(@ApiResponse(responseCode = "409", description = "엔드포인트 전용 충돌 설명", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))))
        String explicit() {
            return "ok";
        }

        @GetMapping("/generic")
        String generic() {
            return "ok";
        }
    }
}
