package com.nyxora.swiftdrop.auth.repository;

import com.nyxora.swiftdrop.auth.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    Optional<Merchant> findByOauthClientId(String oauthClientId);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByOauthClientId(String oauthClientId);
}
