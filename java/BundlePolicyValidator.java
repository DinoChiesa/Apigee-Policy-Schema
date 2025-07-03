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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class BundlePolicyValidator {
  private final ToolArgs toolArgs;
  private SchemaFactory schemaFactory;
  private final Map<String, Schema> schemaCache = new LinkedHashMap<>();
  private Path bundlePath;
  private Path effectiveSourcePath;
  private Path tempDirToDelete = null;

  public BundlePolicyValidator(ToolArgs toolArgs) {
    this.toolArgs = toolArgs;
  }

  // ==============================================================================
  // ==== Validation Results ======================================================

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

  // ==============================================================================
  // ==== XSD Validation Error Handler ============================================
  static class CollectingErrorHandler implements ErrorHandler {
    private final List<ValidationMessage> notices = new ArrayList<>();

    public void addFatalError(String message) {
      SAXParseException e = new SAXParseException(message, null);
      notices.add(new ValidationMessage(e, "fatalError"));
    }

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

  // ==============================================================================
  // ==== ToolArgs ================================================================

  private record ToolArgs(String source, String xsdSourceDir, boolean insecure) {}

  private static ToolArgs parseArgs(String[] args) {
    String source = null;
    String xsdSourceDir = null;
    boolean insecure = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--insecure".equals(arg)) {
        insecure = true;
      } else if ("--source".equals(arg)) {
        if (i + 1 < args.length) {
          source = args[++i];
        } else {
          System.err.println("Error: missing file or directory for --source option.");
          System.exit(1);
        }
      } else if ("--xsdSource".equals(arg)) {
        if (i + 1 < args.length) {
          xsdSourceDir = args[++i];
        } else {
          System.err.println("Error: missing directory for --xsdSource option.");
          System.exit(1);
        }
      }
    }

    if (xsdSourceDir == null || source == null) {
      System.err.println(
          "Usage: java BundlePolicyValidator --xsdSource <directory> --source <file|directory>"
              + " [--insecure]");
      System.exit(1);
    }
    return new ToolArgs(source, xsdSourceDir, insecure);
  }

  // ==============================================================================

  private void prepareSource() throws IOException {
    File xsdSource = new File(toolArgs.xsdSourceDir());
    if (!xsdSource.exists() || !xsdSource.isDirectory()) {
      System.err.printf(
          "Error: xsdSource '%s' is not an existing directory.%n", toolArgs.xsdSourceDir());
      System.exit(1);
    }

    Path sourceInputPath = Paths.get(toolArgs.source());
    if (!Files.exists(sourceInputPath)) {
      System.err.printf("Error: source '%s' does not exist.%n", sourceInputPath);
      System.exit(1);
    }

    if (Files.isDirectory(sourceInputPath)) {
      this.effectiveSourcePath = sourceInputPath;
    } else if (Files.isRegularFile(sourceInputPath)
        && sourceInputPath.toString().toLowerCase().endsWith(".zip")) {
      this.tempDirToDelete = Files.createTempDirectory("bundle-validator-");
      this.effectiveSourcePath = this.tempDirToDelete;
      try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceInputPath.toFile()))) {
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
          Path newPath = this.effectiveSourcePath.resolve(zipEntry.getName());
          if (!newPath.normalize().startsWith(this.effectiveSourcePath.normalize())) {
            throw new IOException("Bad zip entry: " + zipEntry.getName());
          }
          if (zipEntry.isDirectory()) {
            Files.createDirectories(newPath);
          } else {
            if (newPath.getParent() != null) {
              if (Files.notExists(newPath.getParent())) {
                Files.createDirectories(newPath.getParent());
              }
            }
            Files.copy(zis, newPath);
          }
          zis.closeEntry();
          zipEntry = zis.getNextEntry();
        }
      } catch (java.util.zip.ZipException e) {
        System.err.printf("Error: Could not unzip '%s'. File may be corrupt.%n", sourceInputPath);
        System.exit(1);
      }
    } else {
      System.err.printf(
          "Error: source '%s' must be a directory or a .zip file.%n", sourceInputPath);
      System.exit(1);
    }

    String[] policiesDirs = {"apiproxy/policies", "sharedflowbundle/policies", "policies"};
    for (String dir : policiesDirs) {
      Path currentPoliciesPath = this.effectiveSourcePath.resolve(dir);
      if (Files.exists(currentPoliciesPath) && Files.isDirectory(currentPoliciesPath)) {
        // The bundle path is the parent of the policies directory.
        // For a path like "apiproxy/policies", the parent is "apiproxy".
        // For a path like "policies", the parent is the root of the bundle structure.
        this.bundlePath = currentPoliciesPath.getParent();
        break;
      }
    }

    if (this.bundlePath == null) {
      if (this.tempDirToDelete != null) {
        System.err.printf(
            "Error: The zip file '%s' does not appear to be a valid bundle.%n", sourceInputPath);
        System.err.println(
            "Could not find a 'policies' directory in standard locations within the archive.");
      } else {
        System.err.println("Error: Could not find a 'policies' directory in the source.");
        System.err.println("Searched for: apiproxy/policies, sharedflowbundle/policies, policies");
      }
      System.exit(1);
    }
  }

  private void cleanup() {
    if (this.tempDirToDelete != null) {
      try {
        try (Stream<Path> walk = Files.walk(this.tempDirToDelete)) {
          walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
      } catch (IOException e) {
        System.err.printf(
            "Warning: Failed to delete temporary directory %s%n", this.tempDirToDelete);
      }
    }
  }

  private void createSchemaFactory() throws SAXException {
    this.schemaFactory = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
    this.schemaFactory.setFeature(
        "http://apache.org/xml/features/validation/cta-full-xpath-checking", true);
    if (toolArgs.insecure()) {
      this.schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
    }
  }

  private void addXmlFilesFromDir(Path dir, List<File> files) throws IOException {
    if (Files.exists(dir) && Files.isDirectory(dir)) {
      try (Stream<Path> paths = Files.walk(dir)) {
        paths
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
            .map(Path::toFile)
            .forEach(files::add);
      }
    }
  }

  private List<File> findXmlFiles() throws IOException {
    if (this.bundlePath == null) {
      throw new IllegalStateException("bundlePath is not set. Call prepareSource() first.");
    }
    List<File> xmlFiles = new ArrayList<>();

    // Every bundle has a policies directory.
    addXmlFilesFromDir(this.bundlePath.resolve("policies"), xmlFiles);

    // API Proxies have proxies and optionally targets. Shared Flows have sharedflows.
    // A bundle can be one or the other, but not both.
    Path proxiesDir = this.bundlePath.resolve("proxies");
    if (Files.exists(proxiesDir) && Files.isDirectory(proxiesDir)) {
      addXmlFilesFromDir(proxiesDir, xmlFiles);
      addXmlFilesFromDir(this.bundlePath.resolve("targets"), xmlFiles);
    } else {
      // It's not an API proxy, maybe it is a Shared Flow.
      addXmlFilesFromDir(this.bundlePath.resolve("sharedflows"), xmlFiles);
    }
    return xmlFiles.stream().distinct().toList();
  }

  private String getRootElementName(File xmlFile) throws IOException, XMLStreamException {
    XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

    try (InputStream in = new FileInputStream(xmlFile)) {
      XMLEventReader reader = xmlInputFactory.createXMLEventReader(in);
      while (reader.hasNext()) {
        XMLEvent nextEvent = reader.nextEvent();
        if (nextEvent.isStartElement()) {
          StartElement startElement = nextEvent.asStartElement();
          return startElement.getName().getLocalPart();
        }
      }
    }
    return null;
  }

  private Schema getSchema(String rootElementName) throws SAXException {
    if (this.schemaCache.containsKey(rootElementName)) {
      return this.schemaCache.get(rootElementName);
    }
    Path xsdPath = Paths.get(this.toolArgs.xsdSourceDir(), rootElementName + ".xsd");
    File xsdFile = xsdPath.toFile();
    if (!xsdFile.exists() || !xsdFile.isFile()) {
      return null;
    }
    Schema schema = this.schemaFactory.newSchema(xsdFile);
    this.schemaCache.put(rootElementName, schema);
    return schema;
  }

  private ValidationResult validateFile(File file)
      throws SAXException, IOException, XMLStreamException {
    String rootElementName = getRootElementName(file);
    if (rootElementName == null) {
      throw new IllegalStateException("Could not determine root element.");
    }

    CollectingErrorHandler handler = new CollectingErrorHandler();
    Schema schema = getSchema(rootElementName);

    if (schema == null) {
      Path xsdPath = Paths.get(this.toolArgs.xsdSourceDir(), rootElementName + ".xsd");
      handler.addFatalError(
          String.format(
              "Schema file not found for root element '%s': %s", rootElementName, xsdPath));
      return handler.getResult();
    }

    Validator validator = schema.newValidator();
    validator.setErrorHandler(handler);

    try (InputStream inputStream = new FileInputStream(file)) {
      validator.validate(new StreamSource(inputStream));
    }
    return handler.getResult();
  }

  public void run() throws Exception {
    prepareSource();
    List<File> filesToValidate = findXmlFiles();
    if (filesToValidate.isEmpty()) {
      System.out.printf("No XML files found to validate in %s%n", this.bundlePath);
      System.exit(0);
    }

    createSchemaFactory();

    Map<String, ValidationResult> validationResults = new LinkedHashMap<>();
    Map<String, Exception> fatalErrors = new LinkedHashMap<>();

    for (File file : filesToValidate) {
      try {
        ValidationResult result = validateFile(file);
        validationResults.put(file.getPath(), result);
      } catch (Exception e) {
        fatalErrors.put(file.getPath(), e);
      }
    }

    System.out.printf("%n==================== Validation Report ====================%n");
    System.out.printf("Bundle directory: %s%n%n", this.bundlePath);

    boolean allValid = true;
    long totalErrorCount = 0;

    for (Map.Entry<String, ValidationResult> entry : validationResults.entrySet()) {
      String absoluteFilePath = entry.getKey();
      ValidationResult result = entry.getValue();
      Path relativePath = this.bundlePath.relativize(Paths.get(absoluteFilePath));

      System.out.printf("File: %s%n", relativePath);
      if (result.hasErrors()) {
        allValid = false;
        System.out.printf("  result: WITH_ERRORS%n");
        totalErrorCount +=
            result.notices().stream()
                .filter(n -> "error".equals(n.type()) || "fatalError".equals(n.type()))
                .count();
      } else {
        System.out.printf("  result: OK%n");
      }

      for (ValidationMessage notice : result.notices()) {
        System.err.printf("    [%s] %s%n", notice.type(), notice.exception());
      }
      System.out.printf("-------------------------------------------------------%n");
    }

    if (!fatalErrors.isEmpty()) {
      allValid = false;
      totalErrorCount += fatalErrors.size();
      System.out.printf("Fatal Errors Encountered:%n");
      for (Map.Entry<String, Exception> entry : fatalErrors.entrySet()) {
        String absoluteFilePath = entry.getKey();
        Exception e = entry.getValue();
        Path relativePath = this.bundlePath.relativize(Paths.get(absoluteFilePath));

        System.out.printf("File: %s%n", relativePath);
        System.err.printf("  [fatal] %s%n", e.getMessage());
        System.out.printf("-------------------------------------------------------%n");
      }
    }

    if (totalErrorCount > 0) {
      System.out.printf("%nValidation finished with %d errors.%n", totalErrorCount);
    } else {
      System.out.printf("%nAll policies validated successfully.%n");
    }

    System.exit(allValid ? 0 : 1);
  }

  public static void main(String[] args) throws Exception {
    ToolArgs toolArgs = parseArgs(args);
    var tool = new BundlePolicyValidator(toolArgs);
    try {
      tool.run();
    } finally {
      tool.cleanup();
    }
  }
}
