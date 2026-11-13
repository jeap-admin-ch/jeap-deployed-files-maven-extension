package ch.admin.bit.jeap.maven.deployedfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeployedFilesJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void document_exactFormat() {
        DeployedFile artifact = new DeployedFile();
        artifact.kind = "artifact";
        artifact.status = "deployed";
        artifact.repositoryId = "releases";
        artifact.repositoryUrl = "https://repository.example.org/releases/";
        artifact.path = "org/example/a/1.0.0/a-1.0.0.jar";
        artifact.groupId = "org.example";
        artifact.artifactId = "a";
        artifact.baseVersion = "1.0.0";
        artifact.version = "1.0.0";
        artifact.classifier = "";
        artifact.extension = "jar";
        artifact.size = 2256L;
        artifact.sha1 = "64418cda3cc4aedaf94cdadd90a6830f672aab0a";
        DeployedFile metadata = new DeployedFile();
        metadata.kind = "metadata";
        metadata.status = "failed";
        metadata.repositoryId = "releases";
        metadata.repositoryUrl = "https://repository.example.org/releases/";
        metadata.path = "org/example/a/maven-metadata.xml";
        metadata.groupId = "org.example";
        metadata.artifactId = "a";
        metadata.version = "";
        metadata.type = "maven-metadata.xml";
        metadata.nature = "RELEASE_OR_SNAPSHOT";
        metadata.error = "status code: 403";

        String json = DeployedFilesJson.document("3.9.16", "/work/project", "FAILURE", "2026-01-01T00:00:00Z",
                List.of(artifact, metadata));

        assertThat(json).isEqualTo("""
                {
                  "formatVersion": 1,
                  "mavenVersion": "3.9.16",
                  "executionRoot": "/work/project",
                  "buildResult": "FAILURE",
                  "writtenAt": "2026-01-01T00:00:00Z",
                  "files": [
                    {"kind": "artifact", "status": "deployed", "repositoryId": "releases", "repositoryUrl": "https://repository.example.org/releases/", "path": "org/example/a/1.0.0/a-1.0.0.jar", "groupId": "org.example", "artifactId": "a", "baseVersion": "1.0.0", "version": "1.0.0", "classifier": "", "extension": "jar", "size": 2256, "sha1": "64418cda3cc4aedaf94cdadd90a6830f672aab0a"},
                    {"kind": "metadata", "status": "failed", "repositoryId": "releases", "repositoryUrl": "https://repository.example.org/releases/", "path": "org/example/a/maven-metadata.xml", "groupId": "org.example", "artifactId": "a", "version": "", "type": "maven-metadata.xml", "nature": "RELEASE_OR_SNAPSHOT", "error": "status code: 403"}
                  ]
                }
                """);
    }

    @Test
    void document_noFilesAndNoOptionalValues() throws Exception {
        String json = DeployedFilesJson.document(null, null, null, null, List.of());

        assertThat(json).isEqualTo("""
                {
                  "formatVersion": 1,
                  "files": []
                }
                """);
        assertThat(objectMapper.readTree(json).get("files")).isEmpty();
    }

    @Test
    void document_fileWithoutAnyValue() throws Exception {
        String json = DeployedFilesJson.document(null, null, null, null, List.of(new DeployedFile()));

        JsonNode file = objectMapper.readTree(json).get("files").get(0);
        assertThat(file.isObject()).isTrue();
        assertThat(file).isEmpty();
    }

    @Test
    void document_escapesStrings() throws Exception {
        DeployedFile file = new DeployedFile();
        String error = "quote \" backslash \\ newline \n return \r tab \t bell \u0007 umlaut ä emoji \uD83D\uDE00";
        file.error = error;
        String root = "C:\\work\\my \"project\"";

        String json = DeployedFilesJson.document(null, root, null, null, List.of(file));

        JsonNode document = objectMapper.readTree(json);
        assertThat(document.get("executionRoot").asText()).isEqualTo(root);
        assertThat(document.get("files").get(0).get("error").asText()).isEqualTo(error);
        assertThat(json).contains("\\u0007").doesNotContain("\u0007");
        // one line per file, whatever the values contain
        assertThat(json.lines()).hasSize(7);
    }

    @Test
    void document_sizeBeyondInt() throws Exception {
        DeployedFile file = new DeployedFile();
        file.size = 5_000_000_000L;

        String json = DeployedFilesJson.document(null, null, null, null, List.of(file));

        assertThat(objectMapper.readTree(json).get("files").get(0).get("size").asLong()).isEqualTo(5_000_000_000L);
    }
}
