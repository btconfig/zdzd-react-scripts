package com.example.agent.interfaces.rest;

import com.example.agent.application.service.CustomerSessionQueryService;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryRequest;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-sessions")
public class CustomerSessionController {
    private final CustomerSessionQueryService customerSessionQueryService;

    public CustomerSessionController(CustomerSessionQueryService customerSessionQueryService) {
        this.customerSessionQueryService = customerSessionQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<CustomerSessionQueryResponse> query(@RequestBody CustomerSessionQueryRequest request) {
        return ResponseEntity.ok(customerSessionQueryService.query(request));
    }
}
