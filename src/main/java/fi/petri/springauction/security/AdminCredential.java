package fi.petri.springauction.security;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("admin_credential")
public record AdminCredential(
        @Id Long userId,
        String passwordHash,
        Instant updatedAt
) {
}
