import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class BundlePolicyValidator {
  public BundlePolicyValidator() {}

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

  private record ToolArgs(String sourceDir, String xsdFile, boolean insecure) {}

  private static ToolArgs parseArgs(String[] args) {
    String sourceDir = null;
    String schemaFile = null;
    boolean insecure = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--insecure".equals(arg)) {
        insecure = true;
      } else if ("--source".equals(arg)) {
        if (i + 1 < args.length) {
          sourceDir = args[++i];
        } else {
          System.err.println("Error: missing directory for --source option.");
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

    if (schemaFile == null || sourceDir == null) {
      System.err.println(
          "Usage: java BundlePolicyValidator --xsd <schema.xsd> --source <directory> [--insecure]");
      System.exit(1);
    }

    File source = new File(sourceDir);
    if (!source.exists() || !source.isDirectory()) {
      System.err.printf("Error: source '%s' is not an existing directory.%n", sourceDir);
      System.exit(1);
    }
    return new ToolArgs(sourceDir, schemaFile, insecure);
  }

  private static List<File> findXmlFiles(String sourceDir) throws IOException {
    try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
          .map(Path::toFile)
          .toList();
    }
  }

  public static void main(String[] args) throws Exception {
    ToolArgs toolArgs = parseArgs(args);

    SchemaFactory sf = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
    sf.setFeature("http://apache.org/xml/features/validation/cta-full-xpath-checking", true);
    if (toolArgs.insecure()) {
      sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
    }
    Schema schema = sf.newSchema(new File(toolArgs.xsdFile()));

    List<File> filesToValidate = findXmlFiles(toolArgs.sourceDir());
    if (filesToValidate.isEmpty()) {
      System.out.println("No XML files found in the specified directory.");
      System.exit(0);
    }

    boolean allValid = true;

    for (File file : filesToValidate) {
      System.out.printf("Validating %s...%n", file.getPath());
      var handler = new CollectingErrorHandler();
      Validator validator = schema.newValidator();
      validator.setErrorHandler(handler);

      try (InputStream inputStream = new FileInputStream(file)) {
        validator.validate(new StreamSource(inputStream));
        ValidationResult result = handler.getResult();
        if (result.hasErrors()) {
          allValid = false;
          System.out.printf("result: WITH_ERRORS%n");
        } else {
          System.out.printf("result: OK%n");
        }
        for (ValidationMessage notice : result.notices()) {
          System.err.printf("[%s] %s%n", notice.type(), notice.exception());
        }
      } catch (Exception e) {
        allValid = false;
        System.err.printf("[fatal] %s%n", e.getMessage());
        System.out.printf("result: FATAL_ERROR%n");
      }
      System.out.printf("----%n");
    }

    System.exit(allValid ? 0 : 1);
  }
}
