package io.github.absketches.mitbauen.nativeapp;

import berlin.yuna.typemap.model.FunctionOrNull;
import io.github.absketches.mitbauen.nativeapp.auth.service.AuthService;
import io.github.absketches.mitbauen.nativeapp.comments.service.ProjectCommentsService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseConfig;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.MigrationRunner;
import io.github.absketches.mitbauen.nativeapp.email.ResendConfig;
import io.github.absketches.mitbauen.nativeapp.email.ResendTransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.email.TransactionalEmailService;
import io.github.absketches.mitbauen.nativeapp.email.TransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.jobs.service.JobsService;
import io.github.absketches.mitbauen.nativeapp.notifications.service.NotificationsService;
import io.github.absketches.mitbauen.nativeapp.projects.translation.ProjectDescriptionTranslationWarmService;
import io.github.absketches.mitbauen.nativeapp.projects.service.ProjectFeedService;
import io.github.absketches.mitbauen.nativeapp.shell.AppShellService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

import java.util.List;

public class MitbauenApplication {

    public static void main(final String[] args) {
        final DatabaseRuntime databaseRuntime = dbStartup();
        final TransactionalEmailSender emailSender = resendEmailProvider();
        final FunctionOrNull<Context, List<Service>> startupServices = context -> services(context, databaseRuntime, emailSender);
        final Nano nano = new Nano(startupServices, null);
    }

    private static List<Service> services(
        final Context context,
        final DatabaseRuntime databaseRuntime,
        final TransactionalEmailSender emailSender
    ) {
        context.subscribeEvent(Context.EVENT_APP_SHUTDOWN, event -> databaseRuntime.stop());

        return List.of(
            new HttpServer(),
            new HttpClient(),
            new TransactionalEmailService(emailSender),
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

    public static TransactionalEmailSender resendEmailProvider() {
        final ResendConfig resendConfig = ResendConfig.fromEnvironment();
        return new ResendTransactionalEmailSender(resendConfig.apiKey(), resendConfig.emailFrom());
    }
}
