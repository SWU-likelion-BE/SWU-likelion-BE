package com.afterme.backend.handovertask.service;

import com.afterme.backend.global.security.TokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HandoverTaskApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired TokenProvider tokens;

    @Test
    void storesPartialUpdatesAndClearsExplicitNulls() throws Exception {
        Long userId = jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', 'Predecessor', current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test");
        Long organizationId = jdbc.queryForObject(
                "insert into organization(owner_id) values (?) returning id", Long.class, userId);
        Long projectId = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Task details project', 'Role', current_date + 1) returning id
                """, Long.class, organizationId);
        Long memberId = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, 'MEMBER', 'ACTIVE') returning id
                """, Long.class, organizationId, userId);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, 'PREDECESSOR')",
                projectId, memberId);
        String authorization = "Bearer " + tokens.createAccessToken(userId, Instant.now());
        String tasksPath = "/api/handovers/" + projectId + "/tasks";

        mvc.perform(post(tasksPath).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Monthly report"}
                                """))
                .andExpect(status().isCreated());
        entityManager.flush();
        Long taskId = jdbc.queryForObject("select id from handover_task where project_id = ?", Long.class, projectId);
        String taskPath = tasksPath + "/" + taskId;

        mvc.perform(get(taskPath).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview").value(nullValue()))
                .andExpect(jsonPath("$.data.details").value(nullValue()));

        mvc.perform(patch(taskPath).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overview":"Monthly sales","details":"Run on the first business day"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Monthly report"))
                .andExpect(jsonPath("$.data.overview").value("Monthly sales"))
                .andExpect(jsonPath("$.data.details").value("Run on the first business day"));
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(taskPath).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview").value("Monthly sales"))
                .andExpect(jsonPath("$.data.details").value("Run on the first business day"));

        mvc.perform(patch(taskPath).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"details":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details").value(nullValue()));
        entityManager.flush();
        entityManager.clear();

        for (String body : new String[] {"{\"title\":null}", "{\"title\":\" \"}"}) {
            mvc.perform(patch(taskPath).header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(get(taskPath).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Monthly report"))
                .andExpect(jsonPath("$.data.overview").value("Monthly sales"))
                .andExpect(jsonPath("$.data.details").value(nullValue()));
    }
}
