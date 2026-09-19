package com.jasonwidjaja.dvp.api;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

final class JsonMapping {

    private JsonMapping() {
    }

    static JsonMapper.Builder applyStrictIntegerRules(JsonMapper.Builder builder) {
        return builder
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .withCoercionConfig(LogicalType.Integer, cfg -> {
                    cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    cfg.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                });
    }
}
