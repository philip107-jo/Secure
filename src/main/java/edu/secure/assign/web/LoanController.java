package edu.secure.assign.web;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;
import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoanController {

    private final LoanMapper loanMapper;
    private final LoanService loanService;

    public LoanController(LoanMapper loanMapper, LoanService loanService) {
        this.loanMapper = loanMapper;
        this.loanService = loanService;
    }

    @GetMapping("/loans")
    public List<Loan> loans(@RequestParam String customerId, HttpSession session) {
        LoginController.requireCustomer(session);
        return loanMapper.findByCustomer(customerId);
    }

    @GetMapping("/loans/sorted")
    public List<Loan> sorted(@RequestParam String customerId, @RequestParam String sortBy, HttpSession session) {
        LoginController.requireCustomer(session);
        return loanService.findSorted(customerId, sortBy);
    }

    @GetMapping("/loans/{loanId}")
    public Loan loan(@PathVariable long loanId, HttpSession session) {
    	 String me = LoginController.requireCustomer(session);

    	    Loan result = loanMapper.findById(loanId, me);

    	    if (result == null) {
    	        throw new ResponseStatusException(
    	            HttpStatus.NOT_FOUND
    	        );
    	    }

    	    return result;
    }

    @PostMapping("/loans/{loanId}/repay")
    public Map<String, Object> repay(
            @PathVariable long loanId,
            @RequestParam long amount,
            HttpSession session) {

        String me = LoginController.requireCustomer(session);

        if (loanId <= 0 || amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "잘못된 입력값입니다."
            );
        }

        return Map.of("success", loanService.repay(loanId, me, amount));
    }
    @GetMapping("/interest")
    public Map<String, Object> interest(
            @RequestParam long principal,
            @RequestParam String rate) {

        if (principal <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "원금은 0보다 커야 합니다."
            );
        }

        return Map.of("monthlyInterest",
                loanService.monthlyInterest(principal, rate));
    }
}
