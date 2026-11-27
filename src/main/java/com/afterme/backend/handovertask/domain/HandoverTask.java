package com.afterme.backend.handovertask.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
public class HandoverTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(columnDefinition = "text")
    private String details;

    protected HandoverTask(){}

    public HandoverTask(Long projectId, String title, OffsetDateTime now){
        this.projectId = projectId;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String title, OffsetDateTime now){
        this.title = title;
        this.updatedAt = now;
    }

    public void updateOverview(String overview, OffsetDateTime now){
        this.overview = overview;
        this.updatedAt = now;
    }

    public void updateDetails(String details, OffsetDateTime now){
        this.details = details;
        this.updatedAt = now;
    }

    public Long getId(){return id;}

    public Long getProjectId(){return projectId;}

    public String getTitle(){return title;}

    public OffsetDateTime getCreatedAt(){return createdAt;}

    public OffsetDateTime getUpdatedAt(){return updatedAt;}

    public String getOverview(){return overview;}

    public String getDetails(){return details;}
}
