# Policy Schema for Apigee X/hybrid policies

A set of XML Schemas (XSD 1.1) that describe the required and optional parts of
the XML "Domain specific language" for Apigee X/hybrid policies.

## Background

Currently, Google has published a set of Schemas for Apigee at [this
link](https://github.com/apigee/api-platform-samples/tree/master/schemas).  But
these schema are outdated, at least 5 years old at this time. They do not
support recent extensions in the configuration possibilities for existing
policies , nor do they include more recently added policies.

This set of schemas attempts to provide current schema for current policies.

However,

- these schemas are not directly, officially supported by Google.
- The support is "best effort" by me and other Apigeeks
- You can file issues on Github if you find problems. Or file PRs on them.

## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.

## Using the Schemas

To use them , you will need an XSD validator tool.  These schemas are
implemented using XSD 1.1; You will need an XSD-1.1 compliant validator.
Notably, Microsoft has not implemented XSD1.1 support in .NET , as far as I
know.  You can build a simple validator in Python using xmlschema, or Java using
its builtin JAXP.

## License

This material is [Copyright © 2025 Google LLC](./NOTICE).
and is licensed under the [Apache 2.0 License](LICENSE).


## Support

These schemas are published as open-source software; they are not a supported
part of Apigee.  If you have questions or need assistance, inquire on [the
Google Cloud Community forum dedicated to
Apigee](https://goo.gle/apigee-community) There is no service-level guarantee
for responses to inquiries posted to that site.
