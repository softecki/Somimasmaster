package com.somimas.fineract.saas.provisioning.dto;

import lombok.Data;

@Data
public class AdminSeedRequest {

    private String username;
    private String email;
    private String firstName;
    private String lastName;
    /** Fineract DelegatingPasswordEncoder-compatible hash (e.g. {bcrypt}$2a$...). */
    private String passwordHash;
}
