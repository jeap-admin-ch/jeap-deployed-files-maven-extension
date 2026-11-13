# Format of `deployed-files.json`

Reference of the file the extension writes. For where it is written see the
[Configuration reference](configuration.md); for how to act on it see
[Use the list in a delivery pipeline](pipeline-how-to.md).

The file is one JSON object, encoded in UTF-8. Each file of the list is on a line of its own; the line breaks and
the order of the fields are not part of the format.

## Example

A snapshot of one module (`repositoryUrl` shortened to `…` after the first file, the values are otherwise as
written):

```json
{
  "formatVersion": 1,
  "mavenVersion": "3.9.16",
  "executionRoot": "/work/my-library",
  "buildResult": "SUCCESS",
  "writtenAt": "2026-11-13T09:36:36.921054310Z",
  "files": [
    {"kind": "artifact", "status": "deployed", "repositoryId": "snapshots", "repositoryUrl": "https://repository.example.org/snapshots/", "path": "org/example/my-library/2.0.0-SNAPSHOT/my-library-2.0.0-20261113.093636-2.pom", "groupId": "org.example", "artifactId": "my-library", "baseVersion": "2.0.0-SNAPSHOT", "version": "2.0.0-20261113.093636-2", "classifier": "", "extension": "pom", "size": 973, "sha1": "0ef070036714bd3e40e0cccc93f87d573791be44"},
    {"kind": "artifact", "status": "deployed", "repositoryId": "snapshots", "repositoryUrl": "…", "path": "org/example/my-library/2.0.0-SNAPSHOT/my-library-2.0.0-20261113.093636-2.jar", "groupId": "org.example", "artifactId": "my-library", "baseVersion": "2.0.0-SNAPSHOT", "version": "2.0.0-20261113.093636-2", "classifier": "", "extension": "jar", "size": 1769, "sha1": "3b37f2ef4d29442d633372afe07a3e39264f26df"},
    {"kind": "metadata", "status": "deployed", "repositoryId": "snapshots", "repositoryUrl": "…", "path": "org/example/my-library/2.0.0-SNAPSHOT/maven-metadata.xml", "groupId": "org.example", "artifactId": "my-library", "version": "2.0.0-SNAPSHOT", "type": "maven-metadata.xml", "nature": "SNAPSHOT", "size": 786, "sha1": "9ba033f6b0d58e0bd11bec4f61e2cb0b394c6eae"},
    {"kind": "metadata", "status": "deployed", "repositoryId": "snapshots", "repositoryUrl": "…", "path": "org/example/my-library/maven-metadata.xml", "groupId": "org.example", "artifactId": "my-library", "version": "", "type": "maven-metadata.xml", "nature": "RELEASE_OR_SNAPSHOT", "size": 296, "sha1": "e32d1541113fa2c1c2a88b0fa4fdd1fb916b29ae"}
  ]
}
```

A failed upload carries the error instead of counting as deployed:

```json
{"kind": "artifact", "status": "failed", "repositoryId": "releases", "repositoryUrl": "https://repository.example.org/releases/", "path": "org/example/my-library/1.4.0/my-library-1.4.0.pom", "groupId": "org.example", "artifactId": "my-library", "baseVersion": "1.4.0", "version": "1.4.0", "classifier": "", "extension": "pom", "size": 711, "sha1": "86102398a7ff6367a2648561bf701e26f0a1239a", "error": "Could not transfer artifact org.example:my-library:pom:1.4.0 from/to releases (https://repository.example.org/releases/): status code: 400, reason phrase: releases/org/example/my-library/1.4.0/my-library-1.4.0.pom cannot be updated (400)"}
```

## The document

A field that has no value is **left out**; it is never written as `null`.

