package br.com.geangc.sistema_mr.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private JwtEncoder jwtEncoder;

    @Test
    void rejectsMissingTokenWithoutCallingGoogle() {
        AuthController controller = new AuthController(googleIdTokenVerifier, jwtEncoder);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.authenticate(" ").getStatusCode());
        verifyNoInteractions(googleIdTokenVerifier, jwtEncoder);
    }

    @Test
    void rejectsTokenWhenGoogleVerificationReturnsNull() throws Exception {
        AuthController controller = new AuthController(googleIdTokenVerifier, jwtEncoder);
        when(googleIdTokenVerifier.verify("invalid-token")).thenReturn(null);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.authenticate("invalid-token").getStatusCode());
        verifyNoInteractions(jwtEncoder);
    }
}
