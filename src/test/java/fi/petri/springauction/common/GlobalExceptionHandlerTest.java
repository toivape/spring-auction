package fi.petri.springauction.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    @Test
    void handlesUnexpectedExceptionsAsAProblemDetail() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("boom"));

        assertEquals(500, problem.getStatus());
        assertEquals("An unexpected error occurred", problem.getDetail());
    }

    @Test
    void handlesValidationFailuresAsAProblemDetail() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ProblemDetail problem = handler.handleValidation(mock(MethodArgumentNotValidException.class));

        assertEquals(400, problem.getStatus());
        assertEquals("Validation failed", problem.getDetail());
    }

}
