package com.jasonwidjaja.dvp.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.api.ErrorResponse;
import com.jasonwidjaja.dvp.api.SettlementResponse;
import com.jasonwidjaja.dvp.api.UnknownTradeException;
import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.domain.CommandResult;
import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.SettleRequestIdentity;
import com.jasonwidjaja.dvp.domain.SettlementJournal;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.AssetRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.LockedAccount;
import com.jasonwidjaja.dvp.persistence.SettlementAttemptRepository;
import com.jasonwidjaja.dvp.persistence.SettlementJournalRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SettleTradeService {

    private static final String CASH_ASSET_CODE = "AUD";

    private final TransactionTemplate transactionTemplate;
    private final CommandResultRepository commandResults;
    private final TradeRepository trades;
    private final AccountRepository accounts;
    private final AssetRepository assets;
    private final SettlementJournalRepository journals;
    private final SettlementAttemptRepository attempts;
    private final BusinessCalendar calendar;
    private final JsonMapper jsonMapper;

    public SettleTradeService(
            PlatformTransactionManager transactionManager,
            CommandResultRepository commandResults,
            TradeRepository trades,
            AccountRepository accounts,
            AssetRepository assets,
            SettlementJournalRepository journals,
            SettlementAttemptRepository attempts,
            BusinessCalendar calendar
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.commandResults = commandResults;
        this.trades = trades;
        this.accounts = accounts;
        this.assets = assets;
        this.journals = journals;
        this.attempts = attempts;
        this.calendar = calendar;
        this.jsonMapper = JsonMapper.builder().build();
    }

    public CommandOutcome settle(SettleCommand command) {
        return transactionTemplate.execute(status -> settleInTransaction(command));
    }

    PlatformTransactionManager transactionManager() {
        return transactionTemplate.getTransactionManager();
    }

    private CommandOutcome settleInTransaction(SettleCommand command) {
        String requestIdentity = SettleRequestIdentity.of(command.tradeId());
        boolean claimed = commandResults.claim(
                command.idempotencyKey(),
                SettleRequestIdentity.OPERATION,
                requestIdentity);
        if (!claimed) {
            return existingCommandOutcome(command.idempotencyKey(), requestIdentity);
        }

        Trade trade = trades.lockById(command.tradeId()).orElseThrow(UnknownTradeException::new);
        LocalDate businessDate = calendar.businessDate();

        if (trade.status() == TradeStatus.SETTLED) {
            return reject(
                    command.idempotencyKey(),
                    trade.id(),
                    SettlementOutcome.ALREADY_SETTLED,
                    trade.journalId(),
                    businessDate,
                    409,
                    "ALREADY_SETTLED",
                    "Trade is already settled");
        }

        if (trade.terms().settlementDate().isAfter(businessDate)) {
            return reject(
                    command.idempotencyKey(),
                    trade.id(),
                    SettlementOutcome.NOT_DUE,
                    null,
                    businessDate,
                    422,
                    "NOT_DUE",
                    "Trade is not due");
        }

        AffectedAccounts affected = resolveAffectedAccounts(trade);
        Map<UUID, LockedAccount> locked = lockAccounts(affected);
        LockedAccount buyerCash = locked.get(affected.buyerCashId());
        LockedAccount sellerSecurity = locked.get(affected.sellerSecurityId());

        if (buyerCash.currentBalance() < trade.terms().cashAmount()) {
            return reject(
                    command.idempotencyKey(),
                    trade.id(),
                    SettlementOutcome.INSUFFICIENT_CASH,
                    null,
                    businessDate,
                    422,
                    "INSUFFICIENT_CASH",
                    "Buyer cash is insufficient");
        }
        if (sellerSecurity.currentBalance() < trade.terms().quantity()) {
            return reject(
                    command.idempotencyKey(),
                    trade.id(),
                    SettlementOutcome.INSUFFICIENT_SECURITIES,
                    null,
                    businessDate,
                    422,
                    "INSUFFICIENT_SECURITIES",
                    "Seller securities are insufficient");
        }

        return writeSettlement(command.idempotencyKey(), trade, affected, businessDate);
    }

    private CommandOutcome writeSettlement(
            String commandKey,
            Trade trade,
            AffectedAccounts affected,
            LocalDate businessDate
    ) {
        SettlementJournal journal = journals.insertJournal(trade.id());
        journals.insertPostings(journal.id(), List.of(
                posting(affected.buyerCashId(), PostingDirection.DEBIT, trade.terms().cashAmount()),
                posting(affected.sellerCashId(), PostingDirection.CREDIT, trade.terms().cashAmount()),
                posting(affected.buyerSecurityId(), PostingDirection.CREDIT, trade.terms().quantity()),
                posting(affected.sellerSecurityId(), PostingDirection.DEBIT, trade.terms().quantity())));
        applyDeltas(affected, trade);
        trades.markSettled(trade.id(), journal.id());
        attempts.insert(
                trade.id(),
                commandKey,
                SettlementOutcome.SETTLED,
                journal.id(),
                businessDate);
        String body = json(new SettlementResponse(
                trade.id(),
                TradeStatus.SETTLED,
                SettlementOutcome.SETTLED,
                journal.id(),
                journal.settledAt()));
        String location = "/v1/journals/" + journal.id();
        commandResults.finalize(commandKey, 201, body, location);
        return new CommandOutcome(201, body, location);
    }

    private void applyDeltas(AffectedAccounts affected, Trade trade) {
        Map<UUID, Long> deltas = Map.of(
                affected.buyerCashId(), -trade.terms().cashAmount(),
                affected.sellerCashId(), trade.terms().cashAmount(),
                affected.buyerSecurityId(), trade.terms().quantity(),
                affected.sellerSecurityId(), -trade.terms().quantity());
        for (UUID accountId : orderedAccountIds(affected)) {
            accounts.applyDelta(accountId, deltas.get(accountId));
        }
    }

    private CommandOutcome existingCommandOutcome(String commandKey, String requestIdentity) {
        CommandResult existing = commandResults.findByCommandKey(commandKey).orElseThrow();
        if (!existing.hasRequestIdentity(requestIdentity)) {
            return new CommandOutcome(
                    409,
                    json(new ErrorResponse(
                            "IDEMPOTENCY_KEY_CONFLICT",
                            "Idempotency key was reused with a different request")),
                    null);
        }
        if (!existing.completed()) {
            throw new IllegalStateException("Unfinished command_result cannot be replayed for key " + commandKey);
        }
        return new CommandOutcome(existing.httpStatus(), existing.responseBody(), existing.location());
    }

    private AffectedAccounts resolveAffectedAccounts(Trade trade) {
        Asset cash = assets.findByCode(CASH_ASSET_CODE)
                .filter(asset -> asset.type() == AssetType.CASH)
                .orElseThrow(() -> new SettlementIntegrityException("AUD cash asset is missing"));
        UUID buyerCashId = requireAccount(trade.terms().buyerId(), cash.id(), "buyer cash");
        UUID sellerCashId = requireAccount(trade.terms().sellerId(), cash.id(), "seller cash");
        UUID buyerSecurityId = requireAccount(trade.terms().buyerId(), trade.terms().securityId(), "buyer security");
        UUID sellerSecurityId = requireAccount(trade.terms().sellerId(), trade.terms().securityId(), "seller security");
        List<UUID> ids = List.of(buyerCashId, sellerCashId, buyerSecurityId, sellerSecurityId);
        if (new HashSet<>(ids).size() != 4) {
            throw new SettlementIntegrityException("Settlement accounts must be distinct");
        }
        return new AffectedAccounts(buyerCashId, sellerCashId, buyerSecurityId, sellerSecurityId);
    }

    private UUID requireAccount(UUID participantId, UUID assetId, String role) {
        return accounts.findIdByParticipantAndAsset(participantId, assetId)
                .orElseThrow(() -> new SettlementIntegrityException("Required " + role + " account is missing"));
    }

    private Map<UUID, LockedAccount> lockAccounts(AffectedAccounts affected) {
        Map<UUID, LockedAccount> locked = new HashMap<>();
        for (UUID accountId : orderedAccountIds(affected)) {
            LockedAccount row = accounts.lockBalance(accountId)
                    .orElseThrow(() -> new SettlementIntegrityException(
                            "Required settlement account disappeared: " + accountId));
            locked.put(accountId, row);
        }
        return locked;
    }

    private static List<UUID> orderedAccountIds(AffectedAccounts affected) {
        List<UUID> ordered = new ArrayList<>(affected.ids());
        ordered.sort(UUID::compareTo);
        return ordered;
    }

    private static Posting posting(UUID accountId, PostingDirection direction, long amount) {
        long signedAmount = direction == PostingDirection.DEBIT ? -amount : amount;
        return new Posting(UUID.randomUUID(), UUID.randomUUID(), accountId, direction, amount, signedAmount);
    }

    private CommandOutcome reject(
            String commandKey,
            UUID tradeId,
            SettlementOutcome outcome,
            UUID journalId,
            LocalDate businessDate,
            int httpStatus,
            String code,
            String message
    ) {
        attempts.insert(tradeId, commandKey, outcome, journalId, businessDate);
        return finalizeError(commandKey, httpStatus, new ErrorResponse(code, message));
    }

    private CommandOutcome finalizeError(String commandKey, int httpStatus, ErrorResponse error) {
        String body = json(error);
        commandResults.finalize(commandKey, httpStatus, body, null);
        return new CommandOutcome(httpStatus, body, null);
    }

    private String json(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize command result", ex);
        }
    }

    private record AffectedAccounts(
            UUID buyerCashId,
            UUID sellerCashId,
            UUID buyerSecurityId,
            UUID sellerSecurityId
    ) {
        List<UUID> ids() {
            return List.of(buyerCashId, sellerCashId, buyerSecurityId, sellerSecurityId);
        }
    }
}
