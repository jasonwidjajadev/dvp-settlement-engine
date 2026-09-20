package com.jasonwidjaja.dvp.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.SettlementJournal;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.SettlementJournalRepository;

@RestController
@RequestMapping("/v1/journals")
public class JournalController {

    private final SettlementJournalRepository journals;
    private final AccountRepository accounts;

    public JournalController(SettlementJournalRepository journals, AccountRepository accounts) {
        this.journals = journals;
        this.accounts = accounts;
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JournalResponse get(@PathVariable UUID id) {
        SettlementJournal journal = journals.findJournalById(id).orElseThrow(UnknownJournalException::new);
        return new JournalResponse(
                journal.id(),
                journal.tradeId(),
                journal.settledAt(),
                journals.findPostingsByJournalId(id).stream()
                        .map(this::toPosting)
                        .toList());
    }

    private JournalResponse.PostingResponse toPosting(Posting posting) {
        Account account = accounts.findById(posting.accountId())
                .orElseThrow(() -> new IllegalStateException("Posting account does not exist: " + posting.accountId()));
        return new JournalResponse.PostingResponse(
                posting.id(),
                posting.accountId(),
                new AccountResponse.ParticipantResponse(account.participant().id(), account.participant().name()),
                new AccountResponse.AssetResponse(account.asset().id(), account.asset().code(), account.asset().type()),
                posting.direction(),
                posting.amount());
    }
}
