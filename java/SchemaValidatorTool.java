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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class SchemaValidatorTool {
  public SchemaValidatorTool() {}

  public record ValidationMessage(SAXParseException exception, String type) {}

  public record ValidationResult(List<ValidationMessage> notices) {
    public boolean hasErrors() {
      return notices.stream()
          .anyMatch(n -> "error".equals(n.type()) || "fatalError".equals(n.type()));
    }

    public boolean hasWarnings() {
      return notices.stream().anyMatch(n -> "warning".equals(n.type()));
    }
  }

  static class CollectingErrorHandler implements ErrorHandler {
    private final List<ValidationMessage> notices = new ArrayList<>();

    @Override
    public void warning(SAXParseException exception) throws SAXException {
      notices.add(new ValidationMessage(exception, "warning"));
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
      notices.add(new ValidationMessage(exception, "error"));
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
      notices.add(new ValidationMessage(exception, "fatalError"));
    }

    public ValidationResult getResult() {
      return new ValidationResult(List.copyOf(notices));
    }
  }

  private record ToolArgs(String xmlFile, String xsdFile, boolean insecure) {}

  private static ToolArgs parseArgs(String[] args) {
    String fileName = null;
    String schemaFile = null;
    boolean insecure = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--insecure".equals(arg)) {
        insecure = true;
      } else if ("--xml".equals(arg)) {
        if (i + 1 < args.length) {
          fileName = args[++i];
        } else {
          System.err.println("Error: missing filename for --xml option.");
          System.exit(1);
        }
      } else if ("--xsd".equals(arg)) {
        if (i + 1 < args.length) {
          schemaFile = args[++i];
        } else {
          System.err.println("Error: missing filename for --xsd option.");
          System.exit(1);
        }
      }
    }

    if (schemaFile == null || fileName == null) {
      System.err.println(
          "Usage: java SchemaValidatorTool --xsd <schema.xsd> --xml <doc.xml> [--insecure]");
      System.exit(1);
    }
    return new ToolArgs(fileName, schemaFile, insecure);
  }

  public static void main(String[] args) throws Exception {
    ToolArgs toolArgs = parseArgs(args);
    var handler = new CollectingErrorHandler();
    boolean valid = false;

    try (InputStream inputStream = new FileInputStream(toolArgs.xmlFile())) {
      SchemaFactory sf = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
      sf.setFeature("http://apache.org/xml/features/validation/cta-full-xpath-checking", true);
      if (toolArgs.insecure()) {
        sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      }

      Schema schema = sf.newSchema(new File(toolArgs.xsdFile()));
      Validator validator = schema.newValidator();

      validator.setErrorHandler(handler);
      validator.validate(new StreamSource(inputStream));

      ValidationResult result = handler.getResult();

      if (result.hasErrors()) {
        System.out.printf("result: WITH_ERRORS%n");
      } else {
        System.out.printf("result: OK%n");
        valid = true;
      }
      for (ValidationMessage notice : result.notices()) {
        System.err.printf("[%s] %s%n", notice.type(), notice.exception());
      }
    } catch (Exception e) {
      System.err.printf("[fatal] %s%n", e.getMessage());
      System.out.printf("result: FATAL_ERROR%n");
    }

    System.exit(valid ? 0 : 1);
  }
}
