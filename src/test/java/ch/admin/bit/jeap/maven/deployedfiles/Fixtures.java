package ch.admin.bit.jeap.maven.deployedfiles;

import org.apache.maven.eventspy.EventSpy;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test fixtures: resolver events as Maven sends them, and the collaborators of the spy. */
final class Fixtures {

    static final String RELEASES_URL = "https://repository.example.org/repository/releases/";
    static final String SNAPSHOTS_URL = "https://repository.example.org/repository/snapshots/";

    static final RemoteRepository RELEASES = new RemoteRepository.Builder("releases", "default", RELEASES_URL).build();
    static final RemoteRepository SNAPSHOTS =
            new RemoteRepository.Builder("snapshots", "default", SNAPSHOTS_URL).build();

    /** The events only carry the session, the spy never uses it. */
    static final RepositorySystemSession SESSION = (RepositorySystemSession) Proxy.newProxyInstance(
            Fixtures.class.getClassLoader(), new Class<?>[]{RepositorySystemSession.class},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });

    private Fixtures() {
    }

    @SuppressWarnings("deprecation") // Maven 3.9 has no setPath()
    static Artifact artifact(String coordinates, Path file) {
        Artifact artifact = new DefaultArtifact(coordinates);
        return file == null ? artifact : artifact.setFile(file.toFile());
    }

    @SuppressWarnings("deprecation") // Maven 3.9 has no constructor taking a Path
    static Metadata metadata(String groupId, String artifactId, String version, Metadata.Nature nature, Path file) {
        File local = file == null ? null : file.toFile();
        return new DefaultMetadata(groupId, artifactId, version, "maven-metadata.xml", nature, local);
    }

    static RepositoryEvent deploying(RemoteRepository repository, Artifact artifact) {
        return event(RepositoryEvent.EventType.ARTIFACT_DEPLOYING, repository).setArtifact(artifact).build();
    }

    static RepositoryEvent deployed(RemoteRepository repository, Artifact artifact) {
        return event(RepositoryEvent.EventType.ARTIFACT_DEPLOYED, repository).setArtifact(artifact).build();
    }

    static RepositoryEvent failed(RemoteRepository repository, Artifact artifact, Exception exception) {
        return event(RepositoryEvent.EventType.ARTIFACT_DEPLOYED, repository).setArtifact(artifact)
                .setException(exception).build();
    }

    static RepositoryEvent deploying(RemoteRepository repository, Metadata metadata) {
        return event(RepositoryEvent.EventType.METADATA_DEPLOYING, repository).setMetadata(metadata).build();
    }

    static RepositoryEvent deployed(RemoteRepository repository, Metadata metadata) {
        return event(RepositoryEvent.EventType.METADATA_DEPLOYED, repository).setMetadata(metadata).build();
    }

    static RepositoryEvent.Builder event(RepositoryEvent.EventType type, RemoteRepository repository) {
        return new RepositoryEvent.Builder(SESSION, type).setRepository(repository);
    }

    static Path file(Path directory, String name, String content) throws IOException {
        return Files.write(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    /** The context Maven passes to {@code init}: the user and system properties and the working directory. */
    static EventSpy.Context context(Path workingDirectory, Map<String, String> userProperties) {
        return context(workingDirectory, userProperties, Map.of());
    }

    static EventSpy.Context context(Path workingDirectory, Map<String, String> userProperties,
                                    Map<String, String> systemProperties) {
        Map<String, Object> data = new HashMap<>();
        data.put("userProperties", properties(userProperties));
        data.put("systemProperties", properties(systemProperties));
        if (workingDirectory != null) {
            data.put("workingDirectory", workingDirectory.toString());
        }
        return () -> data;
    }

    private static Properties properties(Map<String, String> values) {
        Properties properties = new Properties();
        properties.putAll(values);
        return properties;
    }

    /** A logger that keeps what was logged, as {@code "<level> <message format>"}. */
    static final class RecordingLogger {
        final List<String> lines = new CopyOnWriteArrayList<>();
        final Logger logger = (Logger) Proxy.newProxyInstance(Fixtures.class.getClassLoader(),
                new Class<?>[]{Logger.class}, (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) {
                        return true;
                    }
                    if (method.getReturnType() == String.class) {
                        return "recording";
                    }
                    if (args != null && args.length > 0 && args[0] instanceof String message) {
                        lines.add(method.getName() + " " + message);
                    }
                    return null;
                });

        long warnings() {
            return lines.stream().filter(line -> line.startsWith("warn ")).count();
        }
    }
}
