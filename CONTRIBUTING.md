# Contributing to UhcCore Rebirth

Thank you for your interest in contributing to this plugin!
If you have any questions, open an issue on [GitHub][issue-tracker].

## Submitting feature requests and bug reports

If you have an idea for a new feature, or found a bug that needs to be
fixed, you can [create an issue][issue-tracker] at the GitHub repository.
Please be as descriptive as possible and include all relevant information,
such as the jar you use (Legacy or Modern), your server version, error
messages and/or server logs when submitting a bug report.

[issue-tracker]: https://github.com/JainaGam3r45/UhcCoreRebirth/issues

## Contributing features and bug fixes

Before you start coding, consider submitting a feature request or bug report
(see above) describing what you want to contribute. You may receive tips about where to start or how to
structure your code, which increases the likelihood of your contribution being
accepted. For simple contributions, like fixing a typo or editing a few lines,
you can skip this step.

### Required software

- Git, to clone the repository and to submit your contribution.
- A Java Development Kit (JDK), to run the Gradle build tool. The build itself
uses Gradle toolchains with Java 21.
- A code editor with support for Java projects using the Gradle build tool.
    - You should also make sure that your editor has support for
    [EditorConfig](https://editorconfig.org), perhaps by downloading a plugin.
    This will ensure that your code is formatted consistently with the rest
    of the code in the project.

### Recommended steps

1. [Fork this repository][forking] and clone the fork to your local machine.
2. Open the root directory as a Gradle project in your code editor.
3. Wait for the projects to be imported, and then start coding!
4. When you think you are done, make sure to [test your changes][testing].
5. Commit and push your changes **on a new Git branch** in your forked repository.
6. [Open a pull request][pull-request] for your contribution.

*Note: Please avoid opening a pull request from your main branch.
Instead, create a new branch for the pull request, based on your main branch.*

[forking]: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/fork-a-repo
[testing]: #testing-your-changes
[pull-request]: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork

### Project layout

The build is a Gradle multi-module project:

- `uhccore-common`: the shared core (game logic, scenarios, commands).
Config and language resources live in `uhccore-common/src/main/resources`.
- `uhccore-legacy`: builds `UhcCoreRebirth-Legacy-<version>.jar`
(Java 8+, Spigot/Paper 1.8.8 to 1.20.6).
- `uhccore-modern`: builds `UhcCoreRebirth-Modern-<version>.jar`
(Java 21+, Paper 1.21.x and 26.x).
- `version-adapters/*`: version-specific code, kept out of the core.

Running `./gradlew clean build` (or `gradlew clean build` on Windows) from the
root builds both jars and copies them to `build/libs/`.

### Testing your changes

The root project has a `runServer` Gradle task that downloads and starts a
Paper server for quick testing. This task comes from the
[run-paper][run-paper] Gradle plugin. Without `-PmcVersion` it runs 1.21.4,
and it picks the Java toolchain to match the Minecraft version.

[run-paper]: https://github.com/jpenilla/run-task

**Run on Windows**

```
gradlew runServer -PmcVersion=1.8.8
```

**Run on Linux or macOS**

```
./gradlew runServer -PmcVersion=1.8.8
```

The server files will be stored under the `run/<version>/` folder (relative to
the base of this repository), such as `run/1.21.4/` or `run/1.8.8/`. Note that
you will need to edit the `run/<version>/eula.txt` file manually the first time
you run a server, in order to agree to the Minecraft EULA.

The root `runServer` task does not pick a plugin jar by itself. Build first,
then copy the matching jar from `build/libs/` into `run/<version>/plugins/`:
the Legacy jar for 1.8.8 to 1.20.6, the Modern jar for 1.21+.

If you need to edit server and/or plugin configuration files or install additional
plugins for testing your code, you can do so in the `run/<version>/` folder.
This folder will be reused on subsequent executions of `runServer`.

### Debugging

After starting the server using `runServer`, you can also attach a debugger
via JDWP, which allows you to set breakpoints, inspect variables and more
while the plugin is running. Consult the documentation of your code editor
for more information. Below are documentation links for a few popular editors:

- [IntelliJ IDEA](https://www.jetbrains.com/help/idea/attaching-to-local-process.html#attach-to-local)
- [Eclipse](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/tasks/task-remotejava_launch_config.htm)
- [VS Code](https://github.com/microsoft/vscode-java-debug/blob/main/Configuration.md)

You should attach the debugger to the `localhost` address, at port `5005`.
