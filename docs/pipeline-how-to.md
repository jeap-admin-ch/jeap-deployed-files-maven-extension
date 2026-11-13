# Use the list in a delivery pipeline

This guide shows how to record the deployed files in a pipeline and act on the list in a later step, for example
to copy a release to a second repository. It assumes you have run the extension once by hand, as in
[Getting started](getting-started.md).

## Checklist

1. [Fetch the jar](#fetch-the-jar) and check that it is there.
2. [Remove a stale list](#remove-a-stale-list).
3. [Load the extension only in the Maven call that deploys](#load-the-extension-only-where-the-build-deploys).
4. [Check that the list exists](#check-that-the-list-exists) after the build.
5. [Validate the list](#validate-the-list) before acting on it.

## Fetch the jar

Fetch a pinned version, with the same Maven settings as the build:

```bash
mvn -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact=ch.admin.bit.jeap:jeap-deployed-files-maven-extension:1.0.0 \
    -DoutputDirectory="$WORKSPACE/.deployed-files-extension" \
    -Dmdep.stripVersion=true
test -f "$WORKSPACE/.deployed-files-extension/jeap-deployed-files-maven-extension.jar"
```

The check matters: Maven ignores an entry of `maven.ext.class.path` that does not exist. It prints no warning, the
build is green, and no list is written.

## Remove a stale list

If the workspace is reused, delete the list of an earlier build before the build, so that a list found afterwards
is the one of this build:

```bash
rm -f "$WORKSPACE/deployed-files.json"
```

This is not needed if the list stays at its default path below `target` and the build runs `clean`.

## Load the extension only where the build deploys

Add the two properties to the Maven call that runs `deploy`, with absolute paths:

```bash
mvn deploy \
    -Dmaven.ext.class.path="$WORKSPACE/.deployed-files-extension/jeap-deployed-files-maven-extension.jar" \
    -DdeployedFiles.output="$WORKSPACE/deployed-files.json"
```

- If the build runs in a container, use the paths as the container sees them.
- Other Maven calls of the pipeline (tests, code analysis) do not need the extension. If one of them gets it
  anyway, it overwrites the list with an empty one; give that call `-DdeployedFiles.skip=true` or leave the
  extension out.
- If one Maven call deploys to several repositories, the list has the files of all of them. Each file names its
  repository.

## Check that the list exists

The extension never fails the build, so a missing list is not a build error. Check for it:

```bash
test -f "$WORKSPACE/deployed-files.json" || echo "no list of deployed files: the extension did not run"
```

If the list is missing, look for a `[WARNING] [deployed-files]` line in the build log, and check the path of the
jar.

## Validate the list

Before a step acts on the list:

1. Refuse a `formatVersion` it does not know.
2. Require `buildResult` to be `SUCCESS`, unless the step handles partial deploys on purpose.
3. Require every file to have the status `deployed`.
4. If the build may deploy to several repositories, select the files by `repositoryUrl`.
5. Ignore fields it does not know: new fields can be added without a new `formatVersion`.

With `jq`, the first three checks are:

```bash
jq -e '.formatVersion == 1 and .buildResult == "SUCCESS" and all(.files[]; .status == "deployed")' \
    "$WORKSPACE/deployed-files.json"
```

## Download the listed files

The URL of a file is its `repositoryUrl`, a `/` if the URL does not end with one, and its `path`:

```bash
jq -r '.files[] | (.repositoryUrl | sub("/*$"; "/")) + .path' "$WORKSPACE/deployed-files.json"
```

- Check each download against `size` and `sha1` of the list.
- The checksum files (`.md5`, `.sha1`) are not in the list. If you need them, fetch them next to every listed file
  except signatures (`.asc`), which have none.
- For a snapshot, compare the `sha1` of the version-level `maven-metadata.xml` with the file on the repository: if
  they differ, a newer build of the same snapshot has been deployed since.

## Related

- [jeap-deployed-files-maven-extension](../README.md)
- [Getting started](getting-started.md)
- [Configuration reference](configuration.md)
- [Format of `deployed-files.json`](deployed-files-json.md)
