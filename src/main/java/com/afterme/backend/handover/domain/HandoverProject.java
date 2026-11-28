package com.afterme.backend.handover.domain;

import com.afterme.backend.organization.domain.Organization;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name="handover_project")
public class HandoverProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organizationId;

    @Column
    String title;

    @Column
    String roleName;

    @Column
    private String organizationName;

    @Column
    private String affiliationName;

    @Column
    LocalDate deadLine;

    @Column
    String status;

    @Column
    int completeness;

    @Column
    Long resourceVersion;

    @Column
    OffsetDateTime createdAt;

    @Column
    OffsetDateTime completedAt = null;

    @Column
    private Long approvedBy;

    public void extendDeadline(LocalDate deadline) {
        this.deadLine = deadline;
        this.resourceVersion++;
    }

    public Long getApprovedBy() { return approvedBy; }

    public void approveCompletion(Long actorId, int score, java.time.Instant now) {
        if (score < 0 || score > 100 || completedAt != null) {
            throw new com.afterme.backend.global.exception.BusinessException(com.afterme.backend.global.exception.ErrorCode.CONFLICT);
        }
        this.approvedBy = actorId;
        this.completedAt = OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);
        this.status = "COMPLETED";
        this.completeness = score;
        this.resourceVersion = (resourceVersion == null ? 0 : resourceVersion) + 1;
    }

    public HandoverProject(){}

    public HandoverProject(Organization organizationId, String title,
                           String roleName, LocalDate deadline){
        this(organizationId, title, roleName, deadline, null, null);
    }

    public HandoverProject(Organization organizationId, String title, String roleName, LocalDate deadline,
                           String organizationName, String affiliationName) {
        this.organizationId = organizationId;
        this.organizationName = displayName(organizationName);
        this.affiliationName = displayName(affiliationName);
        this.title = title;
        this.roleName = roleName;
        this.deadLine = deadline;
        this.status = "DRAFT";
        this.completeness = 0;
        this.resourceVersion = 0L;
        this.createdAt = OffsetDateTime.now();
    }
    //status, completeness, resourceVersion은 임의 초기값

    //getter
    public Long getId(){
        return id;
    }

    public Organization getOrganization(){
        return organizationId;
    }

    public String getTitle(){
        return title;
    }

    public String getRoleName(){
        return roleName;
    }

    private static String displayName(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public String getOrganizationName() { return organizationName; }

    public String getAffiliationName() { return affiliationName; }

    public LocalDate getDeadLine(){
        return deadLine;
    }

    public String getStatus(){
        return status;
    }

    public int getCompleteness(){
        return completeness;
    }

    public Long getResourceVersion(){
        return resourceVersion;
    }

    public OffsetDateTime getCreatedAt(){
        return createdAt;
    }

    public OffsetDateTime getCompletedAt(){
        return completedAt;
    }
}
