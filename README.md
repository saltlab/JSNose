JSNose
======

JSNose is a JavaScript code smell detector tool written in Java. The tool at this point is capable of detecting the following code smells in JavaScript:

1. Closure smells
2. Coupling of JS/HTML/CSS
3. Empty catch
4. Excessive global variables
5. Large object
6. Large object
7. Long message chain
8. Long method/function
9. Long parameter list
10. Nested callback
11. Refused bequest
12. Switch statement
13. Unused code

Usage
-----------------

Run it trough the Main class in JSNose/src/main/java/com/crawljax/examples/JSNoseExample.java

The core smell detection process and thresholds are located in JSNose/src/main/java/codesmells/SmellDetector.java
