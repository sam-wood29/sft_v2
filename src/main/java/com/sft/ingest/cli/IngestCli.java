package com.sft.ingest.cli;

import com.plaid.client.request.PlaidApi;
import com.sft.data.DbConfig;
import com.sft.ingest.PlaidClientFactory;
import com.sft.ingest.check.CheckRunner;
import com.sft.ingest.config.PlaidConfig;
import com.sft.ingest.link.LinkServer;
import com.sft.ingest.schema.SchemaInitializer;
import com.sft.ingest.sync.SyncOrchestrator;
import java.nio.file.Path;

/** Plain dispatcher on args[0] - no CLI parsing library for 4 bare subcommands. */
public final class IngestCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
            return;
        }
        Path dbPath = DbConfig.resolveDbPath();
        try {
            switch (args[0]) {
                case "init-db" -> {
                    SchemaInitializer.initSchema(dbPath);
                    System.out.println("db initialized");
                }
                case "check" -> System.exit(CheckRunner.run(dbPath));
                case "sync" -> SyncOrchestrator.syncAll(dbPath, PlaidConfig.load());
                case "link" -> {
                    PlaidConfig config = PlaidConfig.load();
                    PlaidApi client = PlaidClientFactory.create(config);
                    LinkServer.runInteractive(config, client, dbPath);
                }
                default -> {
                    usage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("[ingest] " + args[0] + " failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println("usage: IngestCli <init-db|sync|check|link>");
    }
}
