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

    public HandoverProject(){}

    public HandoverProject(Organization organizationId, String title,
                           String roleName, LocalDate deadline){
        this.organizationId = organizationId;
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
