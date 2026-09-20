package com.jasonwidjaja.dvp.api;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jasonwidjaja.dvp.application.CaptureTradeService;
import com.jasonwidjaja.dvp.application.CommandOutcome;
import com.jasonwidjaja.dvp.application.SettleTradeService;
import com.jasonwidjaja.dvp.domain.CaptureCommand;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.SettlementAttemptRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/trades")
public class TradeController {

    private final CaptureTradeService capture;
    private final SettleTradeService settle;
    private final TradeRepository trades;
    private final SettlementAttemptRepository attempts;

    public TradeController(
            CaptureTradeService capture,
            SettleTradeService settle,
            TradeRepository trades,
            SettlementAttemptRepository attempts
    ) {
        this.capture = capture;
        this.settle = settle;
        this.trades = trades;
        this.attempts = attempts;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> capture(
            @RequestHeader(name = "Idempotency-Key", required = false) List<String> idempotencyKeys,
            @Valid @RequestBody CaptureTradeRequest request
    ) {
        CaptureCommand command = new CaptureCommand(IdempotencyKey.requireExactlyOne(idempotencyKeys), termsOf(request));
        return toResponse(capture.capture(command));
    }

    @PostMapping(path = "/{id}/settle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> settle(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) List<String> idempotencyKeys,
            HttpServletRequest request
    ) {
        rejectNonEmptyBody(request);
        SettleCommand command = new SettleCommand(IdempotencyKey.requireExactlyOne(idempotencyKeys), id);
        return toResponse(settle.settle(command));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeResponse get(@PathVariable UUID id) {
        return trades.findById(id)
                .map(TradeResponse::from)
                .orElseThrow(UnknownTradeException::new);
    }

    @GetMapping(path = "/{id}/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SettlementAttemptResponse> attempts(@PathVariable UUID id) {
        if (trades.findById(id).isEmpty()) {
            throw new UnknownTradeException();
        }
        return attempts.findByTradeId(id).stream()
                .map(SettlementAttemptResponse::from)
                .toList();
    }

    private static TradeTerms termsOf(CaptureTradeRequest request) {
        return new TradeTerms(
                request.externalTradeId(),
                request.buyerId(),
                request.sellerId(),
                request.securityId(),
                request.quantity(),
                request.cashAmount(),
                request.settlementDate());
    }

    private static void rejectNonEmptyBody(HttpServletRequest request) {
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read request body", ex);
        }
        if (body.length > 0) {
            throw new NonEmptyRequestBodyException();
        }
    }

    private static ResponseEntity<byte[]> toResponse(CommandOutcome outcome) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.httpStatus())
                .contentType(MediaType.APPLICATION_JSON);
        if (outcome.location() != null) {
            builder = builder.location(URI.create(outcome.location()));
        }
        return builder.body(outcome.responseBody().getBytes(StandardCharsets.UTF_8));
    }
}