| Field           | Type             | Present                               | Content                                                                                           |
|-----------------|------------------|---------------------------------------|---------------------------------------------------------------------------------------------------|
| `formatVersion` | integer          | always                                | `1`. See [Compatibility](#compatibility)                                                          |
| `mavenVersion`  | string           | when Maven reports its version        | The version of the Maven that ran the build, e.g. `3.9.16`                                        |
| `executionRoot` | string           | normally always                       | Absolute path of the directory of the top-level POM, on the machine that ran the build            |
| `buildResult`   | string           | when Maven reported a result          | `SUCCESS` or `FAILURE`: whether the whole build succeeded. Absent if Maven ended without a result |
| `writtenAt`     | string           | always                                | When the list was written: ISO 8601, UTC, e.g. `2026-11-13T09:36:36.921054310Z`                   |
| `files`         | array of objects | always, empty if nothing was deployed | One object per upload, see [A file](#a-file)                                                      |

`buildResult` is the result of the build, not of the deploy alone: a build that deploys everything and fails
afterwards has `FAILURE` and only files with the status `deployed`.

## A file

An object of `files` describes one upload of one file to a remote repository.

| Field           | Type    | Present              | Content                                                                                                                      |
|-----------------|---------|----------------------|------------------------------------------------------------------------------------------------------------------------------|
| `kind`          | string  | always               | `artifact` (a POM, jar, attached artifact or signature) or `metadata` (a `maven-metadata.xml`)                               |
| `status`        | string  | always               | `deployed`, `failed` or `incomplete`, see [Status](#status)                                                                  |
| `repositoryId`  | string  | always               | The id of the repository the file was deployed to, as in `distributionManagement`                                            |
| `repositoryUrl` | string  | always               | The URL of that repository exactly as configured: with or without a trailing slash                                           |
| `path`          | string  | always               | Path of the file relative to `repositoryUrl`, in the Maven 2 repository layout, without a leading slash                      |
| `groupId`       | string  | always               | The group id                                                                                                                 |
| `artifactId`    | string  | always               | The artifact id. Empty (`""`) for metadata on the group level                                                                |
| `baseVersion`   | string  | artifacts            | The version as declared in the POM, e.g. `1.2.0` or `2.0.0-SNAPSHOT`                                                         |
| `version`       | string  | always               | Artifacts: the version as deployed, see [Versions of snapshots](#versions-of-snapshots). Metadata: see [Metadata](#metadata) |
| `classifier`    | string  | artifacts            | The classifier, e.g. `sources`. Empty (`""`) for an artifact without one                                                     |
| `extension`     | string  | artifacts            | The file extension without the leading dot, e.g. `pom`, `jar`, `jar.asc`                                                     |
| `type`          | string  | metadata             | The file name of the metadata: `maven-metadata.xml`                                                                          |
| `nature`        | string  | metadata             | `RELEASE`, `SNAPSHOT` or `RELEASE_OR_SNAPSHOT`, see [Metadata](#metadata)                                                    |
| `size`          | integer | `deployed`, `failed` | Size of the uploaded file in bytes                                                                                           |
| `sha1`          | string  | `deployed`, `failed` | SHA-1 of the uploaded file: 40 lowercase hexadecimal characters                                                              |
| `error`         | string  | `failed`             | The message of the error that ended the upload; for an HTTP repository it contains the status code                           |

`size` and `sha1` are read from the local file the upload was made from, at the moment the upload ended. They are
absent in the rare case that this file can no longer be read.

The full URL of a file is `repositoryUrl`, a `/` if `repositoryUrl` does not end with one, and `path`.

### Status

| Status       | Meaning                                                                                                  |
|--------------|----------------------------------------------------------------------------------------------------------|
| `deployed`   | The upload succeeded, including the checksum files Maven uploads with the file                           |
| `failed`     | The upload ended with an error, given in `error`. The file is not on the repository, or not completely   |
| `incomplete` | The upload started and the build ended before it finished, for example because the build was interrupted |

### Versions of snapshots

For a snapshot, Maven replaces `SNAPSHOT` in the file names by a timestamp and a build number when it deploys.
The list has both forms:

- `baseVersion` is the version as declared: `2.0.0-SNAPSHOT`. It is the name of the version directory in `path`.
- `version` is the version as deployed: `2.0.0-20261113.093636-2`. It is part of the file name in `path`.

All files of one build share the same timestamp and build number. For a release, `version` and `baseVersion` are
equal.

### Metadata

Maven uploads the `maven-metadata.xml` files it has updated, and the list names them like any other file. `size`
and `sha1` are those of the merged file as uploaded.

| Level    | `path`                                                  | `artifactId` | `version`        | `nature`                                                      | Uploaded for                                                       |
|----------|---------------------------------------------------------|--------------|------------------|---------------------------------------------------------------|--------------------------------------------------------------------|
| version  | `<group>/<artifactId>/<baseVersion>/maven-metadata.xml` | set          | `2.0.0-SNAPSHOT` | `SNAPSHOT`                                                    | every snapshot module                                              |
| artifact | `<group>/<artifactId>/maven-metadata.xml`               | set          | `""`             | `RELEASE` for a release, `RELEASE_OR_SNAPSHOT` for a snapshot | every module                                                       |
| group    | `<group>/maven-metadata.xml`                            | `""`         | `""`             | as Maven reports it                                           | `maven-plugin` modules (not covered by the tests of the extension) |

## Order and repetitions

- The uploads that ended (`deployed` and `failed`) come first, in the order in which they ended. The `incomplete`
  ones follow. The order is not sorted by module or path, and with a parallel build it differs from run to run.
- A build normally uploads a path once. A path can still appear more than once, for example the group-level
  metadata that every `maven-plugin` module of a reactor updates. The last entry of a path is the content that is
  on the repository.
- A build can deploy to more than one repository. Each file names its own.

## What is not in the list

- **Checksum files** (`.md5`, `.sha1` and others). Maven uploads them as part of the upload of their file and
  reports no event of their own. They exist on the repository for every listed file, with one exception: Maven
  3.9.6 and newer upload no checksum files for signatures (`.asc`).
- **Modules that are not deployed**, for example with `maven.deploy.skip=true`.
- **Files uploaded by other means** than Maven's own deployment: a plugin that publishes with an HTTP client of its
  own produces no events.
- **Files installed** into the local repository, and anything Maven downloads.

## Compatibility

- `formatVersion` is `1`. It is raised only by a change that can break a reader: a field that is removed or
  renamed, a field whose meaning or type changes, a new `status` or `kind`.
- New fields can be added, on the document and on a file, without raising `formatVersion`.

## Related

- [jeap-deployed-files-maven-extension](../README.md)
- [Getting started](getting-started.md)
- [Use the list in a delivery pipeline](pipeline-how-to.md)
- [Configuration reference](configuration.md)
- [Architecture](architecture.md)
