# Micrometer Build

This folder contains the custom plugins and conventions for the Micrometer build.
They are declared in the `build.gradle` file in this folder.

## Build Plugins

### API Compatibility

The `io.micrometer.build.api-compatibility` plugin uses the [Gradle JApiCmp](https://github.com/melix/japicmp-gradle-plugin)
plugin to compare the current build with the previous version and prevent accidental breakage of existing APIs.
By default, only APIs that were already marked as `@Deprecated` can be removed in a follow-up release.
The `apiCompatibility` task relies on a `compatibleVersion` project property that you can define inline:

```
./gradlew apiCompatibility -PcompatibleVersion=1.12.0
```

More commonly, the main `check` task depends on `apiCompatibility` and the `compatibleVersion` property is defined
in the `gradle.properties` at the root of this repository:

```
./gradlew check
```

Defining the `compatibleVersion` with a specific `SKIP` value will... skip the api compatibility check entirely.

```
./gradlew check -PcompatibleVersion=SKIP
```


