package com.jasonwidjaja.dvp.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;

@Repository
public class AssetRepository {

    private static final String FIND_BY_ID_SQL = """
            SELECT id, code, type
            FROM asset
            WHERE id = :assetId
            """;

    private static final String FIND_BY_CODE_SQL = """
            SELECT id, code, type
            FROM asset
            WHERE code = :code
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AssetRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Asset> findById(UUID assetId) {
        List<Asset> assets = jdbc.query(
                FIND_BY_ID_SQL,
                Map.of("assetId", assetId),
                AssetRepository::mapAsset);
        return assets.stream().findFirst();
    }

    public Optional<Asset> findByCode(String code) {
        List<Asset> assets = jdbc.query(
                FIND_BY_CODE_SQL,
                Map.of("code", code),
                AssetRepository::mapAsset);
        return assets.stream().findFirst();
    }

    private static Asset mapAsset(ResultSet rs, int rowNum) throws SQLException {
        return new Asset(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                AssetType.valueOf(rs.getString("type")));
    }
}
