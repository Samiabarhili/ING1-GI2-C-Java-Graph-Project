#!/bin/bash
# Run from project root (where pom.xml is)
mvn javadoc:javadoc
mkdir -p docs
cp -r target/site/apidocs/* docs/
git add docs/
git commit -m "docs: add generated JavaDoc"
git push
echo "JavaDoc generated and committed."
