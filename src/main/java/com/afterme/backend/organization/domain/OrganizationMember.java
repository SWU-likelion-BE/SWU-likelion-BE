package com.afterme.backend.organization.domain;

import com.afterme.backend.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name="organization_member")
public class OrganizationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_role", nullable = false)
    private OrganizationRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrganizationMemberStatus status;
}
