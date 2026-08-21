package io.github.absketches.mitbauen.nativeapp;

import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.auth.EmailVerificationSettings;
import io.github.absketches.mitbauen.nativeapp.auth.ResendTransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.auth.TransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.comments.ProjectCommentsService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseConfig;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.MigrationRunner;
import io.github.absketches.mitbauen.nativeapp.jobs.JobsService;
import io.github.absketches.mitbauen.nativeapp.notifications.NotificationsService;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import io.github.absketches.mitbauen.nativeapp.shell.AppShellService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.core.model.Context;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;

public class MitbauenApplication {

    public static void main(final String[] ignoredArgs) {
        final DatabaseRuntime databaseRuntime = dbStartup();
        final EmailVerificationSettings emailVerificationSettings = EmailVerificationSettings.fromEnvironment();
        final TransactionalEmailSender transactionalEmailSender = new ResendTransactionalEmailSender(
            emailVerificationSettings.resendApiKey(),
            emailVerificationSettings.emailFrom()
        );
        final Nano nano = new Nano(
                new HttpServer(),
                new HttpClient(),
                new AppShellService(),
                new ProjectFeedService(databaseRuntime),
                new JobsService(databaseRuntime, transactionalEmailSender),
                new ProjectCommentsService(databaseRuntime),
                new NotificationsService(databaseRuntime),
                new AuthService(databaseRuntime, emailVerificationSettings, transactionalEmailSender)
        );
        nano.subscribeEvent(Context.EVENT_APP_SHUTDOWN, event -> databaseRuntime.stop());
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
