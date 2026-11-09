package com.nyxora.swiftdrop.parcel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing for automated tracking of entity
 * creation and modification timestamps across all parcel-service entities.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

