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

import xsdValidator from "xsd-schema-validator";
import which from "which";
import fs from "node:fs/promises";
import path from "node:path";

const getArg = (argName) => {
  const argIndex = process.argv.indexOf(argName);
  if (argIndex > -1 && process.argv.length > argIndex + 1) {
    return process.argv[argIndex + 1];
  }
  return null;
};
const scriptName = () => path.basename(process.argv[1]);

const schemaPath = getArg("--xsd");
const xmlPath = getArg("--xml");

if (!schemaPath || !xmlPath) {
  console.error(
    `Usage: node ${scriptName()} --xsd <PATHNAME_OF_XSD> --xml <PATHNAME_OF_XML>`,
  );
  process.exit(1);
}

const javaAvailable = () => {
  const options = process.env.JAVA_HOME
    ? { path: process.env.JAVA_HOME + "/bin" }
    : {};

  return which("javac", options);
};

try {
  if (!javaAvailable()) {
    console.error(
      `Cannot run without javac. Maybe you need to set JAVA_HOME? or your PATH.`,
    );
    process.exit(1);
  }
  const xmlString = await fs.readFile(xmlPath, "utf-8");
  const result = await xsdValidator.validateXML(xmlString, schemaPath);

  if (result.valid) {
    console.log(`${xmlPath} validates against ${schemaPath}.`);
  } else {
    console.error(`${xmlPath} fails to validate against ${schemaPath}.`);
    process.exit(1);
  }
} catch (e) {
  console.error(`An error occurred: ${e.message}`);
  process.exit(1);
}
