package com.afterme.backend.handover.domain;

import com.afterme.backend.organization.domain.OrganizationMember;
import jakarta.persistence.*;

@Entity
@Table(name = "project_participant")
public class ProjectParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private HandoverProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private OrganizationMember member;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ParticipantRole role;


    private ProjectParticipant(
            HandoverProject project,
            OrganizationMember member,
            ParticipantRole role
    ){
        this.project = project;
        this.member = member;
        this.role = role;
    }

    protected ProjectParticipant(){}

    public static ProjectParticipant predecessor(
            HandoverProject project,
            OrganizationMember member
    ){
        return new ProjectParticipant(project, member, ParticipantRole.PREDECESSOR);
    }

}
