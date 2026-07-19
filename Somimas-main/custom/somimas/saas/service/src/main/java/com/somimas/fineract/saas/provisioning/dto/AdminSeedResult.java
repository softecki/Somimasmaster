package com.somimas.fineract.saas.provisioning.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminSeedResult {

    public enum Status {
        CREATED,
        EXISTS,
        FAILED
    }

    String identifier;
    Status status;
    Long appUserId;
    String message;
}
