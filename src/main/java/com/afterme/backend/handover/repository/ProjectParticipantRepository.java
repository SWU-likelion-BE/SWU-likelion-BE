package com.afterme.backend.handover.repository;

import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.domain.ProjectParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectParticipantRepository extends JpaRepository<ProjectParticipant, Long> {

    @Query("""
    select distinct participant.project.id as projectId, participant.role as role
    from ProjectParticipant participant
    where participant.project.id in :projectIds
      and participant.member.user.id = :userId
    """)
    List<ProjectRole> findRolesByProjectIdsAndUserId(
            @Param("projectIds") List<Long> projectIds,
            @Param("userId") Long userId
    );

    @Query("""
        select participant
        from ProjectParticipant participant
        join fetch participant.member orgMember
        join fetch orgMember.user
        where participant.project.id = :projectId
        order by participant.id
        """)
    List<ProjectParticipant> findParticipantsByProjectId(@Param("projectId") Long projectId);

    interface ProjectRole {
        Long getProjectId();

        ParticipantRole getRole();
    }

    boolean existsByProject_IdAndMember_User_IdAndRole(
            Long projectId,
            Long userId,
            ParticipantRole role
    );
}
