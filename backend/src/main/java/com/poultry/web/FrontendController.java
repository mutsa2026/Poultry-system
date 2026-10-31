package com.poultry.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    @GetMapping({
        "/batches", "/sales", "/customers", "/farm", "/analytics", "/medication",
        "/map", "/more", "/account", "/processing", "/suppliers", "/team"
    })
    public String frontendRoute() {
        return "forward:/index.html";
    }
}
