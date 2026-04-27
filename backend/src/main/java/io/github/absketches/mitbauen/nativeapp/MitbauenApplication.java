package io.github.absketches.mitbauen.nativeapp;

import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseService;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import io.github.absketches.mitbauen.nativeapp.shell.AppShellService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

public class MitbauenApplication {

    private MitbauenApplication() {
    }

    public static void main(final String[] ignoredArgs) {
        final DatabaseRuntime databaseRuntime = new DatabaseRuntime();
        final Nano nano = new Nano(
                new HttpServer(),
                new HttpClient(),
                new DatabaseService(databaseRuntime),
                new AppShellService(),
                new ProjectFeedService(databaseRuntime),
                new AuthService(databaseRuntime)
        );
    }
}
