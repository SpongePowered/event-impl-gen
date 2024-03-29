# Event Implementation Generator

Generates the event implementations and factory for SpongeAPI based on event
templates formatted as Java interfaces using an annotation processor. This tool should be compatible with all standard-compliant Java 8+ compilers.

## Usage
You can use `event-impl-gen` in your plugins to generate event implementations based on event interfaces. `event-impl-gen` is primarily tested on 
Gradle 7.2+, but should work on any Java build tool.

### Gradle
To use it in your plugin, you need to apply it to your Gradle build script:

**CAUTION**: Due to a bug in Gradle annotation processing, Gradle 7.2 or newer is required to take advantage of incremental compilation.

```gradle
dependencies {
    annotationProcessor("org.spongepowered:event-impl-gen:8.0.0-SNAPSHOT")
    compileOnlyApi("org.spongepowered:event-impl-gen-annotations:8.0.0-SNAPSHOT")
}

compileJava {
    options.compilerArgs << ["-AeventGenFactory=com.example.myplugin.event.factory.MyPluginEventFactory"]
    
    // Then annotate all packages or interfaces containing events with @GenerateFactoryMethod
}
```

### IDE Integration

**IntelliJ**: Projects built in IntelliJ will need to be compiled once for annotation processor-generated interfaces to show up.

**Eclipse**: Because Eclipse compiles continuously, event implementations are generated immediately. When using Gradle, a bit of plumbing is 
required to ensure annotation processors are automatically detected. See the 


### Options

Beyond the one required `eventGenFactory` option to declare the location of the generated event factory, there are several other options to 
control the output:

| Option | Description | Format | Required | Default Value |
|------- | ----------- | ------ | -------- | ------------- |
| `eventGenFactory` | The package to put the generated factory in | qualified binary name | *yes* | *none* |
| `sortPriorityPrefix` | prefix for methods that should be placed first when sotring | string | *no* | `original` |
| `groupingPrefixes` | appears to modify sorting to group certain prefixes together | `<a>:<b>[,<a>:<b>]*` | *no* | `from:to` |
| `inclusiveAnnotations` | customize the annotations that include an event type  in processing | binary name of an annotation | *no* | `o.s.a.u.a.e.GenerateFactoryMethod` |
| `exclusiveAnnotations` | customize the annotations that exclude an event type from processing | binary name of an annotation | *no* | `o.s.a.u.a.e.GenerateFactoryMethod` |
| `eventGenDebug` | print extra debug output | boolean | *no* | `false` |

