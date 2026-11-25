package com.afterme.backend.handover.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "handover_invitation")
public class HandoverInvitation {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private HandoverProject project;

    @Column(name = "used_at")
    private Instant usedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_name", nullable = false)
    private String inviteName;

    @Column(name = "invite_email", nullable = false)
    private String inviteEmail;

    @Column(name = "invite_role", nullable = false)
    private String inviteRole;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    protected HandoverInvitation() {
    }

    public HandoverInvitation(
            HandoverProject project,
            String inviteName,
            String inviteEmail,
            String inviteRole,
            String code
    ){
        this.project = project;
        this.inviteName = inviteName;
        this.inviteEmail = inviteEmail;
        this.inviteRole = inviteRole;
        this.code = code;
    }

    public HandoverProject getProject() {
        return project;
    }

    public String getInviteEmail() {
        return inviteEmail;
    }

    public String getInviteRole() {
        return inviteRole;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public String getCode(){
        return code;
    }
}
