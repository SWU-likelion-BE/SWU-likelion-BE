package com.afterme.backend.handover.domain;

import com.afterme.backend.organization.domain.Organization;
import com.google.errorprone.annotations.InlineMeValidationDisabled;
import jakarta.persistence.*;

import java.time.LocalDate;

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

    public HandoverProject(){}

    public HandoverProject(Organization organizationId, String title,
                           String roleName, LocalDate deadline){
        this.organizationId = organizationId;
        this.title = title;
        this.roleName = roleName;
        this.deadLine = deadline;
    }

    public Long getId(){
        return id;
    }
}
