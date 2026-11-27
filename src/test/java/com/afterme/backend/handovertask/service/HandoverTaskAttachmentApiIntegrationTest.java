package com.afterme.backend.handovertask.service;

import com.afterme.backend.global.security.TokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HandoverTaskAttachmentApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired TokenProvider tokens;

    @Test
    void uploadsListsAndDownloadsOnlyFilesBelongingToTheTask() throws Exception {
        Long predecessor = user();
        Long successor = user();
        Long organization = jdbc.queryForObject(
                "insert into organization(owner_id) values (?) returning id", Long.class, predecessor);
        Long project = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Attachments', 'Role', current_date + 1) returning id
                """, Long.class, organization);
        participant(organization, project, predecessor, "PREDECESSOR");
        participant(organization, project, successor, "SUCCESSOR");
        Long firstTask = task(project);
        Long secondTask = task(project);
        String base = "/api/handovers/" + project + "/tasks/";
        String path = base + firstTask + "/attachments";
        byte[] bytes = {0, 1, (byte) 255};
        MockMultipartFile file = new MockMultipartFile("file", "../report.bin", "application/octet-stream", bytes);

        mvc.perform(multipart(path).file(file).header(HttpHeaders.AUTHORIZATION, token(successor)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(new MockMultipartFile("file", "empty.bin", null, new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, token(predecessor)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(path).file(file).header(HttpHeaders.AUTHORIZATION, token(predecessor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFilename").value("report.bin"))
                .andExpect(jsonPath("$.data.sizeBytes").value(bytes.length));

        entityManager.flush();
        entityManager.clear();
        Long attachmentId = jdbc.queryForObject(
                "select id from handover_task_attachments where task_id = ?", Long.class, firstTask);

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token(successor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(attachmentId))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());
        mvc.perform(get(path + "/" + attachmentId).header(HttpHeaders.AUTHORIZATION, token(successor)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("report.bin")))
                .andExpect(content().bytes(bytes));
        mvc.perform(get(base + secondTask + "/attachments/" + attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, token(successor)))
                .andExpect(status().isNotFound());
    }

    private Long user() {
        return jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', 'Attachment tester', current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test");
    }

    private void participant(Long organization, Long project, Long user, String role) {
        Long member = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, 'MEMBER', 'ACTIVE') returning id
                """, Long.class, organization, user);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)",
                project, member, role);
    }

    private Long task(Long project) {
        return jdbc.queryForObject("""
                insert into handover_task(project_id, title, created_at, updated_at)
                values (?, 'Task', current_timestamp, current_timestamp) returning id
                """, Long.class, project);
    }

    private String token(Long user) {
        return "Bearer " + tokens.createAccessToken(user, Instant.now());
    }
}
