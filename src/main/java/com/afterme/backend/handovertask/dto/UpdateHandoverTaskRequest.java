package com.afterme.backend.handovertask.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;

public class UpdateHandoverTaskRequest{

    private String title;
    private boolean titleProvided;

    private String overview;
    private boolean overviewProvided;

    private String details;
    private boolean detailsProvided;

    public UpdateHandoverTaskRequest(){}

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public UpdateHandoverTaskRequest(String title){
        setTitle(title);
    }

    @JsonSetter("title")
    public void setTitle(String title){
        this.title = title;
        this.titleProvided = true;
    }

    @JsonSetter("overview")
    public void setOverview(String overview){
        this.overview = overview;
        this.overviewProvided = true;
    }

    @JsonSetter("details")
    public void setDetails(String details){
        this.details = details;
        this.detailsProvided = true;
    }

    @AssertTrue(message = "제목은 비어 있거나 공백일 수 없으며 255자 이하여야 합니다.")
    public boolean isTitleValid(){
        return !titleProvided
                || (title != null && !title.isBlank() && title.length() <= 255);
    }

    public boolean hasTitle(){
        return titleProvided;
    }

    public String title(){
        return title;
    }

    public boolean hasOverview(){
        return overviewProvided;
    }

    public String overview(){
        return overview;
    }

    public boolean hasDetails(){
        return detailsProvided;
    }

    public String details(){
        return details;
    }
}
