package ch.admin.bit.jeap.maven.deployedfiles;

import org.eclipse.aether.RepositoryEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the deploy events of a build. Thread-safe: a parallel build ({@code -T}) deploys from several threads.
 */
final class DeployedFilesRecorder {

    /** ended uploads, successful or failed, in the order they ended */
    private final List<DeployedFile> ended = new ArrayList<>();
    /** started uploads that have not ended yet, by repository URL and path, in the order they started */
    private final Map<String, DeployedFile> started = new LinkedHashMap<>();

    /** Records the event if it starts or ends an upload; every other repository event is ignored. */
    void record(RepositoryEvent event) {
        switch (event.getType()) {
            case ARTIFACT_DEPLOYING, METADATA_DEPLOYING -> {
                DeployedFile file = DeployedFile.of(event, DeployedFile.STATUS_INCOMPLETE, false);
                if (file == null) {
                    return;
                }
                synchronized (this) {
                    started.put(file.key(), file);
                }
            }
            case ARTIFACT_DEPLOYED, METADATA_DEPLOYED -> {
                // an event with an exception is a failed upload, not a deployed file
                String status = event.getException() == null
                        ? DeployedFile.STATUS_DEPLOYED : DeployedFile.STATUS_FAILED;
                // reads and hashes the file outside the lock, so that parallel uploads do not wait for one another
                DeployedFile file = DeployedFile.of(event, status, true);
                if (file == null) {
                    return;
                }
                synchronized (this) {
                    started.remove(file.key());
                    ended.add(file);
                }
            }
            default -> {
                // resolution, installation and the other repository events are not of interest
            }
        }
    }

    /** @return the ended uploads in the order they ended, followed by the uploads that never ended */
    synchronized List<DeployedFile> files() {
        List<DeployedFile> files = new ArrayList<>(ended.size() + started.size());
        files.addAll(ended);
        files.addAll(started.values());
        return files;
    }
}
