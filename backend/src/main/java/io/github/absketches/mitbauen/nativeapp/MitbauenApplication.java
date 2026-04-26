package io.github.absketches.mitbauen.nativeapp;

import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

public class MitbauenApplication {

    private MitbauenApplication() {
    }

    public static void main(final String[] args) {
        new Nano(new HttpServer(), new HttpClient(), new ProjectFeedService());
    }
}
