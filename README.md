# Event Implementation Generator

Generates the event implementations and factory for SpongeAPI based on event
templates formatted as Java interfaces using [Spoon](https://github.com/INRIA/spoon).

## Usage
You can use `event-impl-gen` in your plugins to generate event implementations based on event interfaces.
To use it in your plugin, you need to apply it to your Gradle build script:

```groovy
plugins {
    id 'org.spongepowered.event-impl-gen' version '5.0.0'
}

dependencies {
    // Add generated event factory to the classpath
    compile files(tasks.genEventImpl)
}

jar {
    // Include generated event factory in the JAR
    from tasks.genEventImpl
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
