package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.CreateFileRequest;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
public class GlobalExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/test/not-found")
        void notFound() {
            throw new FileNotFoundInDbException("abc");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody CreateFileRequest request) {
        }

        @GetMapping("/test/param")
        void param(@RequestParam @NotBlank String q) {
        }

        @GetMapping("/test/constraint")
        void constraint() {
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            throw new ConstraintViolationException(validator.validate(new Sample("")));
        }

        @GetMapping("/test/number/{n}")
        void number(@PathVariable int n) {
        }

        @PostMapping("/test/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1024);
        }
    }

    record Sample(@NotBlank String name) {
    }

    @Test
    void fileNotFoundMapsTo404ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.detail").value("No file with id: abc"));
    }

    @Test
    void genericExceptionMapsTo500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void invalidBodyMapsTo400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"\",\"size\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.errors.kind").exists())
                .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void malformedBodyMapsTo400() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Malformed or unreadable request body."));
    }

    @Test
    void missingRequestParamMapsTo400() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Missing required request parameter: q."));
    }

    @Test
    void blankRequestParamMapsTo400() throws Exception {
        mockMvc.perform(get("/test/param").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").exists());
    }

    @Test
    void constraintViolationMapsTo400() throws Exception {
        mockMvc.perform(get("/test/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void unknownPathMapsTo404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."));
    }

    @Test
    void maxUploadSizeMapsTo413() throws Exception {
        mockMvc.perform(post("/test/too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.title").value("Content too large"))
                .andExpect(jsonPath("$.detail").value("Upload exceeds the maximum allowed size."));
    }

    @Test
    void wrongMethodMapsTo405() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Method not allowed"))
                .andExpect(jsonPath("$.detail").value("HTTP method POST is not supported for this resource."));
    }

    @Test
    void unsupportedMediaTypeMapsTo415() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").value("Unsupported media type"))
                .andExpect(jsonPath("$.detail").value("Unsupported content type: text/plain"));
    }

    @Test
    void parameterTypeMismatchMapsTo400() throws Exception {
        mockMvc.perform(get("/test/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'n'."));
    }
}
