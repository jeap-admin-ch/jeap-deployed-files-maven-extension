package ch.admin.bit.jeap.maven.deployedfiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs a real Maven build of one of the test projects, with the packaged extension loaded, for the integration
 * tests.
 * <p>
 * The build is started as a process of the Maven that runs this build ({@code maven.home}): that is exactly how
 * the extension is used, it needs no library, and it runs the same Maven version as the wrapper. It gets the
 * settings, the local repository and the proxy configuration of this build, so that it resolves its plugins the
 * same way, also on a build agent where these are given on the command line.
 */
final class MavenBuild {

    /** Set by the maven-failsafe-plugin, see the pom. */
    private static final String EXTENSION_JAR = "it.extensionJar";
    private static final String MAVEN_HOME = "it.mavenHome";
    private static final String USER_SETTINGS = "it.userSettings";
    private static final String GLOBAL_SETTINGS = "it.globalSettings";
    private static final String LOCAL_REPOSITORY = "it.localRepository";

    /** Given to the invoked build as they are, when this build has them. */
    private static final List<String> FORWARDED_PROPERTIES = List.of(
            "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts",
            "http.proxyUser", "http.proxyPassword", "https.proxyUser", "https.proxyPassword",
            "it.maven-source-plugin.version", "it.maven-deploy-plugin.version");

    private static final long TIMEOUT_MINUTES = 10;

    private final Path projectDirectory;
    private int builds;

    private MavenBuild(Path projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    /**
     * Copies the test project into the given empty directory, so that every test starts without the files, the
     * deployment repository and the list of an earlier run.
     */
    static MavenBuild ofProject(String name, Path emptyDirectory) throws IOException, URISyntaxException {
        URL resource = MavenBuild.class.getResource("/it-projects/" + name);
        if (resource == null) {
            throw new IllegalArgumentException("no test project " + name);
        }
        Path source = Path.of(resource.toURI());
        Path project = emptyDirectory.toRealPath().resolve(name);
        try (Stream<Path> files = Files.walk(source)) {
            files.forEach(file -> copy(file, project.resolve(source.relativize(file).toString())));
        }
        // marks the root of the project, so that Maven does not look for one in the directories above
        Files.createDirectories(project.resolve(".mvn"));
        return new MavenBuild(project);
    }

    Path projectDirectory() {
        return projectDirectory;
    }

    /** The deployment repository of the test projects, see their poms. */
    Path repository(String name) {
        return projectDirectory.resolve("repository").resolve(name);
    }

    /** Runs {@code mvn deploy} with the extension and the given additional arguments. */
    Result deploy(String... arguments) throws IOException, InterruptedException {
        Path extensionJar = Path.of(requiredProperty(EXTENSION_JAR));
        if (!Files.isRegularFile(extensionJar)) {
            // Maven would ignore the missing jar silently
            throw new IllegalStateException("the extension jar is missing: " + extensionJar);
        }
        List<String> command = new ArrayList<>();
        command.add(mavenExecutable().toString());
        command.add("--batch-mode");
        command.add("--no-transfer-progress");
        addFileOption(command, "--settings", System.getProperty(USER_SETTINGS));
        addFileOption(command, "--global-settings", System.getProperty(GLOBAL_SETTINGS));
        String localRepository = System.getProperty(LOCAL_REPOSITORY);
        if (isSet(localRepository)) {
            command.add("-Dmaven.repo.local=" + localRepository);
        }
        for (String property : FORWARDED_PROPERTIES) {
            String value = System.getProperty(property);
            if (isSet(value)) {
                command.add("-D" + property + "=" + value);
            }
        }
        command.add("-Dmaven.ext.class.path=" + extensionJar);
        command.addAll(List.of(arguments));
        command.add("deploy");

        Path log = projectDirectory.resolve("build-" + ++builds + ".log");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        // the invoked Maven runs on the JDK of this build and finds its own project directory
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().remove("MAVEN_BASEDIR");
        builder.environment().remove("MAVEN_PROJECTBASEDIR");
        builder.environment().remove("MAVEN_ARGS");
        builder.environment().remove("MAVEN_OPTS");
        Process process = builder.start();
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("the Maven build did not end within " + TIMEOUT_MINUTES
                    + " minutes:\n" + Files.readString(log, StandardCharsets.UTF_8));
        }
        return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }

    private static Path mavenExecutable() {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        Path executable = Path.of(requiredProperty(MAVEN_HOME), "bin", windows ? "mvn.cmd" : "mvn");
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("no Maven at " + executable);
        }
        return executable;
    }

    private static void addFileOption(List<String> command, String option, String file) {
        // a build without a settings file still has the path of the default one, which need not exist
        if (isSet(file) && Files.isRegularFile(Path.of(file))) {
            command.add(option);
            command.add(file);
        }
    }

    /** A property the pom could not resolve arrives as its unresolved expression. */
    private static boolean isSet(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (!isSet(value)) {
            throw new IllegalStateException("The system property " + name + " is not set. The integration tests "
                    + "are run by the maven-failsafe-plugin (./mvnw verify), which sets it.");
        }
        return value;
    }

    private static void copy(Path source, Path target) {
        try {
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The exit code and the complete output of a build. */
    record Result(int exitCode, String log) {

        boolean succeeded() {
            return exitCode == 0;
        }
    }
}
