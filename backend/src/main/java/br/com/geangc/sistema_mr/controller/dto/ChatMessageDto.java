/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller.dto;

import java.util.Optional;
import org.springframework.ai.chat.messages.Message;

/**
 *
 * @author gean.carneiro
 */
public record ChatMessageDto(
            String messageType,
            String timestamp,
            String content
        ) {
    
    public static ChatMessageDto fromMessage(Message message){
        return new ChatMessageDto(
                message.getMessageType().toString(), 
                Optional.ofNullable(message.getMetadata().get("timestamp"))
                        .map(Object::toString)
                        .orElse(null), 
                message.getText()
        );
    }
    
}
