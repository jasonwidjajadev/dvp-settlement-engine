package com.jasonwidjaja.dvp.domain;

import java.util.UUID;

public record Asset(UUID id, String code, AssetType type) {
}
