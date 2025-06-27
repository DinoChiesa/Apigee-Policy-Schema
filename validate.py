#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import pathlib
import sys
from pprint import pprint

import xmlschema


def validate_xml(xsd_path: str, xml_path: str) -> bool:
    """
    Validates an XML file against an XSD schema using the 'xmlschema' library.
    This library supports XSD 1.1.

    Args:
    xsd_path (str): The file path to the XSD schema.
    xml_path (str): The file path to the XML document to validate.

    Returns:
    bool: True if validation is successful, False otherwise.
    """
    # 1. Load the XSD schema
    try:
        # The xmlschema library can load from a file path directly
        # and automatically handles XSD 1.0 and 1.1.
        #
        # Resolve the schema path to an absolute path to prevent issues
        # where the library might incorrectly combine a relative schema path
        # with the base_url.
        schema = xmlschema.XMLSchema11(xsd_path)

        print(f"✅ Schema '{xsd_path}' loaded successfully.")
        # Diagnostic output: print the names of the globally defined elements.
        print(f"   Defined elements: {list(schema.elements.keys())}")

    except xmlschema.XMLSchemaException as e:
        print(f"❌ Error: The schema '{xsd_path}' is not valid.")
        print(f"   Reason: {e}")
        return False
    except FileNotFoundError:
        print(f"❌ Error: Schema file not found at '{xsd_path}'")
        return False
    except Exception as e:
        print(f"❌ An unexpected error occurred while loading the schema: {e}")
        return False

    # 2. Validate the XML against the schema
    try:
        # iter_errors() returns an iterator for all validation errors.
        # We convert it to a list to check if there are any errors.
        validation_errors = list(schema.iter_errors(xml_path))

        if not validation_errors:
            print(f"✅ XML document '{xml_path}' parsed successfully.")
            print("🎉 SUCCESS: The XML document is valid against the schema.")
            return True
        else:
            print(f"❌ VALIDATION FAILED: The XML document '{xml_path}' is invalid.")
            print("   --- Error Details ---")
            for error in validation_errors:
                # The 'error' object is an instance of XMLSchemaValidationError.
                # It does not have 'line' or 'column' attributes directly.
                # However, if lxml is installed, error.elem will be an lxml
                # element, which has a 'sourceline' attribute.
                print(f"Path: {error.path}: {error.reason}")
            return False

    except FileNotFoundError:
        print(f"❌ Error: XML file not found at '{xml_path}'")
        return False
    except Exception as e:
        # This can catch XML parsing errors (e.g., not well-formed)
        # and other unexpected issues during validation.
        print(
            f"❌ An unexpected validation error occurred while processing '{xml_path}'."
        )
        print(f"   Reason: {e}")
        return False


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Validate an XML file against an XSD schema."
    )
    parser.add_argument(
        "--xsd", dest="xsd_file", required=True, help="Path to the XSD schema file."
    )
    parser.add_argument(
        "--xml",
        dest="xml_file",
        required=True,
        help="Path to the XML document to validate.",
    )
    parser.add_argument("--verbose", action="store_true", help="Enable verbose output.")

    args = parser.parse_args()

    xsd_file_path = args.xsd_file
    xml_file_path = args.xml_file

    # Run the validation and set the exit code based on the result
    is_valid = validate_xml(xsd_file_path, xml_file_path)
    print("--- END Validation ---\n")

    if is_valid:
        sys.exit(0)
    else:
        sys.exit(1)
