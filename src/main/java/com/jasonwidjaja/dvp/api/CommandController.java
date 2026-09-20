package com.jasonwidjaja.dvp.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jasonwidjaja.dvp.domain.CommandResult;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/v1/commands")
public class CommandController {

    private final CommandResultRepository commandResults;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public CommandController(CommandResultRepository commandResults) {
        this.commandResults = commandResults;
    }

    @GetMapping(path = "/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CommandResultResponse get(@PathVariable String key) {
        IdempotencyKey.requireExactlyOne(List.of(key));
        CommandResult result = commandResults.findByCommandKey(key)
                .filter(CommandResult::completed)
                .orElseThrow(UnknownCommandException::new);
        return new CommandResultResponse(
                result.commandKey(),
                result.operation(),
                result.httpStatus(),
                result.location(),
                parsedResponse(result.responseBody()));
    }

    private JsonNode parsedResponse(String responseBody) {
        try {
            return jsonMapper.readTree(responseBody);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Stored command response is not JSON", ex);
        }
    }
}
