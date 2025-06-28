#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
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

if ! command -v "curl" &>/dev/null; then
  printf "\ncurl is not found; it must be available on the path.\nExiting.\n\n"
  exit 1
fi

# The only "no cost" XSD 1.1 validator for Java that I have
# found is XercesJ. The packaging on maven central does not
# include all the dependencies, but the download direct from
# apache.org does include those dependencies. 
xercesArchivePath=./xercesj-with-xml-schema-1.1.tgz
mkdir -p ./vendor
rm -f ./vendor/*.jar ./vendor/*.tgz

cd vendor
curl -o "${xercesArchivePath}" \
  https://dlcdn.apache.org/xerces/j/binaries/Xerces-J-bin.2.12.2-xml-schema-1.1.tar.gz

tar xvf "${xercesArchivePath}" --wildcards xerces-2_12_2-xml-schema-1.1/\*.jar
rm "${xercesArchivePath}"
mv xerces-2_12_2-xml-schema-1.1/*.jar .
rmdir xerces-2_12_2-xml-schema-1.1
rm -f xercesSamples.jar
