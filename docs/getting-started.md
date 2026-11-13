# Getting started

This page shows how to deploy a Maven project with `jeap-deployed-files-maven-extension` loaded and how to find the
list of deployed files it writes. At the end you have a `target/deployed-files.json` that names every file your
build has uploaded.

You need Maven 3.9, Java 25 or newer as the JDK that Maven runs on, and a Maven project that you can deploy to a
repository.

## 1. Download the extension

In the directory of your project, copy the jar of the extension into the folder `.deployed-files-extension`:

```bash
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact=ch.admin.bit.jeap:jeap-deployed-files-maven-extension:1.0.0 \
    -DoutputDirectory=.deployed-files-extension \
    -Dmdep.stripVersion=true
```

The folder now contains `jeap-deployed-files-maven-extension.jar`.

## 2. Deploy with the extension

Run the deploy and pass the absolute path of the jar with `maven.ext.class.path`:

```bash
mvn clean deploy -Dmaven.ext.class.path="$PWD/.deployed-files-extension/jeap-deployed-files-maven-extension.jar"
```

The project itself is not changed. The last line of the build output is the one of the extension:

```
[INFO] [deployed-files] 3 deployed file(s) recorded in /work/my-library/target/deployed-files.json
```

## 3. Look at the list

Open `target/deployed-files.json`. For a release of a single module it has three files: the POM, the jar, and the
`maven-metadata.xml` Maven has updated.

```json
{
  "formatVersion": 1,
  "mavenVersion": "3.9.16",
  "executionRoot": "/work/my-library",
  "buildResult": "SUCCESS",
  "writtenAt": "2026-11-13T09:36:22.524477143Z",
  "files": [
    {"kind": "artifact", "status": "deployed", "repositoryId": "releases", "repositoryUrl": "https://repository.example.org/releases/", "path": "org/example/my-library/1.0.0/my-library-1.0.0.pom", "groupId": "org.example", "artifactId": "my-library", "baseVersion": "1.0.0", "version": "1.0.0", "classifier": "", "extension": "pom", "size": 972, "sha1": "4db1a1b84969ea731023a19e0f99a22eb0e295ec"},
    {"kind": "artifact", "status": "deployed", "repositoryId": "releases", "repositoryUrl": "https://repository.example.org/releases/", "path": "org/example/my-library/1.0.0/my-library-1.0.0.jar", "groupId": "org.example", "artifactId": "my-library", "baseVersion": "1.0.0", "version": "1.0.0", "classifier": "", "extension": "jar", "size": 1799, "sha1": "b61d87b6a277f5143d0a5d635e2003091face15e"},
    {"kind": "metadata", "status": "deployed", "repositoryId": "releases", "repositoryUrl": "https://repository.example.org/releases/", "path": "org/example/my-library/maven-metadata.xml", "groupId": "org.example", "artifactId": "my-library", "version": "", "type": "maven-metadata.xml", "nature": "RELEASE", "size": 321, "sha1": "d8c170f5c6e389400314af5318e574330ad49b46"}
  ]
}
```

Every file names the repository it went to, its path there, its size and its SHA-1. You now have the exact list of
what your build has published.

## Related

- [jeap-deployed-files-maven-extension](../README.md)
- [Use the list in a delivery pipeline](pipeline-how-to.md): the checks a pipeline needs around the build and before
  it acts on the list
- [Configuration reference](configuration.md): another output path, turning the extension off
- [Format of `deployed-files.json`](deployed-files-json.md): every field of the list
- [Architecture](architecture.md): how the extension sees what Maven deploys
