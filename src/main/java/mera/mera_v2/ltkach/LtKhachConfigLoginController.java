package mera.mera_v2.ltkach;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LtKhachConfigLoginController {

    @GetMapping("/lt-khach/config-login")
    public String loginPage() {
        return "lt-khach-config-login";
    }

    @PostMapping("/lt-khach/config-login")
    public String verifyPassword(@RequestParam String password, 
                                  jakarta.servlet.http.HttpSession session,
                                  jakarta.servlet.http.HttpServletResponse response) {
        if (LtKhachConfigInterceptor.PROTECTED_PASSWORD.equals(password)) {
            session.setAttribute(LtKhachConfigInterceptor.SESSION_KEY, Boolean.TRUE);
            return "redirect:/lt-khach/combo";
        }
        return "redirect:/lt-khach/config-login?error=1";
    }

    @PostMapping("/lt-khach/config-logout")
    public String logout(jakarta.servlet.http.HttpSession session) {
        session.invalidate();
        return "redirect:/lt-khach/config-login";
    }
}
