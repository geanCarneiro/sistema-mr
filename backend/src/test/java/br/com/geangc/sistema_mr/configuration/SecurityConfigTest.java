package br.com.geangc.sistema_mr.configuration;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void encoderAndDecoderUseTheSameValidatedSecret() {
        SecurityConfig configuration = new SecurityConfig(SECRET);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sistema-mr")
                .subject("google-user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();

        String token = configuration.jwtEncoder()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                ))
                .getTokenValue();

        assertEquals("google-user-id", configuration.jwtDecoder().decode(token).getSubject());
    }

    @Test
    void rejectsShortSecrets() {
        SecurityConfig configuration = new SecurityConfig("short-secret");

        assertThrows(IllegalStateException.class, configuration::jwtEncoder);
    }
}
