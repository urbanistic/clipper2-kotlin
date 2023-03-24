# Clipper2KotlinMP
A Kotlin Multiplatform (jvm, js, native) port of
_[Clipper2-java](https://github.com/micycle1/Clipper2-java)_,
a java port of
_[Clipper2](https://github.com/AngusJohnson/Clipper2)_.

## Usage
todo

### Overview

The interface of *Clipper2-kotlin-mpp* is based on the original C# version/ java version.
As JsExport constructor and method overloading is not supported JsNames were provided for these methods 

The `Clipper` class provides static methods for clipping, path-offsetting, minkowski-sums and path simplification.
For more complex clipping operations (e.g. when clipping open paths or when outputs are expected to include polygons nested within holes of others), use the `Clipper64` or `ClipperD` classes directly.

### Maven
*Clipper2KotlinMP* is currently NOT available as Maven/Gradle artifact.

### Example

## Port Info
* this port is based on this java port: _[Clipper2-java](https://github.com/micycle1/Clipper2-java)_ , therefore I recomend to read their port infos first as
* Code passes all tests: polygon, line and polytree.
* Private And Public!! variables and methods have been renamed to their _camelCase_ variant
* `scanlineList` from `ClipperBase` used Java `TreeSet`. As this is not supported in MPP it was changed to `MutableList` with custom sorting.
