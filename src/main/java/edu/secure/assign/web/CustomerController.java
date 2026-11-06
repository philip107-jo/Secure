package edu.secure.assign.web;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.mapper.CustomerMapper;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerMapper customerMapper;

    public CustomerController(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @GetMapping("/customers/{customerId}")
    public CustomerResponse customer(
            @PathVariable String customerId,
            HttpSession session) {

        String me = LoginController.requireCustomer(session);

        // 다른 고객의 정보 조회 차단
        if (!me.equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Customer c = customerMapper.findById(me);

        log.info("고객 조회 요청: {}", me);

        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // 필요한 정보만 DTO에 담아 반환
        return new CustomerResponse(
            c.getCustomerId(),
            c.getCustomerName()
        );
    }
}