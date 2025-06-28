# Policy Schema for Apigee X/hybrid policies

A set of XML Schemas (XSD 1.1) that describe the required and optional parts of
the XML "Domain specific language" for Apigee X/hybrid policies.

## Background

Some time back, Google published a set of Schemas for Apigee at [this
link](https://github.com/apigee/api-platform-samples/tree/master/schemas).  But
these schema are now outdated. They do not support recent extensions in the
configuration possibilities for existing policies, nor do they include more
recently added policies.

This set of schemas attempts to provide current schema for current policies.

However,

- these schemas are not directly, officially supported by Google.
- The support is "best effort" by me and other Apigeeks.
- You can file issues on Github if you find problems. Or file PRs on them.
- There is no set of schema for Apigee Edge.

## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.

## Using the Schemas

To use the schemas, you will need an XSD validator tool.

These schemas are implemented using XSD 1.1. They rely on
[xs:assert](https://www.w3.org/TR/xmlschema11-1/#cAssertions) (and xs:assertion)
which were added to XSD in XSD v1.1. Therefore, you will need an XSD-1.1
compliant validator to use them.  I've included some example validators in this
repo, one built with python, and one with Java. You can also build your own in
any language/platform that supports XSD 1.1.

Please note:

* .NET does not natively support XML Schema Definition (XSD) 1.1. While
  Microsoft's built-in XML tools support XSD 1.0, they don't include features
  like assertions (xs:assert) and other features in XSD 1.1. To work with XSD
  1.1 in .NET, you'll need to use a third-party library like Saxon HE.

* I did not find an XSD 1.1-compliant XSD validator for Nodejs.  The most useful
  thing I found was the NPM package
  [xsd-schema-validator](https://www.npmjs.com/package/xsd-schema-validator),
  which is a javascript wrapper on Java, believe it or not. But that also did
  not support XSD 1.1.



## Example Validation Scripts

This repository includes some examples for validating policy files, or bundles.

- [python](./py) - using the xmlschema module
- [java](./java) - using XercesJ 2.12.2

## License

This material is [Copyright © 2025 Google LLC](./NOTICE).
and is licensed under the [Apache 2.0 License](LICENSE).


## Support

These schemas are published as open-source software; they are not a supported
part of Apigee.  If you have questions or need assistance, inquire on [the
Google Cloud Community forum dedicated to
Apigee](https://goo.gle/apigee-community) There is no service-level guarantee
for responses to inquiries posted to that site.


## Bugs

* There are no schemas here for:
  - ProxyEndpoint
  - TargetEndpoint
  - SharedflowBundle
