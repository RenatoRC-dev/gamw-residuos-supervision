package bo.gob.warnes.gamw.residuos.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTests {
    @Test
    void returnsProblemDetailWithoutEchoingRejectedValues() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationFixtureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(post("/test-only/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.violations[0].field").value("value"))
                .andExpect(jsonPath("$.violations[0].rejectedValue").doesNotExist());
    }

    // Instantiated only by this test, outside application component scanning.
    @RestController
    @Profile("validation-fixture")
    static class ValidationFixtureController {
        @PostMapping("/test-only/validation")
        void validate(@Valid @RequestBody ValidationFixture input) { }
    }

    record ValidationFixture(@NotBlank String value) { }
}
