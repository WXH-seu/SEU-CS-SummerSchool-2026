package edu.seu.vcampus.server;

import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessCourseRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.dispatcher.AcademicRequestHandler;
import edu.seu.vcampus.server.dispatcher.CourseRequestHandler;
import edu.seu.vcampus.server.dispatcher.RequestDispatcher;
import edu.seu.vcampus.server.network.VCampusServer;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.service.AcademicService;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.service.CourseService;
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
        AccessAcademicRepository academicRepository = new AccessAcademicRepository(database);
        AccessCourseRepository courseRepository = new AccessCourseRepository(database);
        SessionRegistry sessions = new SessionRegistry();
        AuthService authService = new AuthService(userRepository, passwordHasher, sessions);
        AcademicService academicService = new AcademicService(academicRepository, userRepository);
        CourseService courseService = new CourseService(courseRepository);
        AcademicRequestHandler academicHandler = new AcademicRequestHandler(academicService);
        CourseRequestHandler courseHandler = new CourseRequestHandler(courseService);
        RequestDispatcher dispatcher = new RequestDispatcher(
                authService, sessions, academicHandler, courseHandler);
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
        server.start();
    }
}
