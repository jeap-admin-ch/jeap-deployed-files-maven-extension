# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [1.1.0] - 2026-11-25

### Changed

- Update parent from 11.13.0 to 12.0.0

## [1.0.0] - 2026-11-13

### Added

- Initial version: a Maven core extension that records every file a build deploys to a remote repository and writes
  the list to `target/deployed-files.json`. Requires Maven 3.9 and Java 25 or newer
