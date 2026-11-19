package com.afterme.backend.organization.domain;

import com.afterme.backend.user.domain.User;
import jakarta.persistence.*;

import static com.afterme.backend.organization.domain.OrganizationRole.ADMIN;

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

    public boolean isActiveAdmin(){

        return status == OrganizationMemberStatus.ACTIVE
                && role == OrganizationRole.ADMIN;

    }

    public boolean isActive(){
        return status == OrganizationMemberStatus.ACTIVE;
    }

    public Long getId(){ return id; }
    public User getUser(){ return user;}

}
