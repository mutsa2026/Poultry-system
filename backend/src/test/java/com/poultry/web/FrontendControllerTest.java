package com.poultry.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FrontendController()).build();

    @Test
    void frontendRoutesForwardToTheSpaEntryPoint() throws Exception {
        for (String path : new String[] {
            "/batches", "/sales", "/customers", "/farm", "/analytics", "/medication",
            "/map", "/more", "/account", "/processing", "/suppliers", "/team"
        }) {
            mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        }
    }
}
