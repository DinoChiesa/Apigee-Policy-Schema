// Copyright © 2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import libxmljs from "libxmljs";
import fs from "fs";
import path from "path";

const getArg = (argName) => {
  const argIndex = process.argv.indexOf(argName);
  if (argIndex > -1 && process.argv.length > argIndex + 1) {
    return process.argv[argIndex + 1];
  }
  return null;
};

const schemaPath = getArg("--xsd");
const xmlPath = getArg("--xml");

if (!schemaPath || !xmlPath) {
  const scriptName = path.basename(process.argv[1]);
  console.error(
    `Usage: node ${scriptName} --xsd <PATHNAME_OF_XSD> --xml <PATHNAME_OF_XML>`
  );
  process.exit(1);
}

try {
  const schemaString = fs.readFileSync(schemaPath, "utf8");
  const schema = libxmljs.parseXml(schemaString);

  const xmlString = fs.readFileSync(xmlPath, "utf8");
  const doc = libxmljs.parseXml(xmlString);

  if (doc.validate(schema)) {
    console.log(`${xmlPath} validates against ${schemaPath}.`);
  } else {
    console.error(`${xmlPath} fails to validate against ${schemaPath}.`);
    console.error("Validation errors:");
    doc.validationErrors.forEach((error) => {
      console.error(
        `  line ${error.line}, col ${error.column}: ${error.message.trim()}`
      );
    });
    process.exit(1);
  }
} catch (e) {
  console.error(`An error occurred: ${e.message}`);
  process.exit(1);
}
