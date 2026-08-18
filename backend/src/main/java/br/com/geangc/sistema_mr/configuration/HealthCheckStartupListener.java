/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author gean.carneiro
 */
public class HealthCheckStartupListener implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckStartupListener.class);
    
    private final HealthEndpoint healthEndpoint;
    private final ConfigurableApplicationContext context;
    
    public HealthCheckStartupListener(
            final HealthEndpoint healthEndpoint,
            final ConfigurableApplicationContext context
    ) {
        this.healthEndpoint = healthEndpoint;
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var health = healthEndpoint.health();
        
        String statusCode = health.getStatus().getCode();
        
        if(!"UP".equalsIgnoreCase(statusCode)) {
            logger.error("🛑 FALHA NO HEALTH CHECK: Status atual eh '{}'. Conexao com servicos falhou!", statusCode);
            
            int exitCode = SpringApplication.exit(context, () -> 1);
            System.exit(exitCode);
        }
        
        logger.info("✅ Todos os Health Checks estao UP! Aplicação pronta.");
    }
    
    
    
}
