package com.jasonwidjaja.dvp.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.api.ErrorResponse;
import com.jasonwidjaja.dvp.api.TradeResponse;
import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.domain.CaptureCommand;
import com.jasonwidjaja.dvp.domain.CaptureRequestIdentity;
import com.jasonwidjaja.dvp.domain.CommandResult;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AssetRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.ParticipantRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CaptureTradeService {

    private final TransactionTemplate transactionTemplate;
    private final CommandResultRepository commandResults;
    private final ParticipantRepository participants;
    private final AssetRepository assets;
    private final TradeRepository trades;
    private final JsonMapper jsonMapper;

    public CaptureTradeService(
            PlatformTransactionManager transactionManager,
            CommandResultRepository commandResults,
            ParticipantRepository participants,
            AssetRepository assets,
            TradeRepository trades
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.commandResults = commandResults;
        this.participants = participants;
        this.assets = assets;
        this.trades = trades;
        this.jsonMapper = JsonMapper.builder().build();
    }

    public CommandOutcome capture(CaptureCommand command) {
        return transactionTemplate.execute(status -> captureInTransaction(command));
    }

    PlatformTransactionManager transactionManager() {
        return transactionTemplate.getTransactionManager();
    }

    private CommandOutcome captureInTransaction(CaptureCommand command) {
        String requestIdentity = CaptureRequestIdentity.of(command.terms());
        boolean claimed = commandResults.claim(command.idempotencyKey(), requestIdentity);
        if (!claimed) {
            return existingCommandOutcome(command.idempotencyKey(), requestIdentity);
        }

        Optional<ErrorResponse> rejection = validateBusiness(command.terms());
        if (rejection.isPresent()) {
            return finalizeError(command.idempotencyKey(), 422, rejection.get(), null);
        }

        Optional<Trade> inserted = trades.insertIfAbsent(command.terms());
        if (inserted.isPresent()) {
            return finalizeTrade(command.idempotencyKey(), 201, inserted.get());
        }

        Trade existing = trades.findByExternalTradeId(command.terms().externalTradeId()).orElseThrow();
        if (existing.terms().equals(command.terms())) {
            return finalizeTrade(command.idempotencyKey(), 200, existing);
        }
        return finalizeError(
                command.idempotencyKey(),
                409,
                new ErrorResponse("TRADE_REFERENCE_CONFLICT", "A trade with this external reference already exists with different terms"),
                null);
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

    private Optional<ErrorResponse> validateBusiness(TradeTerms terms) {
        if (terms.buyerId().equals(terms.sellerId())) {
            return Optional.of(new ErrorResponse("SELF_TRADE", "Buyer and seller must be different"));
        }
        if (!participants.existsById(terms.buyerId())) {
            return Optional.of(new ErrorResponse("UNKNOWN_PARTICIPANT", "Buyer does not exist"));
        }
        if (!participants.existsById(terms.sellerId())) {
            return Optional.of(new ErrorResponse("UNKNOWN_PARTICIPANT", "Seller does not exist"));
        }
        Optional<Asset> security = assets.findById(terms.securityId());
        if (security.isEmpty()) {
            return Optional.of(new ErrorResponse("UNKNOWN_SECURITY", "Security does not exist"));
        }
        if (security.get().type() != AssetType.SECURITY) {
            return Optional.of(new ErrorResponse("INVALID_SECURITY", "Asset is not a SECURITY"));
        }
        return Optional.empty();
    }

    private CommandOutcome finalizeTrade(String commandKey, int httpStatus, Trade trade) {
        String body = json(TradeResponse.from(trade));
        String location = "/v1/trades/" + trade.id();
        commandResults.finalize(commandKey, httpStatus, body, location);
        return new CommandOutcome(httpStatus, body, location);
    }

    private CommandOutcome finalizeError(
            String commandKey,
            int httpStatus,
            ErrorResponse error,
            String location
    ) {
        String body = json(error);
        commandResults.finalize(commandKey, httpStatus, body, location);
        return new CommandOutcome(httpStatus, body, location);
    }

    private String json(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize command result", ex);
        }
    }
}
