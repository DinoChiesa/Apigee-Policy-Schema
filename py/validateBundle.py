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
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
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


def get_policy_files_from_bundle(
    source_input_path: pathlib.Path,
) -> Tuple[List[pathlib.Path], pathlib.Path, Optional[str]]:
    """
    Finds all policy XML files from a source directory or zip bundle.

    Handles unzipping archives into a temporary directory if necessary.
    Exits the script with an error if the source is invalid or the policies
    directory cannot be found.

    Args:
        source_input_path: Path to the source directory or .zip file.

    Returns:
        A tuple containing:
        - A sorted list of paths to the policy XML files.
        - The path to the policies directory.
        - The name of the temporary directory if one was created, otherwise None.
    """
    temp_dir_name = None
    source_path = None  # This will be the path to the directory to be validated.

    if not source_input_path.exists():
        print(f"Error: Source not found at '{source_input_path}'", file=sys.stderr)
        sys.exit(1)

    if source_input_path.is_dir():
        source_path = source_input_path
    elif source_input_path.is_file() and str(source_input_path).endswith(".zip"):
        if not zipfile.is_zipfile(source_input_path):
            print(
                f"Error: Source file '{source_input_path}' is not a valid zip file.",
                file=sys.stderr,
            )
            sys.exit(1)

        temp_dir_name = tempfile.mkdtemp()
        source_path = pathlib.Path(temp_dir_name)

        try:
            with zipfile.ZipFile(source_input_path, "r") as zip_ref:
                zip_ref.extractall(source_path)
        except zipfile.BadZipFile:
            print(
                f"Error: Could not unzip '{source_input_path}'. File may be corrupt.",
                file=sys.stderr,
            )
            sys.exit(1)
    else:
        print(
            f"Error: Source '{source_input_path}' must be a directory or a .zip file.",
            file=sys.stderr,
        )
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
        if temp_dir_name:
            print(
                f"Error: The zip file '{source_input_path}' does not appear to be a valid bundle.",
                file=sys.stderr,
            )
            print(
                "Could not find a 'policies' directory in standard locations within the archive.",
                file=sys.stderr,
            )
        else:
            print(
                "Error: Could not find 'policies' directory in standard locations within source.",
                file=sys.stderr,
            )
        sys.exit(1)

    xml_files = sorted(list(policies_path.glob("*.xml")))
    return xml_files, policies_path, temp_dir_name


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
    parser.add_argument(
        "--source",
        dest="source",
        required=True,
        help="Directory of the bundle source, or a .zip file containing a bundle.",
    )

    args = parser.parse_args()

    temp_dir_name = None
    try:
        xsd_source_path = pathlib.Path(args.xsd_source_dir)
        if not xsd_source_path.is_dir():
            print(
                f"Error: XSD source directory not found at '{xsd_source_path}'",
                file=sys.stderr,
            )
            sys.exit(1)

        source_input_path = pathlib.Path(args.source)

        xml_files, policies_path, temp_dir_name = get_policy_files_from_bundle(
            source_input_path
        )

        if not xml_files:
            print(f"No XML files found in {policies_path}")
            sys.exit(0)

        results = []
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

    finally:
        if temp_dir_name:
            shutil.rmtree(temp_dir_name)
