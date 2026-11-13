package ch.admin.bit.jeap.maven.deployedfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deploys real Maven projects to a file repository with the packaged extension loaded, and compares the list the
 * extension has written with the files in that repository.
 */
class DeployedFilesExtensionIT {

    private static final String GROUP_PATH = "org/example/deployedfiles/it";
    private static final String LIST = "target/deployed-files.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path directory;

    /**
     * A multi-module release as a jEAP library deploys it: the whole reactor at the end of the build, one module
     * with a sources jar, one module that is not deployed.
     */
    @Test
    void releaseReactor_listEqualsTheRepository() throws Exception {
        MavenBuild build = MavenBuild.ofProject("release-reactor", directory);

        MavenBuild.Result result = build.deploy();

        assertThat(result.succeeded()).as(result.log()).isTrue();
        Path repository = build.repository("releases");
        JsonNode list = readList(build.projectDirectory().resolve(LIST), "SUCCESS");
        assertThat(Path.of(list.get("executionRoot").asText()).toRealPath()).isEqualTo(build.projectDirectory());
        assertListEqualsRepository(list, repository, "it-releases");
        assertThat(paths(list)).containsExactlyInAnyOrder(
                GROUP_PATH + "/release-reactor/1.2.0/release-reactor-1.2.0.pom",
                GROUP_PATH + "/release-reactor/maven-metadata.xml",
                GROUP_PATH + "/module-a/1.2.0/module-a-1.2.0.pom",
                GROUP_PATH + "/module-a/1.2.0/module-a-1.2.0.jar",
                GROUP_PATH + "/module-a/1.2.0/module-a-1.2.0-sources.jar",
                GROUP_PATH + "/module-a/maven-metadata.xml",
                GROUP_PATH + "/module-b/1.2.0/module-b-1.2.0.pom",
                GROUP_PATH + "/module-b/1.2.0/module-b-1.2.0.jar",
                GROUP_PATH + "/module-b/maven-metadata.xml");

        // the module with maven.deploy.skip was built, but is not in the list
        assertThat(build.projectDirectory().resolve("module-c/target/module-c-1.2.0.jar")).isRegularFile();
        assertThat(files(list)).noneMatch(file -> "module-c".equals(text(file, "artifactId")));

        JsonNode sources = files(list).stream()
                .filter(file -> text(file, "path").endsWith("-sources.jar")).findFirst().orElseThrow();
        assertThat(text(sources, "kind")).isEqualTo("artifact");
        assertThat(text(sources, "groupId")).isEqualTo("org.example.deployedfiles.it");
        assertThat(text(sources, "artifactId")).isEqualTo("module-a");
        assertThat(text(sources, "version")).isEqualTo("1.2.0");
        assertThat(text(sources, "baseVersion")).isEqualTo("1.2.0");
        assertThat(text(sources, "classifier")).isEqualTo("sources");
        assertThat(text(sources, "extension")).isEqualTo("jar");

        List<JsonNode> poms = files(list).stream().filter(file -> "pom".equals(file.path("extension").asText())).toList();
        assertThat(poms).hasSize(3).allMatch(pom -> text(pom, "classifier").isEmpty());

        // a release has one metadata file per module, on the artifact level
        List<JsonNode> metadata = files(list).stream().filter(file -> "metadata".equals(text(file, "kind"))).toList();
        assertThat(metadata).hasSize(3);
        for (JsonNode file : metadata) {
            assertThat(text(file, "type")).isEqualTo("maven-metadata.xml");
            assertThat(text(file, "nature")).isEqualTo("RELEASE");
            assertThat(text(file, "version")).isEmpty();
            assertThat(file.has("baseVersion")).isFalse();
            assertThat(file.has("classifier")).isFalse();
            assertThat(file.has("extension")).isFalse();
        }
    }

