package com.afterme.backend.handover.repository;

import com.afterme.backend.handover.domain.HandoverProject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface HandoverProjectRepository extends JpaRepository<HandoverProject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from HandoverProject project where project.id = :projectId")
    Optional<HandoverProject> findByIdForUpdate(@Param("projectId") Long projectId);

    // 목록과 상세에 같은 접근 조건을 적용
    String ACCESS_CONDITION = """
        (
            exists (
                select participant.id
                from ProjectParticipant participant
                where participant.project = project
                and participant.member.user.id = :userId
            )
            or exists (
                select orgMember.id
                from OrganizationMember orgMember
                where orgMember.organization = project.organizationId
                  and orgMember.user.id = :userId
                  and orgMember.role = com.afterme.backend.organization.domain.OrganizationRole.ADMIN
                  and orgMember.status = com.afterme.backend.organization.domain.OrganizationMemberStatus.ACTIVE
            )
        )
        """;

    @Query("""
        select project
        from HandoverProject project
        where (:organizationId is null or project.organizationId.id = :organizationId)
          and (:status is null or project.status = :status)
          and
        """ + ACCESS_CONDITION)
    Page<HandoverProject> findAccessibleByUserId(
            @Param("userId") Long userId,
            @Param("organizationId") Long organizationId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
        select project
        from HandoverProject project
        where project.id = :projectId
          and
        """ + ACCESS_CONDITION)
    Optional<HandoverProject> findAccessibleById(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );
}
