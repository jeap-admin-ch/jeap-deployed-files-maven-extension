package ch.admin.bit.jeap.maven.deployedfiles;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.aether.RepositoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records every file the build deploys to a remote repository, from the deploy events of the Maven Resolver, and
 * writes the list as JSON when the build ends.
 * <p>
 * Maven forwards the repository events of the resolver to its event spies, and only a core extension can provide
 * one. Load the jar with {@code -Dmaven.ext.class.path=<jar>}.
 * <p>
 * The spy never fails the build: every error is caught, and the first one is logged as a warning.
 * <p>
 * User properties:
 * <ul>
 *     <li>{@code deployedFiles.output}: the file to write, default {@code target/deployed-files.json}. A relative
 *     path is resolved against the execution root.</li>
 *     <li>{@code deployedFiles.skip}: {@code true} turns the spy off.</li>
 * </ul>
 */
@Named("deployed-files")
@Singleton
public class DeployedFilesEventSpy extends AbstractEventSpy {

    static final String OUTPUT_PROPERTY = "deployedFiles.output";
    static final String SKIP_PROPERTY = "deployedFiles.skip";
    static final String DEFAULT_OUTPUT = "target/deployed-files.json";

    static final String BUILD_SUCCESS = "SUCCESS";
    static final String BUILD_FAILURE = "FAILURE";

    private final Logger log;
    private final DeployedFilesRecorder recorder = new DeployedFilesRecorder();
    private final AtomicBoolean warned = new AtomicBoolean();

    private volatile boolean skip;
    private volatile String outputProperty;
    private volatile String executionRoot;
    private volatile String buildResult;
    private volatile String mavenVersion;
    private volatile boolean writeAttempted;

    /** Used by Maven. */
    public DeployedFilesEventSpy() {
        this(LoggerFactory.getLogger(DeployedFilesEventSpy.class));
    }

    DeployedFilesEventSpy(Logger log) {
        this.log = log;
    }

    @Override
    public void init(Context context) {
        try {
            Map<String, Object> data = context.getData();
            Properties userProperties = (Properties) data.get("userProperties");
            Properties systemProperties = (Properties) data.get("systemProperties");
            outputProperty = property(userProperties, systemProperties, OUTPUT_PROPERTY);
            skip = Boolean.parseBoolean(property(userProperties, systemProperties, SKIP_PROPERTY));
            Object workingDirectory = data.get("workingDirectory");
            if (workingDirectory != null) {
                executionRoot = workingDirectory.toString();
            }
            mavenVersion = mavenVersion();
        } catch (Throwable t) {
            warn("could not initialise", t);
        }
    }

    @Override
    public void onEvent(Object event) {
        if (skip) {
            return;
        }
        try {
            if (event instanceof RepositoryEvent repositoryEvent) {
                recorder.record(repositoryEvent);
            } else if (event instanceof MavenExecutionRequest request) {
                // the directory of the top-level POM (-f), which the session uses as execution root
                if (request.getBaseDirectory() != null) {
                    executionRoot = request.getBaseDirectory();
                }
            } else if (event instanceof ExecutionEvent executionEvent) {
                if (executionEvent.getType() == ExecutionEvent.Type.SessionStarted
                        && executionEvent.getSession() != null
                        && executionEvent.getSession().getExecutionRootDirectory() != null) {
                    executionRoot = executionEvent.getSession().getExecutionRootDirectory();
                }
            } else if (event instanceof MavenExecutionResult result) {
                // the last event of a build: Maven sends it after the session has ended, right before close()
                buildResult = result.hasExceptions() ? BUILD_FAILURE : BUILD_SUCCESS;
                write();
            }
        } catch (Throwable t) {
            warn("could not record an event", t);
        }
    }

    /** Writes the list if the build ended without a result event, which carries the build result. */
    @Override
    public void close() {
        if (skip || writeAttempted) {
            return;
        }
        write();
    }

    /** Writes the list, once per build: after a failed attempt, close() does not try again. */
    private synchronized void write() {
        writeAttempted = true;
        try {
            Path output = outputPath();
            List<DeployedFile> files = recorder.files();
            String json = DeployedFilesJson.document(mavenVersion, executionRoot, buildResult,
                    Instant.now().toString(), files);
            Files.createDirectories(output.getParent());
            // written beside the output and then moved, so that a reader never sees half a list
            Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            long deployed = files.stream().filter(file -> DeployedFile.STATUS_DEPLOYED.equals(file.status)).count();
            long notDeployed = files.size() - deployed;
            log.info("[deployed-files] {} deployed file(s){} recorded in {}", deployed,
                    notDeployed == 0 ? "" : " and " + notDeployed + " failed or incomplete upload(s)", output);
        } catch (Throwable t) {
            warn("could not write the list", t);
        }
    }

    /** The configured output, or the default; a relative path is resolved against the execution root. */
    Path outputPath() {
        String root = executionRoot != null ? executionRoot : System.getProperty("user.dir");
        String configured = outputProperty;
        String output = configured == null || configured.isBlank() ? DEFAULT_OUTPUT : configured.trim();
        return Paths.get(root).resolve(output).toAbsolutePath().normalize();
    }

    private static String mavenVersion() {
        try (InputStream in = MavenExecutionRequest.class.getResourceAsStream(
                "/META-INF/maven/org.apache.maven/maven-core/pom.properties")) {
            if (in == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String property(Properties userProperties, Properties systemProperties, String name) {
        String value = userProperties == null ? null : userProperties.getProperty(name);
        if (value == null && systemProperties != null) {
            value = systemProperties.getProperty(name);
        }
        return value;
    }

    /** Logs the first error only: one broken build must not produce a warning per event. */
    private void warn(String what, Throwable t) {
        if (warned.compareAndSet(false, true)) {
            try {
                log.warn("[deployed-files] {}, the list of deployed files may be missing or incomplete: {}", what,
                        String.valueOf(t));
            } catch (Throwable ignored) {
                // not even a failing logger may fail the build
            }
        }
    }
}
