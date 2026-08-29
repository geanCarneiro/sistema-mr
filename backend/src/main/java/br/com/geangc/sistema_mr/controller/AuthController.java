/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpStatusCodes;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author geanCarneiro
 */
@RestController
public class AuthController {
    
    private final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final JwtEncoder jwtEncoder;

    public AuthController(
            final GoogleIdTokenVerifier googleIdTokenVerifier,
            final JwtEncoder jwtEncoder
    ) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.jwtEncoder = jwtEncoder;
    }
        
    @PostMapping("/api/v1/auth/google")
    public ResponseEntity<String> authenticate(
            @RequestBody String idToken
    ) {
        if (idToken == null || idToken.isBlank()) {
            return unauthorized("Token do Google ausente.");
        }

        try {
            GoogleIdToken token = googleIdTokenVerifier.verify(idToken);

            if (token == null) {
                return unauthorized("Token do Google inválido ou expirado.");
            }
            
            GoogleIdToken.Payload userData = token.getPayload();
            
            if (!Boolean.TRUE.equals(userData.getEmailVerified())) {
                return unauthorized("Email não validado, favor validar seu email antes de continuar");
            }
            
            
            
            Instant now = Instant.now();
                        
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("sistema-mr")
                    .issuedAt(now)
                    .expiresAt(now.plus(1, ChronoUnit.DAYS))
                    .subject(userData.getSubject())
                    .claim("nome", userData.get("name"))
                    .claim("avatar", userData.get("picture"))
                    .claim("email", userData.get("email"))
                    .build();
            
            JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
            
            
            return ResponseEntity.ok(
                 this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue()
            );
            
        } catch (GeneralSecurityException ex) {
            UUID uuid = UUID.randomUUID();
            
            logger.error("error uuid: " + uuid, ex);
            return unauthorized("Token do Google inválido ou expirado.");
            
        } catch (IOException ex) {
            UUID uuid = UUID.randomUUID();
            
            logger.error("error uuid: " + uuid, ex);
            return ResponseEntity
                    .status(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
                    .body("Não foi possível validar o token agora. Referência: " + uuid);
        }
    }

    private ResponseEntity<String> unauthorized(String message) {
        return ResponseEntity
                .status(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED)
                .body(message);
    }
}
