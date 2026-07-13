package fi.petri.springauction.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("user")
public record User(
        @Id Long id,
        String googleSubjectId,
        String email,
        String displayName,
        UserRole role,
        Instant createdAt
) {
}
