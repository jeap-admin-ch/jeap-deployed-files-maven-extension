# jeap-deployed-files-maven-extension

`jeap-deployed-files-maven-extension` is a Maven core extension that records every file a build deploys to a
remote repository and writes the list as a JSON file when the build ends. A later step of a delivery pipeline can
then work with exactly the files the build has published, for example to copy them to another repository, without
having to guess them from module coordinates or a repository search.

* Records what Maven really uploaded: jars, POMs, attached artifacts such as `-sources` and `-javadoc`, signatures,
  and the `maven-metadata.xml` files, each with its repository, path, size and SHA-1
* Snapshots are listed under their timestamped file names; modules that are not deployed are absent
* Failed and unfinished uploads are listed as such, so a partial deploy stays traceable
* Loaded with `-Dmaven.ext.class.path`: the projects that are built need no change
* Never fails the build and adds no measurable build time; the jar has no dependencies beyond Maven itself

## Documentation

Start with [Getting started](docs/getting-started.md), then follow the links below.

| Topic                                                                     | Kind         | File                                                       |
|---------------------------------------------------------------------------|--------------|------------------------------------------------------------|
| Getting started: deploy with the extension and find the list              | Tutorial     | [docs/getting-started.md](docs/getting-started.md)         |
| Use the list in a delivery pipeline                                       | How-to guide | [docs/pipeline-how-to.md](docs/pipeline-how-to.md)         |
| Configuration: user properties, output path, log output                   | Reference    | [docs/configuration.md](docs/configuration.md)             |
| Format of `deployed-files.json`                                           | Reference    | [docs/deployed-files-json.md](docs/deployed-files-json.md) |
| Architecture: how the extension sees what Maven deploys, design decisions | Explanation  | [docs/architecture.md](docs/architecture.md)               |

## Modules

Group id of the extension is `ch.admin.bit.jeap`.

| Module                                | Purpose                                                                     |
|---------------------------------------|-----------------------------------------------------------------------------|
| `jeap-deployed-files-maven-extension` | Maven core extension that writes the list of the files a build has deployed |

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP.
See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