    /** A snapshot, deployed twice: the list of the second build names the files of the second build. */
    @Test
    void snapshotDeployedTwice_listNamesTheTimestampedFilesOfTheSecondBuild() throws Exception {
        MavenBuild build = MavenBuild.ofProject("snapshot", directory);
        String versionDirectory = GROUP_PATH + "/snapshot/2.0.0-SNAPSHOT";

        MavenBuild.Result first = build.deploy();
        MavenBuild.Result second = build.deploy();

        assertThat(first.succeeded()).as(first.log()).isTrue();
        assertThat(second.succeeded()).as(second.log()).isTrue();
        Path repository = build.repository("snapshots");
        JsonNode list = readList(build.projectDirectory().resolve(LIST), "SUCCESS");
        assertThat(files(list)).hasSize(4);

        List<JsonNode> artifacts = files(list).stream().filter(file -> "artifact".equals(text(file, "kind"))).toList();
        assertThat(artifacts).extracting(file -> text(file, "extension")).containsExactlyInAnyOrder("pom", "jar");
        for (JsonNode file : artifacts) {
            assertThat(text(file, "status")).isEqualTo("deployed");
            assertThat(text(file, "repositoryId")).isEqualTo("it-snapshots");
            assertThat(text(file, "repositoryUrl")).isEqualTo("file://" + repository);
            assertThat(text(file, "baseVersion")).isEqualTo("2.0.0-SNAPSHOT");
            // the version as deployed: timestamp and build number instead of SNAPSHOT
            assertThat(text(file, "version")).matches("2\\.0\\.0-\\d{8}\\.\\d{6}-2");
            assertThat(text(file, "path")).isEqualTo(
                    versionDirectory + "/snapshot-" + text(file, "version") + "." + text(file, "extension"));
            Path deployed = repository.resolve(text(file, "path"));
            assertThat(deployed).isRegularFile();
            assertThat(file.get("size").asLong()).isEqualTo(Files.size(deployed));
            assertThat(text(file, "sha1")).isEqualTo(DeployedFile.sha1(deployed));
        }
        // one timestamp for all files of a build
        assertThat(artifacts).extracting(file -> text(file, "version")).containsOnly(text(artifacts.get(0), "version"));
        String version = text(artifacts.get(0), "version");

        JsonNode versionLevel = file(list, versionDirectory + "/maven-metadata.xml");
        assertThat(text(versionLevel, "kind")).isEqualTo("metadata");
        assertThat(text(versionLevel, "nature")).isEqualTo("SNAPSHOT");
        assertThat(text(versionLevel, "version")).isEqualTo("2.0.0-SNAPSHOT");
        JsonNode artifactLevel = file(list, GROUP_PATH + "/snapshot/maven-metadata.xml");
        assertThat(text(artifactLevel, "kind")).isEqualTo("metadata");
        assertThat(text(artifactLevel, "nature")).isEqualTo("RELEASE_OR_SNAPSHOT");
        assertThat(text(artifactLevel, "version")).isEmpty();

        // the recorded metadata is the file in the repository, and it points at the recorded build
        for (JsonNode metadata : List.of(versionLevel, artifactLevel)) {
            Path deployed = repository.resolve(text(metadata, "path"));
            assertThat(text(metadata, "sha1")).isEqualTo(DeployedFile.sha1(deployed));
            assertThat(metadata.get("size").asLong()).isEqualTo(Files.size(deployed));
        }
        Document snapshotMetadata = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(repository.resolve(text(versionLevel, "path")).toFile());
        String buildNumber = snapshotMetadata.getElementsByTagName("buildNumber").item(0).getTextContent();
        String timestamp = snapshotMetadata.getElementsByTagName("timestamp").item(0).getTextContent();
        assertThat(buildNumber).isEqualTo("2");
        assertThat(version).isEqualTo("2.0.0-" + timestamp + "-2");

        // the files of the first build are still in the repository and not in the list of the second build
        List<String> firstBuild = repositoryPaths(repository).stream()
                .filter(path -> path.matches(".*-1\\.(pom|jar)")).toList();
        assertThat(firstBuild).hasSize(2);
        assertThat(paths(list)).doesNotContainAnyElementsOf(firstBuild);
    }

    /** The repository is below a regular file, so no file can be written: the build fails, the list is written. */
    @Test
    void failedDeploy_listHasTheFailedUploadsAndTheBuildResult() throws Exception {
        MavenBuild build = MavenBuild.ofProject("failed-deploy", directory);

        MavenBuild.Result result = build.deploy();

        assertThat(result.succeeded()).as(result.log()).isFalse();
        JsonNode list = readList(build.projectDirectory().resolve(LIST), "FAILURE");
        assertThat(files(list)).isNotEmpty();
        assertThat(files(list)).allMatch(file -> Set.of("failed", "incomplete").contains(text(file, "status")));
        List<JsonNode> failed = files(list).stream().filter(file -> "failed".equals(text(file, "status"))).toList();
        assertThat(failed).isNotEmpty();
        for (JsonNode file : failed) {
            assertThat(text(file, "repositoryId")).isEqualTo("it-releases");
            assertThat(text(file, "repositoryUrl")).endsWith("/pom.xml/releases");
            assertThat(text(file, "path")).startsWith(GROUP_PATH + "/failed-deploy/");
            assertThat(text(file, "error")).isNotBlank();
        }
        assertThat(failed).anyMatch(file -> "artifact".equals(text(file, "kind"))
                && "failed-deploy".equals(text(file, "artifactId")) && "1.0.0".equals(text(file, "version")));

        // the failure is that of the deploy, not one of the extension
        assertThat(result.log()).contains("BUILD FAILURE").contains("[deployed-files]")
                .doesNotContain("the list of deployed files may be missing or incomplete");
    }

