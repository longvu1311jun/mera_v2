package mera.mera_v2.customer.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchInfoPageController {

  @GetMapping("/search-info")
  public String searchInfo() {
    return "searchInfo";
  }

  /** Mo tu menu trai: giu sidebar, nhung /search-info vao khung ben phai. */
  @GetMapping("/tim-khach-hang")
  public String searchInfoInShell(@RequestParam(required = false) String phone, Model model) {
    model.addAttribute("phone", phone);
    return "searchInfoShell";
  }
}
