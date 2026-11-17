package com.afterme.backend.organization.repository;

import com.afterme.backend.organization.domain.OrganizationMember;
import com.afterme.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember>findByOrganization_IdAndUser_Id(
            Long organizationId, Long userId
    );

    List<OrganizationMember> findAllByOrganization_IdAndIdIn(
            Long organizationId, Collection<Long> memberIds
    );

}