    @Test
    void customOutput_listIsWrittenToTheGivenPathRelativeToTheExecutionRoot() throws Exception {
        MavenBuild build = MavenBuild.ofProject("custom-output", directory);

        MavenBuild.Result result = build.deploy("-DdeployedFiles.output=out/files.json");

        assertThat(result.succeeded()).as(result.log()).isTrue();
        JsonNode list = readList(build.projectDirectory().resolve("out/files.json"), "SUCCESS");
        assertThat(build.projectDirectory().resolve(LIST)).doesNotExist();
        assertListEqualsRepository(list, build.repository("releases"), "it-releases");
        assertThat(files(list)).hasSize(3);
    }

    @Test
    void skip_buildDeploysAndNothingIsWrittenOrLogged() throws Exception {
        MavenBuild build = MavenBuild.ofProject("skip", directory);

        MavenBuild.Result result = build.deploy("-DdeployedFiles.skip=true");

        assertThat(result.succeeded()).as(result.log()).isTrue();
        assertThat(repositoryPaths(build.repository("releases"))).hasSize(3);
        try (Stream<Path> files = Files.walk(build.projectDirectory())) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".json") || name.endsWith(".tmp"));
        }
        assertThat(result.log()).contains("BUILD SUCCESS").doesNotContain("[deployed-files]");
    }

    // --- helpers ---

    /** Parses the list with a JSON parser and checks the fields of the document itself. */
    private JsonNode readList(Path file, String expectedBuildResult) throws IOException {
        assertThat(file).isRegularFile();
        JsonNode list = objectMapper.readTree(Files.readAllBytes(file));
        assertThat(list.get("formatVersion").isIntegralNumber()).isTrue();
        assertThat(list.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(text(list, "buildResult")).isEqualTo(expectedBuildResult);
        assertThat(text(list, "mavenVersion")).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(Path.of(text(list, "executionRoot"))).isDirectory();
        assertThat(text(list, "writtenAt")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        assertThat(list.get("files").isArray()).isTrue();
        assertThat(file.resolveSibling(file.getFileName() + ".tmp")).doesNotExist();
        return list;
    }

    /**
     * Every entry of the list is a deployed file that is in the repository with the recorded size and sha1, and
     * the repository holds no other file: nothing missing, nothing extra.
     */
    private static void assertListEqualsRepository(JsonNode list, Path repository, String repositoryId)
            throws IOException {
        for (JsonNode file : files(list)) {
            String path = text(file, "path");
            assertThat(text(file, "status")).isEqualTo("deployed");
            assertThat(text(file, "repositoryId")).isEqualTo(repositoryId);
            assertThat(text(file, "repositoryUrl")).isEqualTo("file://" + repository);
            assertThat(file.has("error")).isFalse();
            Path deployed = repository.resolve(path);
            assertThat(deployed).as("%s is in the list, but not in the repository", path).isRegularFile();
            assertThat(file.get("size").asLong()).isEqualTo(Files.size(deployed));
            assertThat(text(file, "sha1")).isEqualTo(DeployedFile.sha1(deployed));
            // the checksum files are not in the list, but every listed file has them
            assertThat(isChecksum(path)).isFalse();
            assertThat(Files.readString(repository.resolve(path + ".sha1")).trim()).isEqualTo(text(file, "sha1"));
            assertThat(repository.resolve(path + ".md5")).isRegularFile();
        }
        assertThat(paths(list)).doesNotHaveDuplicates();
        assertThat(new TreeSet<>(paths(list))).isEqualTo(repositoryPaths(repository));
    }

    /** The paths of all files below the repository, relative to it, without the checksum files. */
    private static TreeSet<String> repositoryPaths(Path repository) throws IOException {
        TreeSet<String> paths = new TreeSet<>();
        try (Stream<Path> files = Files.walk(repository)) {
            files.filter(Files::isRegularFile)
                    .map(file -> repository.relativize(file).toString().replace('\\', '/'))
                    .filter(path -> !isChecksum(path))
                    .forEach(paths::add);
        }
        return paths;
    }

    private static boolean isChecksum(String path) {
        return path.endsWith(".md5") || path.endsWith(".sha1");
    }

    private static List<JsonNode> files(JsonNode list) {
        List<JsonNode> files = new ArrayList<>();
        list.get("files").forEach(files::add);
        return files;
    }

    private static List<String> paths(JsonNode list) {
        return files(list).stream().map(file -> text(file, "path")).toList();
    }

    private static JsonNode file(JsonNode list, String path) {
        return files(list).stream().filter(file -> path.equals(text(file, "path"))).findFirst().orElseThrow();
    }

    private static String text(JsonNode node, String field) {
        assertThat(node.has(field)).as("field %s of %s", field, node).isTrue();
        return node.get(field).asText();
    }
}
