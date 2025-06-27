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

// AI>> modify this to accept command-line arguments:
// --xsd PATHNAME_OF_XSD
// --xml PATHNAME_OF_XML
//
// If either of these parameters are not present,
// print a helpful message and exit. AI!

try {
  const schema = libxml.parseXml(fs.readFileSync(schemaPath, "utf8"));

  const doc = libxml.parseXml(document);
  const valid = doc.validate(schema);
  if (!valid) {
    console.error("XML did not validate against XSD schema!", {
      errors: doc.validationErrors,
    });
  }
  return valid;
} catch (e) {
  throw e;
}
