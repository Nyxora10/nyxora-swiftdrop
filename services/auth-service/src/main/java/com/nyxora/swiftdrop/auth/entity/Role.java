package com.nyxora.swiftdrop.auth.entity;

import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

/**
 * Role entity representing an authorization group in SwiftDrop RBAC.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(name = "description", length = 255)
    private String description;
}

