package ch.admin.bit.jeap.maven.deployedfiles;

import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;

import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.artifact;
import static ch.admin.bit.jeap.maven.deployedfiles.Fixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;

class LayoutTest {

    @Test
    void artifactPath_release() {
        assertThat(Layout.artifactPath(artifact("org.example.lib:module-a:jar:1.2.0", null)))
                .isEqualTo("org/example/lib/module-a/1.2.0/module-a-1.2.0.jar");
    }

    @Test
    void artifactPath_withClassifier() {
        assertThat(Layout.artifactPath(artifact("org.example.lib:module-a:jar:sources:1.2.0", null)))
                .isEqualTo("org/example/lib/module-a/1.2.0/module-a-1.2.0-sources.jar");
    }

    @Test
    void artifactPath_signature() {
        assertThat(Layout.artifactPath(artifact("org.example.lib:module-a:jar.asc:1.2.0", null)))
                .isEqualTo("org/example/lib/module-a/1.2.0/module-a-1.2.0.jar.asc");
    }

    @Test
    void artifactPath_snapshot_directoryOfTheBaseVersionAndTimestampedFileName() {
        assertThat(Layout.artifactPath(artifact("org.example.lib:module-a:pom:2.0.0-20261113.074028-1", null)))
                .isEqualTo("org/example/lib/module-a/2.0.0-SNAPSHOT/module-a-2.0.0-20261113.074028-1.pom");
    }

    @Test
    void metadataPath_versionLevel() {
        Metadata metadata = metadata("org.example.lib", "module-a", "2.0.0-SNAPSHOT", Metadata.Nature.SNAPSHOT, null);

        assertThat(Layout.metadataPath(metadata))
                .isEqualTo("org/example/lib/module-a/2.0.0-SNAPSHOT/maven-metadata.xml");
    }

    @Test
    void metadataPath_artifactLevel() {
        Metadata metadata = metadata("org.example.lib", "module-a", "", Metadata.Nature.RELEASE_OR_SNAPSHOT, null);

        assertThat(Layout.metadataPath(metadata)).isEqualTo("org/example/lib/module-a/maven-metadata.xml");
    }

    @Test
    void metadataPath_groupLevel() {
        Metadata metadata = metadata("org.example.lib", "", "", Metadata.Nature.RELEASE_OR_SNAPSHOT, null);

        assertThat(Layout.metadataPath(metadata)).isEqualTo("org/example/lib/maven-metadata.xml");
    }
}
