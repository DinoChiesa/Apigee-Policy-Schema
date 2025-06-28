#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Copyright © 2025 Google LLC.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET
from typing import Dict, List, Optional, Tuple

import xmlschema


def get_root_element_name(
    xml_path: pathlib.Path,
) -> Tuple[Optional[str], Optional[str]]:
    """
    Parses an XML file and returns the tag name of the root element.
    Returns (root_name, None) on success, or (None, error_message) on failure.
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        return root.tag, None
    except ET.ParseError as e:
        return None, f"Malformed XML file: {e}"
    except Exception as e:
        return None, f"Could not read or parse file: {e}"


def validate_with_schema(
    xsd_path: str, xml_path: str, schema_cache: Dict[str, xmlschema.XMLSchema11]
) -> List[str]:
    """
    Validates an XML file against an XSD schema using the 'xmlschema' library.
    This library supports XSD 1.1.

    Args:
    xsd_path (str): The file path to the XSD schema.
    xml_path (str): The file path to the XML document to validate.
    schema_cache (dict): A cache for storing loaded XSD schemas.

    Returns:
    list: A list of validation error messages. An empty list indicates success.
    """
    errors = []
    try:
        if xsd_path in schema_cache:
            schema = schema_cache[xsd_path]
        else:
            schema = xmlschema.XMLSchema11(xsd_path)
            schema_cache[xsd_path] = schema
    except xmlschema.XMLSchemaException as e:
        errors.append(
            f"The schema '{pathlib.Path(xsd_path).name}' is not valid. Reason: {e}"
        )
        return errors
    except FileNotFoundError:
        errors.append(f"Schema file not found at '{xsd_path}'")
        return errors
    except Exception as e:
        errors.append(
            f"An unexpected error occurred while loading schema '{xsd_path}': {e}"
        )
        return errors

    try:
        validation_errors = list(schema.iter_errors(xml_path))
        for error in validation_errors:
            # The 'error' object is an instance of XMLSchemaValidationError.
            # It has a 'sourceline' attribute if lxml is installed and used.
            line_info = (
                f" (line {error.sourceline})"
                if hasattr(error, "sourceline") and error.sourceline is not None
                else ""
            )
            errors.append(f"Path: {error.path}{line_info}: {error.reason}")
    except FileNotFoundError:
        errors.append(f"XML file not found at '{xml_path}'")
    except Exception as e:
        # This can catch XML parsing errors (e.g., not well-formed)
        # and other unexpected issues during validation.
        errors.append(f"An unexpected validation error occurred. Reason: {e}")
    return errors


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Validate all policy XML files in a bundle."
    )
    parser.add_argument(
        "--xsdSource",
        dest="xsd_source_dir",
        required=True,
        help="Directory containing XSD schema files.",
    )
    # AI! Modify the logic slightly so that the --source argument takes either
    # a directory (as currently implemented), OR, it takes the name of a .zip
    # file which contains a "proxy bundle". 
    #
    # In case of the latter, the script should
    #  - create a temporary directory
    #  - unzip the zip file in that temporary directory
    #  - verify that the structure of the unzipped contents looks something like this:
    #
    #     apiproxy/
    #     apiproxy/API-PROXY-NAME-HERE.xml
    #     apiproxy/policies/
    #     apiproxy/policies/NAME-OF-POLICY-1.xml
    #     apiproxy/policies/NAME-OF-POLICY-2.xml
    #     apiproxy/policies/...
    #     apiproxy/resources/
    #     ...
    #
    # If unzip fails or if the unzipped archive does not look like that, then
    # delete the temp directory, print an error message and exit with status 1.
    #
    # If the unzipped archive DOES result in a structure like that, then
    # treat the temporary directory as the "source dir" and proceed with
    # the logic as currently implemented.
    #
    # In all cases make sure to remove the temporary directory at the
    # completion of the script. 

    parser.add_argument(
        "--source",
        dest="source_dir",
        required=True,
        help="Directory of the bundle source.",
    )

    args = parser.parse_args()

    xsd_source_path = pathlib.Path(args.xsd_source_dir)
    source_path = pathlib.Path(args.source_dir)

    if not xsd_source_path.is_dir():
        print(
            f"Error: XSD source directory not found at '{xsd_source_path}'",
            file=sys.stderr,
        )
        sys.exit(1)

    if not source_path.is_dir():
        print(f"Error: Source directory not found at '{source_path}'", file=sys.stderr)
        sys.exit(1)

    policies_dir_options = [
        "policies",
        "apiproxy/policies",
        "sharedflowbundle/policies",
    ]
    policies_path = None
    for p_dir in policies_dir_options:
        current_path = source_path.joinpath(p_dir)
        if current_path.is_dir():
            policies_path = current_path
            break

    if not policies_path:
        print(
            "Error: Could not find 'policies' directory in standard locations within source.",
            file=sys.stderr,
        )
        sys.exit(1)

    results = []
    xml_files = sorted(list(policies_path.glob("*.xml")))

    if not xml_files:
        print(f"No XML files found in {policies_path}")
        sys.exit(0)

    schema_cache = {}

    for xml_file in xml_files:
        file_result = {"file": xml_file.name, "errors": []}
        root_element, err_msg = get_root_element_name(xml_file)

        if err_msg:
            file_result["errors"].append(err_msg)
            results.append(file_result)
            continue

        if not root_element:
            file_result["errors"].append("Could not determine root element.")
            results.append(file_result)
            continue

        xsd_file_name = f"{root_element}.xsd"
        xsd_file_path = xsd_source_path.joinpath(xsd_file_name)

        if not xsd_file_path.is_file():
            file_result["errors"].append(
                f"Schema file '{xsd_file_name}' not found for root element '{root_element}'."
            )
            results.append(file_result)
            continue

        validation_errors = validate_with_schema(
            str(xsd_file_path), str(xml_file), schema_cache
        )
        if validation_errors:
            file_result["errors"].extend(validation_errors)

        results.append(file_result)

    print("==================== Validation Report ====================")
    print(f"Policies directory: {policies_path}")
    print()

    total_errors = 0
    for res in results:
        print(f"File: {res['file']}")
        if not res["errors"]:
            print("  result: OK")
        else:
            print("  result: ERRORS FOUND")
            total_errors += len(res["errors"])
            for error in res["errors"]:
                print(f"  - {error}")
        print("-------------------------------------------------------")

    if total_errors > 0:
        print(f"\nValidation finished with {total_errors} errors.")
        sys.exit(1)
    else:
        print("\nAll policies validated successfully.")
        sys.exit(0)
