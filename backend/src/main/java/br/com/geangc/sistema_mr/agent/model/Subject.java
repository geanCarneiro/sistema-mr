package br.com.geangc.sistema_mr.agent.model;

import java.time.Instant;
import java.util.UUID;

public record Subject(
        UUID id,
        String ownerSubject,
        String kind,
        String title,
        Instant createdAt
) {}
