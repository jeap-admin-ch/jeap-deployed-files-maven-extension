package ch.admin.bit.jeap.maven.deployedfiles;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * One file of the list: an upload that succeeded, failed, or started and never finished. Fields that are
 * {@code null} are left out of the JSON document.
 */
final class DeployedFile {

    static final String KIND_ARTIFACT = "artifact";
    static final String KIND_METADATA = "metadata";

    static final String STATUS_DEPLOYED = "deployed";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_INCOMPLETE = "incomplete";

    String kind;
    String status;
    String repositoryId;
    String repositoryUrl;
    /** relative to the repository URL, in the Maven 2 repository layout */
    String path;
    String groupId;
    String artifactId;
    /** artifacts only: the version as declared, e.g. {@code 1.0.0-SNAPSHOT} */
    String baseVersion;
    /** artifacts: the version as deployed, timestamped for a snapshot; metadata: its version, empty if it has none */
    String version;
    String classifier;
    String extension;
    /** metadata only: the file name, {@code maven-metadata.xml} */
    String type;
    /** metadata only: RELEASE, SNAPSHOT or RELEASE_OR_SNAPSHOT */
    String nature;
    Long size;
    String sha1;
    String error;

    /** Identifies an upload between its start and its end. */
    String key() {
        return repositoryUrl + "|" + path;
    }

    /**
     * Describes the file a deploy event is about.
     *
     * @return the file, or {@code null} for an event that names neither an artifact nor metadata
     * @param withContent whether to read size and sha1 from the local file. Done when the upload has ended and not
     *                    when the list is written, because the metadata files in the local repository can change
     *                    again later in the build.
     */
    static DeployedFile of(RepositoryEvent event, String status, boolean withContent) {
        DeployedFile file = new DeployedFile();
        file.status = status;
        ArtifactRepository repository = event.getRepository();
        if (repository instanceof RemoteRepository remote) {
            file.repositoryId = remote.getId();
            file.repositoryUrl = remote.getUrl();
        } else if (repository != null) {
            file.repositoryId = repository.getId();
        }
        Artifact artifact = event.getArtifact();
        Metadata metadata = event.getMetadata();
        if (artifact != null) {
            file.kind = KIND_ARTIFACT;
            file.groupId = artifact.getGroupId();
            file.artifactId = artifact.getArtifactId();
            file.baseVersion = artifact.getBaseVersion();
            file.version = artifact.getVersion();
            file.classifier = artifact.getClassifier();
            file.extension = artifact.getExtension();
            file.path = Layout.artifactPath(artifact);
        } else if (metadata != null) {
            file.kind = KIND_METADATA;
            file.groupId = metadata.getGroupId();
            file.artifactId = metadata.getArtifactId();
            file.version = metadata.getVersion();
            file.type = metadata.getType();
            file.nature = metadata.getNature() == null ? null : metadata.getNature().name();
            file.path = Layout.metadataPath(metadata);
        } else {
            return null;
        }
        if (event.getException() != null) {
            file.error = String.valueOf(event.getException().getMessage());
        }
        if (withContent) {
            File local = localFile(event, artifact, metadata);
            if (local != null && local.isFile()) {
                file.size = local.length();
                file.sha1 = sha1(local.toPath());
            }
        }
        return file;
    }

    /**
     * The deprecated accessors are the only ones that exist in every supported Maven: Maven 3.9 has no
     * {@code getPath()}.
     */
    @SuppressWarnings("deprecation")
    private static File localFile(RepositoryEvent event, Artifact artifact, Metadata metadata) {
        if (event.getFile() != null) {
            return event.getFile();
        }
        if (artifact != null) {
            return artifact.getFile();
        }
        return metadata == null ? null : metadata.getFile();
    }

    /** @return the sha1 of the file as lowercase hex, or {@code null} if it cannot be read */
    static String sha1(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(40);
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
