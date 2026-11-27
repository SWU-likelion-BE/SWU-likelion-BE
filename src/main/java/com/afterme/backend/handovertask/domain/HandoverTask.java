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

    public Long getId(){return id;}

    public Long getProjectId(){return projectId;}

    public String getTitle(){return title;}

    public OffsetDateTime getCreatedAt(){return createdAt;}

    public OffsetDateTime getUpdatedAt(){return updatedAt;}
}
