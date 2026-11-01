package com.nyxora.swiftdrop.auth.repository;

import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRoleNameAndStatus(RoleName roleName, UserStatus status);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    @Query("""
        SELECT u FROM User u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:roleName IS NULL OR u.role.name = :roleName)
          AND (
              :search IS NULL OR :search = '' OR
              LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
              LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """)
    Page<User> findAllWithFilters(
            @Param("status") UserStatus status,
            @Param("roleName") RoleName roleName,
            @Param("search") String search,
            Pageable pageable
    );
}
