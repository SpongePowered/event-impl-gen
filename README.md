# Event Implementation Generator

Generates the event implementations and factory for SpongeAPI based on event
templates formatted as Java interfaces using [Spoon](https://github.com/INRIA/spoon).

## Usage
You can use `event-impl-gen` in your plugins to generate event implementations based on event interfaces.
To use it in your plugin, you need to apply it to your Gradle build script:

```gradle
plugins {
    id 'org.spongepowered.event-impl-gen' version '5.3.0'
}

genEventImpl {
    // The full qualified class name of the factory
    outputFactory = 'com.example.myplugin.event.factory.MyPluginEventFactory'
    // The path to your event interfaces
    include 'com/example/myplugin/event/'
}
```

Gradle will automatically generate the event factory when building your plugin. However, if you want to
import your project into the IDE, you must run the task manually. To generate the event factory manually,
run the `genEventImpl` Gradle task.

By default, the `jar` task of your project is automatically configured to include the generated event factory.
However, if you have a custom `jar` task you have to include the classes manually:

```groovy
task myCustomJar(type: Jar) {
    // ...
    from tasks.genEventImpl
}
```

If you are using the [shadow](https://github.com/johnrengelman/shadow) plugin, the event factory should be
also included automatically.
