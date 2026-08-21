/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
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
    
    private final String clientId;

    public AuthController(
            @Value("${google.client-id}") final String clientId
    ) {
        this.clientId = clientId;
    }
        
    @PostMapping("/api/v1/auth/google")
    public ResponseEntity<String> authenticate(
            @RequestBody String idToken
    ) {
        
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
        .setAudience(Collections.singletonList(this.clientId))
        .build();
        
        try {
            GoogleIdToken token = verifier.verify(idToken);
            
            GoogleIdToken.Payload userData = token.getPayload();
            
            userData.forEach((k, v) -> logger.info("{0}: {1}", k, String.valueOf(v)));            
            
            
        } catch (GeneralSecurityException ex) {
            UUID uuid = UUID.randomUUID();
            
            logger.error("error uuid: " + uuid, ex);
            return ResponseEntity
                    .status(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED)
                    .body("Token do Google inválido ou expirado.");
            
        } catch (IOException ex) {
            UUID uuid = UUID.randomUUID();
            
            logger.error("error uuid: " + uuid, ex);
            return ResponseEntity
                    .status(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
                    .body("erro(" + uuid + "): " + ex.getMessage());
        }
        
        
        return null;
    }
    
    
}
