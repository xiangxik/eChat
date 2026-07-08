package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.api.admin.AdminAuthController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminIdentityControllerTest extends PostgresIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final String PASSWORD = "Stronger-Test-Password-1";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void managesRbacAndAuthenticatesUserSessions() throws Exception {
        Long permissionId = createPermission();
        Long roleId = createRole(permissionId);
        Long adminRoleId = systemRoleId("ADMIN");
        Long userId = createUser(adminRoleId);

        mockMvc.perform(get("/api/admin/identity/users").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'rbac-admin')]").exists())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        MvcResult login = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"rbac-admin",
                                  "password":"%s"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie(AdminAuthController.ADMIN_SESSION_COOKIE);
        mockMvc.perform(get("/api/admin/providers").cookie(sessionCookie))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/identity/users/{id}", userId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"rbac-admin",
                                  "displayName":"RBAC Admin Updated",
                                  "tenantId":"tenant-a",
                                  "enabled":true,
                                                                                                                                        "roleIds":[%d]
                                }
                                                                                                                                """.formatted(adminRoleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("RBAC Admin Updated"));

        mockMvc.perform(delete("/api/admin/identity/users/{id}", userId).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/identity/roles/{id}", roleId).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/identity/permissions/{id}", permissionId).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void defaultAdminIsSuperAdminAndCannotBeDeleted() throws Exception {
        String users = mockMvc.perform(get("/api/admin/identity/users").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin' && @.systemUser == true)]").exists())
                .andExpect(jsonPath("$[?(@.username == 'admin' && @.roles[?(@.code == 'SUPER_ADMIN')])]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long defaultAdminId = JsonTestSupport.idByUsername(users, "admin");
        mockMvc.perform(delete("/api/admin/identity/users/{id}", defaultAdminId).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("System admin user cannot be deleted")));
    }

    private Long createPermission() throws Exception {
        String response = mockMvc.perform(post("/api/admin/identity/permissions")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"TEST_MANAGE",
                                  "name":"Test Manage",
                                  "description":"Test permission"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEST_MANAGE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.id(response);
    }

    private Long createRole(Long permissionId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/identity/roles")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"TEST_ADMIN",
                                  "name":"Test Admin",
                                  "description":"Test role",
                                  "permissionIds":[%d]
                                }
                                """.formatted(permissionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionIds[0]").value(permissionId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.id(response);
    }

    private Long createUser(Long roleId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/identity/users")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"rbac-admin",
                                  "displayName":"RBAC Admin",
                                  "password":"%s",
                                  "tenantId":"tenant-a",
                                  "enabled":true,
                                  "roleIds":[%d]
                                }
                                """.formatted(PASSWORD, roleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("rbac-admin"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.id(response);
    }

    private Long systemRoleId(String code) throws Exception {
        String response = mockMvc.perform(get("/api/admin/identity/roles").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.idByCode(response, code);
    }
}
