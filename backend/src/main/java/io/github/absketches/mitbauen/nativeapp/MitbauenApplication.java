package io.github.absketches.mitbauen.nativeapp;

import berlin.yuna.typemap.model.FunctionOrNull;
import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.comments.ProjectCommentsService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseConfig;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.MigrationRunner;
import io.github.absketches.mitbauen.nativeapp.jobs.JobsService;
import io.github.absketches.mitbauen.nativeapp.notifications.NotificationsService;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectDescriptionTranslationBackfill;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectDescriptionTranslationWarmService;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import io.github.absketches.mitbauen.nativeapp.shell.AppShellService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

import java.util.List;

public class MitbauenApplication {

    public static void main(final String[] args) {
        if (args.length == 1 && "backfill-project-description-translations".equals(args[0])) {
            ProjectDescriptionTranslationBackfill.run();
            return;
        }

        final DatabaseRuntime databaseRuntime = dbStartup();
        final FunctionOrNull<Context, List<Service>> startupServices = context -> services(context, databaseRuntime);
        final Nano nano = new Nano(startupServices, null);
    }

    private static List<Service> services(final Context context, final DatabaseRuntime databaseRuntime) {
        context.subscribeEvent(Context.EVENT_APP_SHUTDOWN, event -> databaseRuntime.stop());

        return List.of(
            new HttpServer(),
            new HttpClient(),
            new AppShellService(),
            new ProjectFeedService(databaseRuntime),
            new ProjectDescriptionTranslationWarmService(databaseRuntime),
            new JobsService(databaseRuntime),
            new ProjectCommentsService(databaseRuntime),
            new NotificationsService(databaseRuntime),
            new AuthService(databaseRuntime)
        );
    }

    public static DatabaseRuntime dbStartup() {
        DatabaseRuntime databaseRuntime = null;
        try {
            final DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
            databaseRuntime = new DatabaseRuntime(
                    databaseConfig.jdbcUrl(),
                    databaseConfig.jdbcUser(),
                    databaseConfig.jdbcPassword()
            );
            new MigrationRunner().migrate(databaseRuntime.dataSource());
        } catch (RuntimeException | Error throwable) {
            if (databaseRuntime != null) {
                databaseRuntime.stop();
            }
            throw throwable;
        }
        return databaseRuntime;
    }
}
