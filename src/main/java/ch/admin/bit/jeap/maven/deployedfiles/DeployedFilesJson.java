package ch.admin.bit.jeap.maven.deployedfiles;

import java.util.List;

/**
 * Writes the list as a JSON document. Written by hand, because the extension must not bring dependencies of its own
 * into the Maven that loads it. The format is described in {@code docs/deployed-files-json.md}; readers depend on it.
 */
final class DeployedFilesJson {

    /** Raised only for a change that breaks readers; added fields do not raise it. */
    static final int FORMAT_VERSION = 1;

    private DeployedFilesJson() {
    }

    /** Values that are {@code null} are left out; one line per file. */
    static String document(String mavenVersion, String executionRoot, String buildResult, String writtenAt,
                           List<DeployedFile> files) {
        StringBuilder json = new StringBuilder(256 + files.size() * 512);
        json.append("{\n");
        json.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n");
        topLevelField(json, "mavenVersion", mavenVersion);
        topLevelField(json, "executionRoot", executionRoot);
        topLevelField(json, "buildResult", buildResult);
        topLevelField(json, "writtenAt", writtenAt);
        json.append("  \"files\": [");
        for (int i = 0; i < files.size(); i++) {
            json.append(i == 0 ? "\n    " : ",\n    ");
            file(json, files.get(i));
        }
        json.append(files.isEmpty() ? "]\n" : "\n  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void topLevelField(StringBuilder json, String name, String value) {
        if (value != null) {
            json.append("  ");
            string(json, name);
            json.append(": ");
            string(json, value);
            json.append(",\n");
        }
    }

    private static void file(StringBuilder json, DeployedFile file) {
        int start = json.length();
        json.append('{');
        field(json, start, "kind", file.kind);
        field(json, start, "status", file.status);
        field(json, start, "repositoryId", file.repositoryId);
        field(json, start, "repositoryUrl", file.repositoryUrl);
        field(json, start, "path", file.path);
        field(json, start, "groupId", file.groupId);
        field(json, start, "artifactId", file.artifactId);
        field(json, start, "baseVersion", file.baseVersion);
        field(json, start, "version", file.version);
        field(json, start, "classifier", file.classifier);
        field(json, start, "extension", file.extension);
        field(json, start, "type", file.type);
        field(json, start, "nature", file.nature);
        if (file.size != null) {
            name(json, start, "size");
            json.append(file.size.longValue());
        }
        field(json, start, "sha1", file.sha1);
        field(json, start, "error", file.error);
        json.append('}');
    }

    private static void field(StringBuilder json, int objectStart, String name, String value) {
        if (value != null) {
            name(json, objectStart, name);
            string(json, value);
        }
    }

    private static void name(StringBuilder json, int objectStart, String name) {
        if (json.length() > objectStart + 1) {
            json.append(", ");
        }
        string(json, name);
        json.append(": ");
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}
