package io.github.absketches.mitbauen.nativeapp;

import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseBootstrapService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.MigrationService;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import io.github.absketches.mitbauen.nativeapp.shell.AppShellService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

public class MitbauenApplication {

    private MitbauenApplication() {
    }

    public static void main(final String[] args) {
        final String mode = args.length == 0 ? "serve" : args[0];
        switch (mode) {
            case "serve" -> serve();
            case "migrate" -> migrate();
            default -> throw new IllegalArgumentException("Unsupported mode [" + mode + "]. Use serve or migrate.");
        }
    }

    private static void serve() {
        final DatabaseRuntime databaseRuntime = new DatabaseRuntime();
        new Nano(
            new HttpServer(),
            new HttpClient(),
            new DatabaseBootstrapService(databaseRuntime),
            new AppShellService(),
            new ProjectFeedService(databaseRuntime),
            new AuthService(databaseRuntime)
        );
    }

    private static void migrate() {
        final DatabaseRuntime databaseRuntime = new DatabaseRuntime("mitbauen-migrate");
        final MigrationService migrationService = new MigrationService(databaseRuntime);
        new Nano(
            new DatabaseBootstrapService(databaseRuntime),
            migrationService
        ).waitForStop();
        if (migrationService.failure() != null) {
            throw new IllegalStateException("Database migration failed", migrationService.failure());
        }
    }
}
