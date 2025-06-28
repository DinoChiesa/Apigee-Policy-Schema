## Example Validator in Java

This is a Java validator that uses Apache XercesJ 2.12.2 with XSD 1.1 support
to validate policy files against the Apigee schema.

### Pre-requisites

- Java 24 - it probably works in Java 21 and earlier as well. I haven't tested it.
- XercesJ 2.12.2 with XSD 1.1 .  But there's a download script to get this dependency.

## Using it

1. Download the dependencies.

   The builtin JAXP (Xerces) in Java does not include XSD1.1 support.
   You need additional libraries, including a specific version of Apache XercesJ,
   and various dependencies. This script downloads all of those.

   ```sh
   1-download-dependencies.sh
   ```

2. Build

   You must have javac on your path. It should be a recent javac. I've tested with
   OpenJDK 24.

   ```sh
   2-build.sh
   ```

3. Run

   Check a single policy file against a single Schema that you specify:

   ```sh
   java -classpath ./out:./vendor/*   SchemaValidatorTool \
        --xsd ../schema/ServiceCallout.xsd \
        --xml ~/dev/my-bundle/apiproxy/policies/SC-Get-Identity-Token-from-IAM.xml
   ```

   Check all of the policy files in an "exploded bundle" against any of the
   possible Schemas available for Apigee:

   ```sh
   java -classpath ./out:./vendor/*  BundlePolicyValidator \
        --xsdSource ../schema \
        --source ~/dev/my-bundle/apiproxy
   ```

   Check all of the policy files in a bundle zip:

   ```sh
   java -classpath ./out:./vendor/*  BundlePolicyValidator \
        --xsdSource ../schema \
        --source ~/dev/my-bundle/apiproxy-bundle.zip
   ```
