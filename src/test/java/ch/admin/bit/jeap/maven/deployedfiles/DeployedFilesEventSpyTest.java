package ch.admin.bit.jeap.maven.deployedfiles;

import ch.admin.bit.jeap.maven.deployedfiles.Fixtures.RecordingLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.RELEASES;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.RELEASES_URL;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.SNAPSHOTS;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.SNAPSHOTS_URL;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.artifact;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.context;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.deployed;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.deploying;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.failed;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.file;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DeployedFilesEventSpyTest {

    /** sha1 of the bytes of "hello" */
    private static final String SHA1_OF_HELLO = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingLogger log = new RecordingLogger();
    private final DeployedFilesEventSpy spy = new DeployedFilesEventSpy(log.logger);

    @TempDir
    Path executionRoot;
    @TempDir
    Path localRepository;

    private Path defaultOutput;

    @BeforeEach
    void setUp() {
        defaultOutput = executionRoot.resolve("target/deployed-files.json");
    }

    // --- events ---

    @Test
    void deployedReleaseArtifact_isRecordedWithCoordinatesPathSizeAndSha1() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deploying(RELEASES, jar));
        spy.onEvent(deployed(RELEASES, jar));
        spy.onEvent(success());

        JsonNode files = readFiles(defaultOutput);
        assertThat(files).hasSize(1);
        JsonNode file = files.get(0);
        assertThat(fieldNames(file)).containsExactly("kind", "status", "repositoryId", "repositoryUrl", "path",
                "groupId", "artifactId", "baseVersion", "version", "classifier", "extension", "size", "sha1");
        assertThat(file.get("kind").asText()).isEqualTo("artifact");
        assertThat(file.get("status").asText()).isEqualTo("deployed");
        assertThat(file.get("repositoryId").asText()).isEqualTo("releases");
        assertThat(file.get("repositoryUrl").asText()).isEqualTo(RELEASES_URL);
        assertThat(file.get("path").asText()).isEqualTo("org/example/lib/module-a/1.2.0/module-a-1.2.0.jar");
        assertThat(file.get("groupId").asText()).isEqualTo("org.example.lib");
        assertThat(file.get("artifactId").asText()).isEqualTo("module-a");
        assertThat(file.get("baseVersion").asText()).isEqualTo("1.2.0");
        assertThat(file.get("version").asText()).isEqualTo("1.2.0");
        assertThat(file.get("classifier").asText()).isEmpty();
        assertThat(file.get("extension").asText()).isEqualTo("jar");
        assertThat(file.get("size").isIntegralNumber()).isTrue();
        assertThat(file.get("size").asLong()).isEqualTo(5);
        assertThat(file.get("sha1").asText()).isEqualTo(SHA1_OF_HELLO);
    }

    @Test
    void deployedSnapshotArtifact_hasTheTimestampedVersionAndTheBaseVersion() throws Exception {
        Artifact sources = artifact("org.example.lib:module-a:jar:sources:2.0.0-20261113.074028-1",
                file(localRepository, "a-sources.jar", "hello"));
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deployed(SNAPSHOTS, sources));
        spy.onEvent(success());

        JsonNode file = readFiles(defaultOutput).get(0);
        assertThat(file.get("repositoryUrl").asText()).isEqualTo(SNAPSHOTS_URL);
        assertThat(file.get("version").asText()).isEqualTo("2.0.0-20261113.074028-1");
        assertThat(file.get("baseVersion").asText()).isEqualTo("2.0.0-SNAPSHOT");
        assertThat(file.get("classifier").asText()).isEqualTo("sources");
        assertThat(file.get("path").asText())
                .isEqualTo("org/example/lib/module-a/2.0.0-SNAPSHOT/module-a-2.0.0-20261113.074028-1-sources.jar");
    }

    @Test
    void deployedMetadata_isRecordedOnBothLevels() throws Exception {
        Path xml = file(localRepository, "maven-metadata.xml", "hello");
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deployed(SNAPSHOTS,
                metadata("org.example.lib", "module-a", "2.0.0-SNAPSHOT", Metadata.Nature.SNAPSHOT, xml)));
        spy.onEvent(deployed(SNAPSHOTS,
                metadata("org.example.lib", "module-a", "", Metadata.Nature.RELEASE_OR_SNAPSHOT, xml)));
        spy.onEvent(success());

        JsonNode files = readFiles(defaultOutput);
        assertThat(files).hasSize(2);
        JsonNode versionLevel = files.get(0);
        assertThat(fieldNames(versionLevel)).containsExactly("kind", "status", "repositoryId", "repositoryUrl",
                "path", "groupId", "artifactId", "version", "type", "nature", "size", "sha1");
        assertThat(versionLevel.get("kind").asText()).isEqualTo("metadata");
        assertThat(versionLevel.get("status").asText()).isEqualTo("deployed");
        assertThat(versionLevel.get("path").asText())
                .isEqualTo("org/example/lib/module-a/2.0.0-SNAPSHOT/maven-metadata.xml");
        assertThat(versionLevel.get("version").asText()).isEqualTo("2.0.0-SNAPSHOT");
        assertThat(versionLevel.get("type").asText()).isEqualTo("maven-metadata.xml");
        assertThat(versionLevel.get("nature").asText()).isEqualTo("SNAPSHOT");
        assertThat(versionLevel.get("sha1").asText()).isEqualTo(SHA1_OF_HELLO);
        JsonNode artifactLevel = files.get(1);
        assertThat(artifactLevel.get("path").asText()).isEqualTo("org/example/lib/module-a/maven-metadata.xml");
        assertThat(artifactLevel.get("version").asText()).isEmpty();
        assertThat(artifactLevel.get("nature").asText()).isEqualTo("RELEASE_OR_SNAPSHOT");
    }

    @Test
    void failedUpload_isRecordedAsFailedWithTheError() throws Exception {
        Artifact pom = artifact("org.example.lib:module-b:pom:1.4.0", file(localRepository, "b.pom", "hello"));
        String message = "Could not transfer artifact org.example.lib:module-b:pom:1.4.0 from/to releases ("
                + RELEASES_URL + "): status code: 400, reason phrase: cannot be updated (400)";
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deploying(RELEASES, pom));
        spy.onEvent(failed(RELEASES, pom, new ArtifactTransferException(pom, RELEASES, message)));
        spy.onEvent(failure());

        JsonNode document = read(defaultOutput);
        assertThat(document.get("buildResult").asText()).isEqualTo("FAILURE");
        assertThat(document.get("files")).hasSize(1);
        JsonNode file = document.get("files").get(0);
        assertThat(file.get("status").asText()).isEqualTo("failed");
        assertThat(file.get("error").asText()).isEqualTo(message);
        assertThat(file.get("path").asText()).isEqualTo("org/example/lib/module-b/1.4.0/module-b-1.4.0.pom");
        assertThat(log.lines).anyMatch(line -> line.startsWith("info "));
    }

    @Test
    void failedUpload_exceptionWithoutMessage() throws Exception {
        Artifact pom = artifact("org.example.lib:module-b:pom:1.4.0", null);
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(failed(RELEASES, pom, new IllegalStateException()));
        spy.onEvent(failure());

        JsonNode file = readFiles(defaultOutput).get(0);
        assertThat(file.get("status").asText()).isEqualTo("failed");
        assertThat(file.get("error").asText()).isEqualTo("null");
    }

    @Test
    void uploadThatNeverEnded_isRecordedAsIncompleteWithoutSizeAndSha1() throws Exception {
        Artifact done = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        Artifact open = artifact("org.example.lib:module-b:jar:1.2.0", file(localRepository, "b.jar", "hello"));
        Metadata openMetadata = metadata("org.example.lib", "module-b", "", Metadata.Nature.RELEASE_OR_SNAPSHOT,
                file(localRepository, "maven-metadata.xml", "hello"));
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deploying(RELEASES, open));
        spy.onEvent(deploying(RELEASES, done));
        spy.onEvent(deployed(RELEASES, done));
        spy.onEvent(deploying(RELEASES, openMetadata));
        spy.onEvent(failure());

        JsonNode files = readFiles(defaultOutput);
        assertThat(files).hasSize(3);
        assertThat(files.get(0).get("status").asText()).isEqualTo("deployed");
        assertThat(files.get(1).get("status").asText()).isEqualTo("incomplete");
        assertThat(files.get(1).get("path").asText()).isEqualTo("org/example/lib/module-b/1.2.0/module-b-1.2.0.jar");
        assertThat(files.get(1).has("size")).isFalse();
        assertThat(files.get(1).has("sha1")).isFalse();
        assertThat(files.get(2).get("status").asText()).isEqualTo("incomplete");
        assertThat(files.get(2).get("kind").asText()).isEqualTo("metadata");
    }

    @Test
    void sameFileInTwoRepositories_areTwoUploads() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deploying(RELEASES, jar));
        spy.onEvent(deploying(SNAPSHOTS, jar));
        spy.onEvent(deployed(RELEASES, jar));
        spy.onEvent(success());

        JsonNode files = readFiles(defaultOutput);
        assertThat(files).hasSize(2);
        assertThat(files.get(0).get("status").asText()).isEqualTo("deployed");
        assertThat(files.get(0).get("repositoryUrl").asText()).isEqualTo(RELEASES_URL);
        assertThat(files.get(1).get("status").asText()).isEqualTo("incomplete");
        assertThat(files.get(1).get("repositoryUrl").asText()).isEqualTo(SNAPSHOTS_URL);
    }

    @Test
    void deployedFileThatIsGoneLocally_isRecordedWithoutSizeAndSha1() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", localRepository.resolve("missing.jar"));
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(deployed(RELEASES, jar));
        spy.onEvent(success());

        JsonNode file = readFiles(defaultOutput).get(0);
        assertThat(file.get("status").asText()).isEqualTo("deployed");
        assertThat(file.has("size")).isFalse();
        assertThat(file.has("sha1")).isFalse();
        assertThat(log.warnings()).isZero();
    }

    @Test
    void otherEvents_areIgnored() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of()));

        for (RepositoryEvent.EventType type : RepositoryEvent.EventType.values()) {
            if (!type.name().contains("DEPLOY")) {
                spy.onEvent(Fixtures.event(type, RELEASES).setArtifact(jar).build());
            }
        }
        spy.onEvent("an event of another kind");
        spy.onEvent(null);
        spy.onEvent(success());

        assertThat(readFiles(defaultOutput)).isEmpty();
        assertThat(log.warnings()).isZero();
    }

    @Test
    void deployEventWithoutArtifactAndMetadata_isIgnored() throws Exception {
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(Fixtures.event(RepositoryEvent.EventType.ARTIFACT_DEPLOYING, RELEASES).build());
        spy.onEvent(Fixtures.event(RepositoryEvent.EventType.ARTIFACT_DEPLOYED, null).build());
        spy.onEvent(success());

        assertThat(readFiles(defaultOutput)).isEmpty();
        assertThat(log.warnings()).isZero();
    }

    @Test
    void buildWithoutDeployment_writesAnEmptyList() throws Exception {
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(success());

        JsonNode document = read(defaultOutput);
        assertThat(fieldNames(document)).containsExactly("formatVersion", "mavenVersion", "executionRoot",
                "buildResult", "writtenAt", "files");
        assertThat(document.get("formatVersion").isIntegralNumber()).isTrue();
        assertThat(document.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(document.get("mavenVersion").asText()).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(document.get("executionRoot").asText()).isEqualTo(executionRoot.toString());
        assertThat(document.get("buildResult").asText()).isEqualTo("SUCCESS");
        assertThat(document.get("writtenAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        assertThat(document.get("files").isArray()).isTrue();
        assertThat(document.get("files")).isEmpty();
    }

    // --- when the list is written ---

    @Test
    void close_withoutAResultEvent_writesTheListWithoutABuildResult() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of()));
        spy.onEvent(deployed(RELEASES, jar));

        spy.close();

        JsonNode document = read(defaultOutput);
        assertThat(document.has("buildResult")).isFalse();
        assertThat(document.get("files")).hasSize(1);
    }

    @Test
    void close_afterTheResultEvent_doesNotWriteAgain() throws Exception {
        spy.init(context(executionRoot, Map.of()));
        spy.onEvent(success());
        FileTime marker = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(defaultOutput, marker);

        spy.close();

        assertThat(Files.getLastModifiedTime(defaultOutput)).isEqualTo(marker);
    }

    @Test
    void existingList_isReplacedAndNoTemporaryFileIsLeft() throws Exception {
        Files.createDirectories(defaultOutput.getParent());
        Files.writeString(defaultOutput, "the list of an earlier build");
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(success());

        assertThat(read(defaultOutput).get("files")).isEmpty();
        try (var files = Files.list(defaultOutput.getParent())) {
            assertThat(files).containsExactly(defaultOutput);
        }
    }

    // --- output path ---

    @Test
    void output_defaultsToTheTargetDirectoryOfTheExecutionRoot() {
        spy.init(context(executionRoot, Map.of()));

        assertThat(spy.outputPath()).isEqualTo(executionRoot.resolve("target/deployed-files.json"));
    }

    @Test
    void output_executionRootIsTheBaseDirectoryOfTheRequest_notTheWorkingDirectory() throws Exception {
        Path project = Files.createDirectory(executionRoot.resolve("project"));
        spy.init(context(executionRoot, Map.of()));

        // mvn -f project/pom.xml
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(project.toFile());
        spy.onEvent(request);
        spy.onEvent(success());

        assertThat(project.resolve("target/deployed-files.json")).isRegularFile();
        assertThat(defaultOutput).doesNotExist();
        assertThat(read(project.resolve("target/deployed-files.json")).get("executionRoot").asText())
                .isEqualTo(project.toString());
    }

    @Test
    void output_executionRootIsTakenFromTheStartedSession() throws Exception {
        Path project = Files.createDirectory(executionRoot.resolve("project"));
        spy.init(context(executionRoot, Map.of()));

        spy.onEvent(sessionStarted(project));
        spy.onEvent(success());

        assertThat(project.resolve("target/deployed-files.json")).isRegularFile();
    }

    @Test
    void output_relativePathIsResolvedAgainstTheExecutionRoot() {
        spy.init(context(executionRoot, Map.of("deployedFiles.output", "out/files.json")));

        spy.onEvent(success());

        assertThat(executionRoot.resolve("out/files.json")).isRegularFile();
        assertThat(defaultOutput).doesNotExist();
    }

    @Test
    void output_absolutePathIsUsedAsIs(@TempDir Path elsewhere) {
        Path output = elsewhere.resolve("nested/directory/deployed-files.json");
        spy.init(context(executionRoot, Map.of("deployedFiles.output", output.toString())));

        spy.onEvent(success());

        assertThat(output).isRegularFile();
        assertThat(defaultOutput).doesNotExist();
    }

    @Test
    void output_blankPropertyMeansTheDefault() {
        spy.init(context(executionRoot, Map.of("deployedFiles.output", " ")));

        assertThat(spy.outputPath()).isEqualTo(defaultOutput);
    }

    @Test
    void output_userPropertyWinsOverSystemProperty() {
        spy.init(context(executionRoot, Map.of("deployedFiles.output", "user.json"),
                Map.of("deployedFiles.output", "system.json")));

        assertThat(spy.outputPath()).isEqualTo(executionRoot.resolve("user.json"));
    }

    @Test
    void output_systemPropertyIsUsedWithoutAUserProperty() {
        spy.init(context(executionRoot, Map.of(), Map.of("deployedFiles.output", "system.json")));

        assertThat(spy.outputPath()).isEqualTo(executionRoot.resolve("system.json"));
    }

    @Test
    void output_withoutAnyExecutionRoot_isResolvedAgainstTheWorkingDirectoryOfTheJvm() {
        spy.init(context(null, Map.of()));

        assertThat(spy.outputPath())
                .isEqualTo(Path.of(System.getProperty("user.dir")).resolve("target/deployed-files.json"));
    }

    // --- skip switch ---

    @Test
    void skip_writesNothing() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of("deployedFiles.skip", "true")));

        spy.onEvent(deployed(RELEASES, jar));
        spy.onEvent(success());
        spy.close();

        assertThat(executionRoot).isEmptyDirectory();
        assertThat(log.lines).isEmpty();
    }

    @Test
    void skip_leavesAnExistingListAlone() throws Exception {
        Files.createDirectories(defaultOutput.getParent());
        Files.writeString(defaultOutput, "the list of an earlier build");
        spy.init(context(executionRoot, Map.of("deployedFiles.skip", "TRUE")));

        spy.onEvent(success());
        spy.close();

        assertThat(defaultOutput).hasContent("the list of an earlier build");
    }

    @Test
    void skip_anyOtherValueDoesNotSkip() {
        spy.init(context(executionRoot, Map.of("deployedFiles.skip", "false")));

        spy.onEvent(success());

        assertThat(defaultOutput).isRegularFile();
    }

    // --- never fails the build ---

    @Test
    void unwritableOutput_isLoggedOnceAndNotRetried() throws Exception {
        // the parent of the output is a file, so the directory cannot be created: fails for every user, also root
        Path blocker = file(executionRoot, "blocker", "a file where a directory is needed");
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of("deployedFiles.output", "blocker/deployed-files.json")));
        spy.onEvent(deployed(RELEASES, jar));

        assertThatCode(() -> {
            spy.onEvent(success());
            spy.close();
        }).doesNotThrowAnyException();

        assertThat(blocker).hasContent("a file where a directory is needed");
        assertThat(log.warnings()).isEqualTo(1);
        assertThat(log.lines).noneMatch(line -> line.startsWith("info "));
    }

    @Test
    void outputThatIsADirectory_isLoggedAndLeavesNoTemporaryFile() throws Exception {
        Path directory = Files.createDirectories(executionRoot.resolve("out/deployed-files.json"));
        Files.createFile(directory.resolve("content"));
        spy.init(context(executionRoot, Map.of("deployedFiles.output", "out/deployed-files.json")));

        assertThatCode(() -> spy.onEvent(success())).doesNotThrowAnyException();

        assertThat(log.warnings()).isEqualTo(1);
        assertThat(directory).isDirectory();
    }

    @Test
    void brokenContext_doesNotFailAndTheListIsStillWritten() {
        assertThatCode(() -> spy.init(() -> {
            throw new IllegalStateException("no data");
        })).doesNotThrowAnyException();
        assertThatCode(() -> spy.init(context(executionRoot, Map.of()))).doesNotThrowAnyException();

        spy.onEvent(success());

        assertThat(log.warnings()).isEqualTo(1);
        assertThat(defaultOutput).isRegularFile();
    }

    @Test
    void brokenEvents_areLoggedOnceAndTheOthersAreStillRecorded() throws Exception {
        Artifact jar = artifact("org.example.lib:module-a:jar:1.2.0", file(localRepository, "a.jar", "hello"));
        spy.init(context(executionRoot, Map.of()));

        assertThatCode(() -> {
            spy.onEvent(brokenEvent());
            spy.onEvent(brokenEvent());
            spy.onEvent(brokenEvent());
        }).doesNotThrowAnyException();
        spy.onEvent(deployed(RELEASES, jar));
        spy.onEvent(success());

        assertThat(log.warnings()).isEqualTo(1);
        assertThat(readFiles(defaultOutput)).hasSize(1);
    }

    @Test
    void failingLogger_doesNotFail() {
        DeployedFilesEventSpy spyWithFailingLogger = new DeployedFilesEventSpy(
                (org.slf4j.Logger) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{org.slf4j.Logger.class}, (proxy, method, args) -> {
                            throw new IllegalStateException("logger");
                        }));
        spyWithFailingLogger.init(context(executionRoot, Map.of()));

        assertThatCode(() -> {
            spyWithFailingLogger.onEvent(brokenEvent());
            spyWithFailingLogger.onEvent(success());
            spyWithFailingLogger.close();
        }).doesNotThrowAnyException();

        assertThat(defaultOutput).isRegularFile();
    }

    // --- thread safety ---

    @Test
    void parallelUploads_areAllRecordedExactlyOnce() throws Exception {
        int threads = 8;
        int filesPerThread = 250;
        Path content = file(localRepository, "content.jar", "hello");
        spy.init(context(executionRoot, Map.of()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < filesPerThread; i++) {
                        String coordinates = "org.example.lib:module-" + thread + ":jar:c" + i + ":1.0.0";
                        Artifact jar = artifact(coordinates, content);
                        spy.onEvent(deploying(RELEASES, jar));
                        if (i % 10 == 0) {
                            spy.onEvent(failed(RELEASES, jar, new IllegalStateException("status code: 500")));
                        } else if (i % 10 != 1) {
                            spy.onEvent(deployed(RELEASES, jar));
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        spy.onEvent(failure());

        JsonNode files = readFiles(defaultOutput);
        assertThat(files).hasSize(threads * filesPerThread);
        Set<String> paths = new HashSet<>();
        int deployed = 0;
        int failed = 0;
        int incomplete = 0;
        for (JsonNode file : files) {
            paths.add(file.get("path").asText());
            switch (file.get("status").asText()) {
                case "deployed" -> {
                    deployed++;
                    assertThat(file.get("sha1").asText()).isEqualTo(SHA1_OF_HELLO);
                }
                case "failed" -> failed++;
                case "incomplete" -> incomplete++;
                default -> throw new AssertionError(file);
            }
        }
        assertThat(paths).hasSize(threads * filesPerThread);
        assertThat(failed).isEqualTo(threads * filesPerThread / 10);
        assertThat(incomplete).isEqualTo(threads * filesPerThread / 10);
        assertThat(deployed).isEqualTo(threads * filesPerThread * 8 / 10);
        assertThat(log.warnings()).isZero();
    }

    // --- helpers ---

    private static MavenExecutionResult success() {
        return new DefaultMavenExecutionResult();
    }

    private static MavenExecutionResult failure() {
        return new DefaultMavenExecutionResult().addException(new IllegalStateException("deploy failed"));
    }

    /** A deploy event whose metadata cannot be read. */
    private static RepositoryEvent brokenEvent() {
        Metadata broken = (Metadata) java.lang.reflect.Proxy.newProxyInstance(Fixtures.class.getClassLoader(),
                new Class<?>[]{Metadata.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("broken metadata");
                });
        return deployed(RELEASES, broken);
    }

    @SuppressWarnings("deprecation")
    private static ExecutionEvent sessionStarted(Path baseDirectory) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(baseDirectory.toFile());
        MavenSession session = new MavenSession(null, Fixtures.SESSION, request, new DefaultMavenExecutionResult());
        return new ExecutionEvent() {
            @Override
            public Type getType() {
                return Type.SessionStarted;
            }

            @Override
            public MavenSession getSession() {
                return session;
            }

            @Override
            public MavenProject getProject() {
                return null;
            }

            @Override
            public MojoExecution getMojoExecution() {
                return null;
            }

            @Override
            public Exception getException() {
                return null;
            }
        };
    }

    private JsonNode read(Path file) throws IOException {
        return objectMapper.readTree(Files.readAllBytes(file));
    }

    private JsonNode readFiles(Path file) throws IOException {
        return read(file).get("files");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
