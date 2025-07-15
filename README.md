# MvnRelease

MvnRelease is a utility script for managing Maven project releases and development versions.
It automates the process of creating release branches, updating versions, and managing Git operations.
The script can be run interactively or with command-line arguments.

## Features
This script is designed to ease release process for projects using git-flow methodology.
By default it assumes that you are using a `develop` branch for development and `release/major.minor` branches for releases.
The develop branch always uses SNAPSHOT versions while release branches always contain final project version.

The script is easily customizable to fit your project's needs.

## Requirements
- Java 17 or higher
- maven installed and available in PATH
- git installed and available in PATH

## Setup your project
- [ ] copy src/mvnrelease.java to your project root (pom.xml is expected at this location)
- [ ] update `Configuration` section if the defaults doesn't fit your project
- [ ] update `releaseBranchVersionFunction` which handles naming convention for release branches, by default it uses the format `release/major.minor`.
- [ ] Add mvnrelease.java to your exclusions in sonar-project.properties file, to avoid false positives in SonarQube analysis.

## Running MvnRelease
Execution is very simple, no need to compile, just run with Java which enables the interactive mode:
```
> java mvnrelease.java 
```

Command-line options are available by using the `--help` option:
```
> java mvnrelease.java --help

Automatic release script using maven and git.
Usage:
 MvnRelease [operation] [version] [options]
	Available operations:
		[r] release - performs a release and creates a release branch
		[d] dev     - create next development version (runs on develop branch only)
		[b] bugfix  - create bugfix version (should be run on release/ branch), doesn't create a new branch
		[v] version - replaces the version in pom files and does nothing else
	[version]
		desired new version, should have -SNAPSHOT suffix when run with 'develop' operation
	[options]
		--debug   - enable debug output
		--confirm - enables confirmation dialog before running the process
		--help    - prints this info

```
## Author
Mojmir Nebel

***
