package edu.seu.vcampus.server;

import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessBookRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.dispatcher.AcademicRequestHandler;
import edu.seu.vcampus.server.dispatcher.RequestDispatcher;
import edu.seu.vcampus.server.network.VCampusServer;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.service.AcademicService;
import edu.seu.vcampus.server.service.AuthService;
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
        new AccessBookRepository(database);
        SessionRegistry sessions = new SessionRegistry();
        AuthService authService = new AuthService(userRepository, passwordHasher, sessions);
        AcademicService academicService = new AcademicService(academicRepository, userRepository);
        AcademicRequestHandler academicHandler = new AcademicRequestHandler(academicService);
        RequestDispatcher dispatcher = new RequestDispatcher(
                authService, sessions, academicHandler);
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
        server.start();
    }
}
