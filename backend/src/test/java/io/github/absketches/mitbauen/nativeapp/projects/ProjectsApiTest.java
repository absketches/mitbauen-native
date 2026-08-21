package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.EmailVerificationSettings;
import io.github.absketches.mitbauen.nativeapp.auth.TransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.PostgresTestDatabase;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectsApiTest {

    private static final String OPEN_INVITE = "test-open-invite";
    private static final String PRIMARY_PASSWORD = "SuperSafe1";
    private static final EmailVerificationSettings EMAIL_VERIFICATION_SETTINGS =
        new EmailVerificationSettings("https://www.mitbauen.space", "Mitbauen <no-reply@mail.mitbauen.space>", "");
    private static final TransactionalEmailSender NOOP_TRANSACTIONAL_EMAIL_SENDER =
        (recipientEmail, recipientName, verificationUrl) -> { };

    private Nano nano;
    private DatabaseRuntime databaseRuntime;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(ProjectsApiTest.class).waitForStop();
            nano = null;
        }
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void createsProjectLoadsDetailsAndShowsItFirstInFeed() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.one@example.test", "Avery Builder");

        final HttpObject createResponse = sendJson("/api/projects", "POST", Map.of(
            "title", "Circular Kitchen Atlas",
            "descriptions", Map.of("en", "A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity, and the fastest path from extra ingredients to community meals."),
            "founderRole", "Founder + Community Ops",
            "founderCommitment", "I am running pilot dinners every week, documenting learnings, and handling the volunteer operations myself.",
            "openRoles", List.of(
                Map.of("title", "Frontend Engineer", "commitment", "Build the first contributor-facing workflows."),
                Map.of("title", "Research Partner", "commitment", "Interview hosts and turn patterns into playbooks.")
            ),
            "links", List.of(
                Map.of("label", "Website", "url", "https://example.test/kitchen"),
                Map.of("label", "Mastodon", "url", "https://social.example.test/@kitchen")
            )
        ), sessionCookie);

        assertThat(createResponse.statusCode()).isEqualTo(201);
        final String slug = createResponse.bodyAsMap().asString("slug");
        assertThat(slug).isEqualTo("circular-kitchen-atlas");

        final byte[] imageBytes = pngBytes();
        final HttpObject imageResponse = sendImage("/api/projects/" + slug + "/images", "image/png", imageBytes, sessionCookie);
        assertThat(imageResponse.statusCode()).isEqualTo(201);
        assertThat(imageResponse.bodyAsMap().asMap("image").asString("url")).contains("/api/projects/" + slug + "/images/");

        final HttpObject detailResponse = sendGet("/api/projects/" + slug, sessionCookie);
        assertThat(detailResponse.statusCode()).isEqualTo(200);
        final LinkedTypeMap project = detailResponse.bodyAsMap().asMap("project");
        assertThat(project.asString("title")).isEqualTo("Circular Kitchen Atlas");
        assertThat(project.asMap("descriptions").asString("en")).contains("surplus food");
        assertThat(project.asBoolean("canManage")).isTrue();
        assertThat(project.asMap("founder").asString("publicId")).isNotBlank();
        assertThat(project.asMap("founder").asString("role")).isEqualTo("Founder + Community Ops");
        assertThat(project.asMap("founder").asString("commitment")).contains("pilot dinners every week");
        final TypeList images = project.asList("images");
        assertThat(images).hasSize(1);
        final LinkedTypeMap image = new LinkedTypeMap((Map<?, ?>) images.get(0));
        assertThat(image.asString("contentType")).isEqualTo("image/png");
        assertThat(image.asInt("sizeBytes")).isEqualTo(imageBytes.length);
        assertThat(sendGet(image.asString("url"), sessionCookie).body()).containsExactly(imageBytes);

        final TypeList roles = project.asList("openRoles");
        assertThat(roles).hasSize(2);
        assertThat(roles.stream()
            .map(role -> new LinkedTypeMap((Map<?, ?>) role).asString("title"))
            .toList())
            .containsExactly("Frontend Engineer", "Research Partner");
        assertThat(project.asList("links").stream()
            .map(link -> new LinkedTypeMap((Map<?, ?>) link).asString("label"))
            .toList())
            .containsExactly("Website", "Mastodon");

        final HttpObject feedResponse = sendGet("/api/projects", sessionCookie);
        assertThat(feedResponse.statusCode()).isEqualTo(200);
        final TypeList feedProjects = feedResponse.bodyAsMap().asList("projects");
        final LinkedTypeMap firstProject = new LinkedTypeMap((Map<?, ?>) feedProjects.get(0));
        assertThat(firstProject.asString("slug")).isEqualTo(slug);
        assertThat(firstProject.asMap("founder").asString("publicId")).isNotBlank();
        assertThat(firstProject.asList("links")).hasSize(2);
        assertThat(firstProject.asList("images")).hasSize(1);
    }

    @Test
    void projectFeedAndDetailsRequireRegistration() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.members.only@example.test", "Members Only");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Members Only Project",
            "descriptions", Map.of("en", "A members-only project description with enough detail to satisfy validation and verify anonymous access is blocked."),
            "founderRole", "Founder + Steward",
            "founderCommitment", "I am coordinating the first member handoffs and keeping the project notes current.",
            "openRoles", List.of(Map.of("title", "Project Helper", "commitment", "Help keep the member workflow moving."))
        ), sessionCookie).bodyAsMap().asString("slug");

        final HttpObject anonymousFeed = sendGet("/api/projects", null);
        assertThat(anonymousFeed.statusCode()).isEqualTo(401);
        assertThat(anonymousFeed.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_VIEW_AUTH_REQUIRED_CODE);

        final HttpObject anonymousDetail = sendGet("/api/projects/" + slug, null);
        assertThat(anonymousDetail.statusCode()).isEqualTo(401);
        assertThat(anonymousDetail.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_VIEW_AUTH_REQUIRED_CODE);
    }

    @Test
    void storesLocalizedProjectDescriptionsAsAuthored() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.localized@example.test", "Localized Owner");
        final String englishDescription = "An English-only project description with enough detail to prove the German field remains empty until the creator writes it.";

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Localized Description Project",
            "descriptions", Map.of("en", englishDescription),
            "founderRole", "Founder + Translator",
            "founderCommitment", "I am keeping the project description clear for contributors.",
            "openRoles", List.of(Map.of("title", "Project Helper", "commitment", "Help keep the member workflow moving."))
        ), sessionCookie).bodyAsMap().asString("slug");

        final LinkedTypeMap project = sendGet("/api/projects/" + slug, sessionCookie).bodyAsMap().asMap("project");
        assertThat(project.asMap("descriptions").asString("en")).isEqualTo(englishDescription);
        assertThat(project.asMap("descriptions").asString("de")).isNull();
    }

    @Test
    void unverifiedMembersCannotViewProjectFeedAndDetails() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.visible@example.test", "Visible Owner");
        final String unverifiedCookie = registerAndReturnSessionCookie("viewer.unverified@example.test", "Unverified Viewer", false);

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Unverified Viewer Project",
            "descriptions", Map.of("en", "A private project description with enough detail to confirm pending members cannot browse project details."),
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "I am coordinating the first project steps and making the work visible to members.",
            "openRoles", List.of(Map.of("title", "Project Support", "commitment", "Help with member coordination."))
        ), ownerCookie).bodyAsMap().asString("slug");

        final HttpObject feedResponse = sendGet("/api/projects", unverifiedCookie);
        assertThat(feedResponse.statusCode()).isEqualTo(403);
        assertThat(feedResponse.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_VIEW_EMAIL_UNVERIFIED_CODE);

        final HttpObject detailResponse = sendGet("/api/projects/" + slug, unverifiedCookie);
        assertThat(detailResponse.statusCode()).isEqualTo(403);
        assertThat(detailResponse.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_VIEW_EMAIL_UNVERIFIED_CODE);
    }

    @Test
    void acceptsExpandedProjectFieldLimits() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.expanded@example.test", "Limit Builder");
        final String description = "D".repeat(ProjectFeedUtil.DESCRIPTION_MAX_LENGTH);
        final String founderRole = "F".repeat(ProjectFeedUtil.FOUNDER_ROLE_MAX_LENGTH);
        final String founderCommitment = "C".repeat(ProjectFeedUtil.FOUNDER_COMMITMENT_MAX_LENGTH);
        final String openRoleTitle = "R".repeat(ProjectFeedUtil.OPEN_ROLE_TITLE_MAX_LENGTH);
        final String openRoleCommitment = "O".repeat(ProjectFeedUtil.OPEN_ROLE_COMMITMENT_MAX_LENGTH);

        final HttpObject createResponse = sendJson("/api/projects", "POST", Map.of(
            "title", "Expanded Field Limits",
            "descriptions", Map.of("en", description),
            "founderRole", founderRole,
            "founderCommitment", founderCommitment,
            "openRoles", List.of(Map.of("title", openRoleTitle, "commitment", openRoleCommitment))
        ), sessionCookie);

        assertThat(createResponse.statusCode()).isEqualTo(201);
        final LinkedTypeMap project = sendGet(
            "/api/projects/" + createResponse.bodyAsMap().asString("slug"),
            sessionCookie
        ).bodyAsMap().asMap("project");
        assertThat(project.asMap("descriptions").asString("en")).hasSize(ProjectFeedUtil.DESCRIPTION_MAX_LENGTH);
        assertThat(project.asMap("founder").asString("role")).hasSize(ProjectFeedUtil.FOUNDER_ROLE_MAX_LENGTH);
        assertThat(project.asMap("founder").asString("commitment")).hasSize(ProjectFeedUtil.FOUNDER_COMMITMENT_MAX_LENGTH);

        final LinkedTypeMap openRole = new LinkedTypeMap((Map<?, ?>) project.asList("openRoles").get(0));
        assertThat(openRole.asString("title")).hasSize(ProjectFeedUtil.OPEN_ROLE_TITLE_MAX_LENGTH);
        assertThat(openRole.asString("commitment")).hasSize(ProjectFeedUtil.OPEN_ROLE_COMMITMENT_MAX_LENGTH);
    }

    @Test
    void rejectsAnonymousProjectCreation() {
        nano = newTestNano();

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Hidden Makerspace Calendar",
            "descriptions", Map.of("en", "A shared calendar and intake flow for community workshop nights so small makerspaces can coordinate volunteers and avoid duplicated prep work."),
            "founderRole", "Founder + Organizer",
            "founderCommitment", "I am already hosting the sessions, coordinating signups, and setting up the space each week.",
            "openRoles", List.of(Map.of("title", "Designer", "commitment", "Shape the first scheduling flow."))
        ), null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_CREATE_AUTH_REQUIRED_CODE);
    }

    @Test
    void blocksProjectCreationUntilTheEmailIsVerified() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.unverified@example.test", "Una Verified", false);

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Shared Workshop Hours",
            "descriptions", Map.of("en", "A lightweight scheduling and handoff tool for neighborhood workshop nights so volunteer hosts can coordinate setup, cleanup, and tool access without relying on private chat threads."),
            "founderRole", "Founder + Host",
            "founderCommitment", "I am already running the sessions, opening the space, and coordinating the volunteer hosts every week.",
            "openRoles", List.of(Map.of("title", "Operations Support", "commitment", "Help coordinate setup and handoff windows."))
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_CREATE_EMAIL_UNVERIFIED_CODE);
    }

    @Test
    void rejectsProjectWithoutOpenRoles() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.two@example.test", "Nora Builder");

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Repair Story Archive",
            "descriptions", Map.of("en", "A simple library for documenting repair stories so volunteers can remember what failed, what worked, and what tools they needed last time."),
            "founderRole", "Founder + Archivist",
            "founderCommitment", "I am already gathering the stories, scanning notes, and interviewing the first volunteer repair teams.",
            "openRoles", List.of()
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_OPEN_ROLES_MIN_CODE);
    }

    @Test
    void rejectsInvalidProjectMediaAndLinks() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.links@example.test", "Link Builder");

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Unsafe Project Links",
            "descriptions", Map.of("en", "A project with enough description to verify invalid external media and links are rejected before storage."),
            "founderRole", "Founder + Link Steward",
            "founderCommitment", "I am keeping project links useful and checking the resources every week.",
            "openRoles", List.of(Map.of("title", "Link Checker", "commitment", "Help review project resources.")),
            "links", List.of(Map.of("label", "Website", "url", "javascript:alert(1)"))
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_LINK_URL_INVALID_CODE);

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Unsafe Project Image",
            "descriptions", Map.of("en", "A project with enough description to verify invalid image uploads are rejected before storage."),
            "founderRole", "Founder + Image Steward",
            "founderCommitment", "I am keeping project images useful and checking the resources every week.",
            "openRoles", List.of(Map.of("title", "Image Checker", "commitment", "Help review project resources."))
        ), sessionCookie).bodyAsMap().asString("slug");
        final HttpObject invalidImage = sendImage("/api/projects/" + slug + "/images", "image/svg+xml", new byte[] {1, 2}, sessionCookie);
        assertThat(invalidImage.statusCode()).isEqualTo(400);
        assertThat(invalidImage.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_IMAGE_TYPE_INVALID_CODE);
    }

    @Test
    void rejectsMalformedProjectPayloadShapes() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.malformed@example.test", "Malformed Payload Owner");

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Malformed Project",
            "descriptions", Map.of("en", "A project with enough description to verify malformed nested payload shapes are rejected cleanly."),
            "founderRole", "Founder + Payload Steward",
            "founderCommitment", "I am keeping project payloads tidy and easy to validate.",
            "openRoles", List.of("not-a-role-map")
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_PAYLOAD_INVALID_CODE);
    }

    @Test
    void onlyProjectOwnerCanEdit() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.three@example.test", "Mika Owner");
        final String intruderCookie = registerAndReturnSessionCookie("intruder@example.test", "Elliot Intruder");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Block Heat Map",
            "descriptions", Map.of("en", "A neighborhood heat resilience project that tracks shade, cooling access, and high-risk blocks so small mutual-aid teams can coordinate the right help faster."),
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "I am already walking the routes, meeting residents, and coordinating the volunteer response plan each week.",
            "openRoles", List.of(Map.of("title", "Data Volunteer", "commitment", "Map the first round of block conditions."))
        ), ownerCookie).bodyAsMap().asString("slug");

        final HttpObject forbiddenEdit = sendJson("/api/projects/" + slug, "PUT", Map.of(
            "title", "Block Heat Map Revised",
            "descriptions", Map.of("en", "A revised description that should never be saved because a non-owner is attempting the edit through the public API."),
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "Still coordinating everything.",
            "openRoles", List.of(Map.of("title", "Data Volunteer", "commitment", "Still mapping conditions."))
        ), intruderCookie);

        assertThat(forbiddenEdit.statusCode()).isEqualTo(403);
        assertThat(forbiddenEdit.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_EDIT_OWNER_REQUIRED_CODE);

        final HttpObject ownerEdit = sendJson("/api/projects/" + slug, "PUT", Map.of(
            "title", "Block Heat Map",
            "descriptions", Map.of("en", "A neighborhood heat resilience project that tracks shade, cooling access, and high-risk blocks so small mutual-aid teams can coordinate the right help faster, with clearer volunteer handoffs."),
            "founderRole", "Founder + Heat Response Lead",
            "founderCommitment", "I am coordinating weekly walks, resident calls, and volunteer handoffs while piloting the first interventions myself.",
            "openRoles", List.of(
                Map.of("title", "Data Volunteer", "commitment", "Map the next round of block conditions."),
                Map.of("title", "Operations Support", "commitment", "Coordinate water, shade, and check-in logistics.")
            )
        ), ownerCookie);

        assertThat(ownerEdit.statusCode()).isEqualTo(200);
        assertThat(ownerEdit.bodyAsMap().asString("slug")).isEqualTo(slug);

        final LinkedTypeMap updatedProject = sendGet("/api/projects/" + slug, ownerCookie).bodyAsMap().asMap("project");
        assertThat(updatedProject.asMap("descriptions").asString("en")).contains("clearer volunteer handoffs");
        assertThat(updatedProject.asMap("founder").asString("role")).isEqualTo("Founder + Heat Response Lead");
        assertThat(updatedProject.asList("openRoles").stream()
            .map(role -> new LinkedTypeMap((Map<?, ?>) role).asString("title"))
            .toList())
            .containsExactly("Data Volunteer", "Operations Support");
    }

    @Test
    void blocksProjectEditingUntilTheEmailIsVerified() {
        nano = newTestNano();
        final String ownerEmail = "owner.edit.unverified@example.test";
        final String ownerCookie = registerAndReturnSessionCookie(ownerEmail, "Edit Unverified");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Neighborhood Heat Watch",
            "descriptions", Map.of("en", "A small coordination project for mapping heat risk and volunteer check-ins so local support teams can reach vulnerable residents faster."),
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "I am organizing the first volunteer routes, resident outreach, and weekly coordination sessions myself.",
            "openRoles", List.of(Map.of("title", "Data Volunteer", "commitment", "Help update the first block-by-block conditions."))
        ), ownerCookie).bodyAsMap().asString("slug");

        markEmailUnverified(ownerEmail);

        final HttpObject response = sendJson("/api/projects/" + slug, "PUT", Map.of(
            "title", "Neighborhood Heat Watch",
            "descriptions", Map.of("en", "Updated description should be blocked."),
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "Updated commitment should be blocked too.",
            "openRoles", List.of(Map.of("title", "Data Volunteer", "commitment", "Still helping.")) 
        ), ownerCookie);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_EDIT_EMAIL_UNVERIFIED_CODE);
    }

    @Test
    void onlyProjectOwnerCanDelete() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.delete@example.test", "Owner Delete");
        final String intruderCookie = registerAndReturnSessionCookie("intruder.delete@example.test", "Intruder Delete");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Community Repair Ledger",
            "descriptions", Map.of("en", "A shared ledger for volunteer repair collectives so each fix, failed attempt, and reused part can be tracked and learned from across neighborhoods."),
            "founderRole", "Founder + Repair Lead",
            "founderCommitment", "I am already organizing the repair nights, capturing the notes, and coordinating the volunteers every week.",
            "openRoles", List.of(Map.of("title", "Data Steward", "commitment", "Organize the repair records."))
        ), ownerCookie).bodyAsMap().asString("slug");

        final HttpObject forbiddenDelete = sendRequest("/api/projects/" + slug, "DELETE", null, intruderCookie);
        assertThat(forbiddenDelete.statusCode()).isEqualTo(403);
        assertThat(forbiddenDelete.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_DELETE_OWNER_REQUIRED_CODE);

        final HttpObject ownerDelete = sendRequest("/api/projects/" + slug, "DELETE", null, ownerCookie);
        assertThat(ownerDelete.statusCode()).isEqualTo(204);

        final HttpObject missingDetail = sendGet("/api/projects/" + slug, ownerCookie);
        assertThat(missingDetail.statusCode()).isEqualTo(404);

        final TypeList feedProjects = sendGet("/api/projects", ownerCookie).bodyAsMap().asList("projects");
        assertThat(feedProjects.stream()
            .map(project -> new LinkedTypeMap((Map<?, ?>) project).asString("slug"))
            .toList())
            .doesNotContain(slug);
    }

    @Test
    void blocksProjectDeletionUntilTheEmailIsVerified() {
        nano = newTestNano();
        final String ownerEmail = "owner.delete.unverified@example.test";
        final String ownerCookie = registerAndReturnSessionCookie(ownerEmail, "Delete Unverified");
        final String viewerCookie = registerAndReturnSessionCookie("viewer.delete.unverified@example.test", "Delete Verified Viewer");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Shared Pantry Routes",
            "descriptions", Map.of("en", "A route-planning effort for volunteer pantry pickups and deliveries so neighborhoods can coordinate stock and drop-offs more reliably."),
            "founderRole", "Founder + Route Lead",
            "founderCommitment", "I am already running the first pickup routes, scheduling volunteers, and coordinating pantry partners each week.",
            "openRoles", List.of(Map.of("title", "Volunteer Dispatcher", "commitment", "Help update delivery shifts and route changes."))
        ), ownerCookie).bodyAsMap().asString("slug");

        markEmailUnverified(ownerEmail);

        final HttpObject response = sendRequest("/api/projects/" + slug, "DELETE", null, ownerCookie);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_DELETE_EMAIL_UNVERIFIED_CODE);

        final HttpObject stillExists = sendGet("/api/projects/" + slug, viewerCookie);
        assertThat(stillExists.statusCode()).isEqualTo(200);
    }

    @Test
    void deletingAnAccountKeepsOwnedProjectsVisible() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.account.delete@example.test", "Owner Account Delete");
        final String viewerCookie = registerAndReturnSessionCookie("viewer.account.delete@example.test", "Viewer Account Delete");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Mutual Aid Logistics",
            "descriptions", Map.of("en", "A coordination project for neighborhood mutual aid teams so delivery runs, supplies, and volunteer handoffs can be managed from one shared operational view."),
            "founderRole", "Founder + Operations Lead",
            "founderCommitment", "I am already coordinating routes, volunteers, and supply pickups every week while running the first delivery shifts myself.",
            "openRoles", List.of(Map.of("title", "Logistics Support", "commitment", "Coordinate route and pickup changes."))
        ), ownerCookie).bodyAsMap().asString("slug");

        final HttpObject deleteAccount = sendRequest("/api/profile", "DELETE", null, ownerCookie);
        assertThat(deleteAccount.statusCode()).isEqualTo(200);
        assertThat(deleteAccount.bodyAsMap().asBoolean("authenticated")).isFalse();

        final HttpObject preservedDetail = sendGet("/api/projects/" + slug, viewerCookie);
        assertThat(preservedDetail.statusCode()).isEqualTo(200);
        assertThat(preservedDetail.bodyAsMap().asMap("project").asString("slug")).isEqualTo(slug);

        final TypeList feedProjects = sendGet("/api/projects", viewerCookie).bodyAsMap().asList("projects");
        assertThat(feedProjects.stream()
            .map(project -> new LinkedTypeMap((Map<?, ?>) project).asString("slug"))
            .toList())
            .contains(slug);
    }

    private Nano newTestNano() {
        return newTestNano(EMAIL_VERIFICATION_SETTINGS, NOOP_TRANSACTIONAL_EMAIL_SENDER);
    }

    private Nano newTestNano(
        final EmailVerificationSettings emailVerificationSettings,
        final TransactionalEmailSender transactionalEmailSender
    ) {
        final PostgresTestDatabase.DatabaseConfig databaseConfig = PostgresTestDatabase.createDatabase("projects");
        TestDatabaseMigrations.migrate(databaseConfig.jdbcUrl(), databaseConfig.jdbcUser(), databaseConfig.jdbcPassword());
        TestDatabaseMigrations.seedInvite(databaseConfig.jdbcUrl(), databaseConfig.jdbcUser(), databaseConfig.jdbcPassword(), OPEN_INVITE);
        databaseRuntime = new DatabaseRuntime(
            databaseConfig.jdbcUrl(),
            databaseConfig.jdbcUser(),
            databaseConfig.jdbcPassword(),
            "mitbauen-test-projects"
        );
        return new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0
            ),
            new HttpServer(),
            new HttpClient(),
            new AuthService(databaseRuntime, emailVerificationSettings, transactionalEmailSender),
            new ProjectFeedService(databaseRuntime)
        );
    }

    private String registerAndReturnSessionCookie(final String email, final String displayName) {
        return registerAndReturnSessionCookie(email, displayName, true);
    }

    private String registerAndReturnSessionCookie(final String email, final String displayName, final boolean markEmailVerified) {
        final HttpObject response = sendJson("/api/auth/register", "POST", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", email,
            "displayName", displayName,
            "password", PRIMARY_PASSWORD
        ), null);
        assertThat(response.statusCode()).isEqualTo(201);
        if (markEmailVerified) {
            markEmailVerified(email);
        }
        return cookieValue(response, AuthUtil.AUTH_SESSION_COOKIE);
    }

    private HttpObject sendGet(final String path, final String sessionCookie) {
        final HttpObject request = new HttpObject().path(baseUrl(path));
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectsApiTest.class));
    }

    private HttpObject sendJson(final String path, final String method, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType(method)
            .contentType("application/json")
            .body(body);
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectsApiTest.class));
    }

    private HttpObject sendImage(final String path, final String contentType, final byte[] body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType("POST")
            .contentType(contentType)
            .body(body);
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectsApiTest.class));
    }

    private static byte[] pngBytes() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
        };
    }

    private HttpObject sendRequest(final String path, final String method, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType(method);
        if (body != null) {
            request.contentType("application/json").body(body);
        }
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectsApiTest.class));
    }

    private String baseUrl(final String path) {
        return "http://localhost:" + nano.service(HttpServer.class).port() + path;
    }

    private String cookieValue(final HttpObject response, final String cookieName) {
        final String setCookie = response.header("set-cookie");
        assertThat(setCookie).isNotBlank();
        final String prefix = cookieName + "=";
        final int start = setCookie.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        final int valueStart = start + prefix.length();
        final int valueEnd = setCookie.indexOf(';', valueStart);
        return valueEnd >= 0 ? setCookie.substring(valueStart, valueEnd) : setCookie.substring(valueStart);
    }

    private void markEmailVerified(final String email) {
        final String sql = """
            update users
            set email_verified_at = current_timestamp
            where email = ?
            """;
        try (var connection = databaseRuntime.dataSource().getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to mark email verified for " + email, exception);
        }
    }

    private void markEmailUnverified(final String email) {
        final String sql = """
            update users
            set email_verified_at = null
            where email = ?
            """;
        try (var connection = databaseRuntime.dataSource().getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to mark email unverified for " + email, exception);
        }
    }
}
