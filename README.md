### GitObjects  -->  SimpleObjects.java

It all started with [an excellent article](https://hackernoon.com/https-medium-com-zspajich-understanding-git-data-model-95eb16cc99f5) on .git/objects -- I had to try it myself!

It worked fine, but the story is incomplete: Most Git objects are packed!


### GitInspector  -->  Git.java

Rather than trying to unpack objects, Git should do the work -- use `java.lang.ProcessBuilder` 

**Usage:** Compile and run Git.java at the root of any Git repo

The output of the program on *this repo*, when it had only three commits:
````
$ java Git
88f4044ecf7f20aba666136072bd4f0132c28f0d
21/03/2018 17:18  GitObjects.java
tree 91ea30  4 items ***  parent e4f4d0
============================================================
21/03/2018 17:17  modify README
tree c9d0f5  2 items ***  parent 7bb2af
============================================================
21/03/2018 14:01  Initial commit
tree bcadff  1 items ***
============================================================
````
