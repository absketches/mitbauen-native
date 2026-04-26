package io.github.absketches.mitbauen.nativeapp;

import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseBootstrapService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

public class MitbauenApplication {

    private MitbauenApplication() {
    }

    public static void main(final String[] args) {
        final DatabaseRuntime databaseRuntime = new DatabaseRuntime();
        new Nano(
            new HttpServer(),
            new HttpClient(),
            new DatabaseBootstrapService(databaseRuntime),
            new ProjectFeedService(databaseRuntime),
            new AuthService(databaseRuntime)
        );
    }
}
