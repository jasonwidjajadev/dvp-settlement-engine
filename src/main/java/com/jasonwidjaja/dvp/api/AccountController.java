package com.jasonwidjaja.dvp.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jasonwidjaja.dvp.persistence.AccountRepository;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AccountResponse> list() {
        return accounts.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }
}
