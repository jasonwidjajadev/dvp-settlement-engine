package com.jasonwidjaja.dvp.api;

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
import com.jasonwidjaja.dvp.domain.CaptureCommand;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.TradeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/trades")
public class TradeController {

    private final CaptureTradeService capture;
    private final TradeRepository trades;

    public TradeController(CaptureTradeService capture, TradeRepository trades) {
        this.capture = capture;
        this.trades = trades;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> capture(
            @RequestHeader(name = "Idempotency-Key", required = false) List<String> idempotencyKeys,
            @Valid @RequestBody CaptureTradeRequest request
    ) {
        CaptureCommand command = new CaptureCommand(IdempotencyKey.requireExactlyOne(idempotencyKeys), termsOf(request));
        return toResponse(capture.capture(command));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeResponse get(@PathVariable UUID id) {
        return trades.findById(id)
                .map(TradeResponse::from)
                .orElseThrow(UnknownTradeException::new);
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

    private static ResponseEntity<byte[]> toResponse(CommandOutcome outcome) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.httpStatus())
                .contentType(MediaType.APPLICATION_JSON);
        if (outcome.location() != null) {
            builder = builder.location(URI.create(outcome.location()));
        }
        return builder.body(outcome.responseBody().getBytes(StandardCharsets.UTF_8));
    }
}
