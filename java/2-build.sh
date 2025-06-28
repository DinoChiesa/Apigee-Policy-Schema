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

if ! command -v "javac" &>/dev/null; then
  printf "\njavac is not found; it must be available on the path.\nExiting.\n\n"
  exit 1
fi

rm -fr ./out
for javasrc in ./*.java; do
  printf "compiling %s...\n" "${javasrc}"
  if ! javac "${javasrc}" -d ./out; then
    printf "compile failed.\n"
    exit 1
  fi
done
