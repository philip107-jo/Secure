package edu.secure.assign.web;

import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Controller
public class PageController {

    private final LoanService loanService;

    private static final String CSRF_TOKEN = "CSRF_TOKEN";
    private static final SecureRandom RANDOM = new SecureRandom();

    public PageController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/login-page")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        return "search";
    }

    @GetMapping("/repay-form")
    public String repayForm(HttpSession session, Model model) {

        String token = (String) session.getAttribute(CSRF_TOKEN);

        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(CSRF_TOKEN, token);
        }

        model.addAttribute("csrfToken", token);

        return "repay-form";
    }

    @PostMapping("/repay-form")
    @ResponseBody
    public String repay(
            @RequestParam long loanId,
            @RequestParam long amount,
            @RequestParam(value = "_csrf", required = false) String requestToken,
            HttpSession session) {

        String sessionToken = (String) session.getAttribute(CSRF_TOKEN);

        if (sessionToken == null || requestToken == null ||
                !MessageDigest.isEqual(
                        sessionToken.getBytes(StandardCharsets.UTF_8),
                        requestToken.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String me = LoginController.requireCustomer(session);

        if (loanId <= 0 || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        return "상환 처리: " + loanService.repay(loanId, me, amount);
    }
}