package com.afterme.backend.handoverdocument.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handoverdocument.repository.PublishedDocumentSectionRepository;
import com.afterme.backend.handoverdocument.repository.PublishedDocumentSectionRepository.PublishedSection;
import com.afterme.backend.qa.port.PublishedSectionReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentPublishedSectionReader implements PublishedSectionReader {
    private final PublishedDocumentSectionRepository sections;

    @Override
    public SectionReference requirePublishedSection(Long projectId, Long sectionId) {
        return sections.findPublished(projectId, sectionId)
                .map(DocumentPublishedSectionReader::reference)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    public List<SectionReference> listPublishedSections(Long projectId) {
        return sections.listCurrentPublished(projectId).stream()
                .map(DocumentPublishedSectionReader::reference).toList();
    }

    private static SectionReference reference(PublishedSection section) {
        return new SectionReference(section.getProjectId(), section.getVersionId(), section.getSectionId(),
                section.getTaskId(), section.getTitle(), section.getDisplayOrder(), section.getContent());
    }
}
