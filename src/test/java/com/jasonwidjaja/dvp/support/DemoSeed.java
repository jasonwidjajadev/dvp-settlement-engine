package com.jasonwidjaja.dvp.support;

import java.nio.file.Path;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class DemoSeed {

    public static final Path SCRIPT = Path.of(System.getProperty("user.dir"), "scripts", "seed-demo.sql");

    public static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID AUD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID EQ1_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    public static final UUID ALICE_AUD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    public static final UUID ALICE_EQ1_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ae");
    public static final UUID BOB_AUD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ba");
    public static final UUID BOB_EQ1_ID = UUID.fromString("00000000-0000-0000-0000-0000000000be");
    public static final UUID UNKNOWN_ACCOUNT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    private DemoSeed() {
    }

    public static void apply(DataSource dataSource) {
        Resource resource = new FileSystemResource(SCRIPT);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing seed script: " + SCRIPT.toAbsolutePath());
        }
        new ResourceDatabasePopulator(resource).execute(dataSource);
    }
}
