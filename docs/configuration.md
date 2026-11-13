# Configuration reference

All properties are Maven user properties (`-D<name>=<value>` on the command line or in `.mvn/maven.config`). For
the properties of the extension, a JVM system property of the same name is used when no user property is set.
Properties in a `pom.xml` or in `settings.xml` have no effect.

## Loading

| Property               | Default | Description                                                                                                                                                                         |
|------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maven.ext.class.path` | —       | Path of the extension jar. A property of Maven. A relative path is resolved against the directory Maven was started in. Maven ignores a path that does not exist, without a warning |

## Properties of the extension

| Property               | Default                      | Description                                                                                                         |
|------------------------|------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `deployedFiles.output` | `target/deployed-files.json` | The file the list is written to. Empty or blank means the default                                                   |
| `deployedFiles.skip`   | `false`                      | `true` (in any case) turns the extension off: nothing is recorded, written or logged. Any other value means `false` |

## Output path

| Value of `deployedFiles.output` | File written                                  |
|---------------------------------|-----------------------------------------------|
| not set                         | `<execution root>/target/deployed-files.json` |
| a relative path                 | `<execution root>/<path>`                     |
| an absolute path                | that path                                     |

- The execution root is the directory of the top-level POM of the build: the directory Maven was started in, or the
  directory of the POM given with `-f`.
- A multi-module build writes one list, for the whole reactor.
- Missing directories are created.
- An existing file is replaced.
- The list is written to `<output>.tmp` and then moved to `<output>`.

## When the list is written

The list is written once per Maven invocation, when Maven reports the result of the build.

| Build                                      | List                                                                    |
|--------------------------------------------|-------------------------------------------------------------------------|
| succeeds and deploys                       | written, `buildResult` is `SUCCESS`                                     |
| deploys nothing (for example `mvn verify`) | written, with an empty `files`                                          |
| fails                                      | written, `buildResult` is `FAILURE`, with the uploads up to the failure |
| runs with `deployedFiles.skip=true`        | not written; an existing file is left unchanged                         |

## Log output

| Level     | Line                                                                                                | When                                      |
|-----------|-----------------------------------------------------------------------------------------------------|-------------------------------------------|
| `INFO`    | `[deployed-files] <n> deployed file(s) recorded in <output>`                                        | the list was written                      |
| `INFO`    | `[deployed-files] <n> deployed file(s) and <m> failed or incomplete upload(s) recorded in <output>` | the list was written and has such uploads |
| `WARNING` | `[deployed-files] <what failed>, the list of deployed files may be missing or incomplete: <error>`  | the first error of the extension          |

An error of the extension never fails the build. Only the first error of a build is logged.

## Requirements

| Requirement | Value                                                      |
|-------------|------------------------------------------------------------|
| Maven       | 3.9 (the integration tests of the extension run on 3.9.16) |
| Java        | 25 or newer, for the JDK that runs the Maven build         |

## Related

- [jeap-deployed-files-maven-extension](../README.md)
- [Getting started](getting-started.md)
- [Use the list in a delivery pipeline](pipeline-how-to.md)
- [Format of `deployed-files.json`](deployed-files-json.md)
- [Architecture](architecture.md)
