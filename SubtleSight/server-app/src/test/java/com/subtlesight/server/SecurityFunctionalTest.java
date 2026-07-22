package com.subtlesight.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityFunctionalTest {
    static final Path DATA=Path.of(System.getProperty("java.io.tmpdir"),"subtlesight-security-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("subtlesight.data-dir",DATA::toString);registry.add("subtlesight.security.password",()->"test-password");}
    private final MockMvc mvc;
    @org.springframework.beans.factory.annotation.Autowired SecurityFunctionalTest(MockMvc mvc){this.mvc=mvc;}

    @Test void localStatusIsPublicAndIssuesCsrfCookie()throws Exception{mvc.perform(get("/api/v1/auth/status")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.user").value("local")).andExpect(cookie().exists("XSRF-TOKEN"));mvc.perform(get("/api/v1/system/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.sources").exists());mvc.perform(get("/api/v1/workspace/overview")).andExpect(status().isOk()).andExpect(jsonPath("$.summary.sources").exists()).andExpect(jsonPath("$.jobs").isArray()).andExpect(jsonPath("$.trends").isArray());mvc.perform(get("/api/v1/workspace/trends")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());}
    @Test void knowledgeSearchResearchAndAgentApisAreNotExposed()throws Exception{mvc.perform(get("/api/v1/search?q=test")).andExpect(status().isNotFound());mvc.perform(get("/api/v1/stories")).andExpect(status().isNotFound());mvc.perform(get("/api/v1/research")).andExpect(status().isNotFound());mvc.perform(post("/api/v1/research").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isNotFound());mvc.perform(post("/api/v1/discovery").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isNotFound());mvc.perform(post("/api/v1/agent").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isNotFound());}
    @Test void csrfAndOriginAreBothEnforcedAndValidLocalWriteSucceeds()throws Exception{mvc.perform(post("/api/v1/sources").contentType("application/json").content(sourceJson())).andExpect(status().isForbidden());mvc.perform(post("/api/v1/sources").with(csrf()).header("Origin","https://evil.example").contentType("application/json").content(sourceJson())).andExpect(status().isForbidden());mvc.perform(post("/api/v1/sources").with(csrf()).header("Origin","http://127.0.0.1:8080").contentType("application/json").content(sourceJson())).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fixture RSS"));mvc.perform(post("/api/v1/sources/bootstrap-real").with(csrf()).header("Origin","http://127.0.0.1:8080")).andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("HN"));}
    private String sourceJson(){return "{\"name\":\"Fixture RSS\",\"type\":\"RSS\",\"kind\":\"PERSISTENT\",\"endpoint\":\"https://example.com/rss\",\"schedule\":\"0 0 * * * *\",\"tier\":\"PROFESSIONAL\",\"topics\":[\"AI\"]}";}
}
