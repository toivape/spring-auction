package fi.petri.springauction.security;

import org.springframework.data.repository.ListCrudRepository;

public interface AdminCredentialRepository extends ListCrudRepository<AdminCredential, Long> {
}
