package edu.seu.vcampus.server;

import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessBookRepository;
import edu.seu.vcampus.server.dao.AccessCurriculumCatalogRepository;
import edu.seu.vcampus.server.dao.AccessCourseRepository;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessStoreRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.dispatcher.AcademicRequestHandler;
import edu.seu.vcampus.server.dispatcher.CourseRequestHandler;
import edu.seu.vcampus.server.dispatcher.LibraryRequestHandler;
import edu.seu.vcampus.server.dispatcher.RequestDispatcher;
import edu.seu.vcampus.server.dispatcher.StoreRequestHandler;
import edu.seu.vcampus.server.network.VCampusServer;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.security.PermissionPolicy;
import edu.seu.vcampus.server.service.AcademicService;
import edu.seu.vcampus.server.service.AuditService;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.service.CourseService;
import edu.seu.vcampus.server.service.CurriculumCatalogService;
import edu.seu.vcampus.server.service.LibraryService;
import edu.seu.vcampus.server.service.MailService;
import edu.seu.vcampus.server.service.PasswordResetService;
import edu.seu.vcampus.server.service.StoreService;
import edu.seu.vcampus.server.session.SessionRegistry;

import java.util.logging.Logger;

/** Server entry point. */
public final class ServerApplication {
    private static final Logger LOGGER = Logger.getLogger(ServerApplication.class.getName());

    private ServerApplication() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.load();
        PasswordHasher passwordHasher = new PasswordHasher();
        AccessDatabase database = new AccessDatabase(config.getDatabasePath());
        AccessUserRepository userRepository = new AccessUserRepository(database, passwordHasher);
        AccessOperationLogRepository logRepository =
                new AccessOperationLogRepository(config.getDatabasePath());
        AuditService auditService = new AuditService(logRepository);
        AccessAcademicRepository academicRepository = new AccessAcademicRepository(database);
        AccessCurriculumCatalogRepository catalogRepository =
                new AccessCurriculumCatalogRepository(database);
        AccessCourseRepository courseRepository = new AccessCourseRepository(database);
        AccessBookRepository bookRepository = new AccessBookRepository(database);
        AccessStoreRepository storeRepository = new AccessStoreRepository(database);
        SessionRegistry sessions = new SessionRegistry();
        MailService mailService = MailService.tryLoadDefault();
        PasswordResetService passwordResetService = new PasswordResetService(5 * 60 * 1000L);
        AuthService authService =
                new AuthService(userRepository, passwordHasher, sessions, auditService,
                        mailService, passwordResetService);
        if (mailService != null) {
            LOGGER.info("Password-reset mail service enabled");
        } else {
            LOGGER.warning("Password-reset mail service disabled (mail.properties missing)");
        }
        PermissionPolicy permissionPolicy = new PermissionPolicy();
        AcademicService academicService = new AcademicService(academicRepository, userRepository);
        CurriculumCatalogService catalogService =
                new CurriculumCatalogService(catalogRepository);
        CourseService courseService = new CourseService(courseRepository);
        LibraryService libraryService = new LibraryService(bookRepository);
        StoreService storeService = new StoreService(storeRepository);
        AcademicRequestHandler academicHandler =
                new AcademicRequestHandler(academicService, catalogService);
        CourseRequestHandler courseHandler = new CourseRequestHandler(courseService);
        LibraryRequestHandler libraryHandler = new LibraryRequestHandler(libraryService);
        StoreRequestHandler storeHandler = new StoreRequestHandler(storeService);
        RequestDispatcher dispatcher = new RequestDispatcher(
                authService, sessions, permissionPolicy, auditService,
                academicHandler, courseHandler, libraryHandler, storeHandler);
        final VCampusServer server = new VCampusServer(
                config.getPort(), config.getWorkerThreads(), dispatcher);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.close();
                } catch (Exception ignored) {
                    // Process is already shutting down.
                }
            }
        }, "vcampus-shutdown"));
        LOGGER.info("Access database: " + userRepository.getDatabaseFile());
        LOGGER.info("Library tables ready: " + database.getDatabaseFile());
        LOGGER.info("SEU catalog ready: "
                + catalogRepository.getImportSummary().getDepartmentCount() + " departments, "
                + catalogRepository.getImportSummary().getMajorCount() + " majors, "
                + catalogRepository.getImportSummary().getCourseCount() + " courses");
        server.start();
    }
}
