package mera.mera_v2.ads.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DtAdsPageController {

    @GetMapping("/dt-ads")
    public String dtAdsPage() {
        return "dt-ads";
    }
}
