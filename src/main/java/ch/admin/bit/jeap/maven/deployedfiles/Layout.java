package ch.admin.bit.jeap.maven.deployedfiles;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/** Repository-relative paths in the Maven 2 layout, as the resolver's Maven2RepositoryLayout builds them. */
final class Layout {

    private Layout() {
    }

    static String artifactPath(Artifact artifact) {
        StringBuilder path = new StringBuilder(128);
        path.append(artifact.getGroupId().replace('.', '/')).append('/');
        path.append(artifact.getArtifactId()).append('/');
        path.append(artifact.getBaseVersion()).append('/');
        path.append(artifact.getArtifactId()).append('-').append(artifact.getVersion());
        if (!artifact.getClassifier().isEmpty()) {
            path.append('-').append(artifact.getClassifier());
        }
        if (!artifact.getExtension().isEmpty()) {
            path.append('.').append(artifact.getExtension());
        }
        return path.toString();
    }

    static String metadataPath(Metadata metadata) {
        StringBuilder path = new StringBuilder(128);
        if (!metadata.getGroupId().isEmpty()) {
            path.append(metadata.getGroupId().replace('.', '/')).append('/');
            if (!metadata.getArtifactId().isEmpty()) {
                path.append(metadata.getArtifactId()).append('/');
                if (!metadata.getVersion().isEmpty()) {
                    path.append(metadata.getVersion()).append('/');
                }
            }
        }
        path.append(metadata.getType());
        return path.toString();
    }
}
