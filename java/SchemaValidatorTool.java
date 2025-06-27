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

    private static void logResult(String msg) {
      System.out.printf("result: %s\n", msg);
    }

    private static void log(String type, String msg) {
      System.err.printf("[%s] %s\n", type, msg);
    }

    private static void log(String type, SAXParseException e) {
      log(
          type,
          String.format("%s (%d:%d)", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
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

    InputStream inputStream = new FileInputStream(toolArgs.xmlFile());

    var handler = new CollectingErrorHandler();
    boolean fatal = false;

    try {
      SchemaFactory sf = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
      if (toolArgs.insecure()) {
        sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      }

      Schema schema = sf.newSchema(new File(toolArgs.xsdFile()));
      Validator validator = schema.newValidator();

      validator.setErrorHandler(handler);
      validator.validate(new StreamSource(inputStream));

      ValidationResult result = handler.getResult();

      if (result.hasErrors()) {
        CollectingErrorHandler.logResult("WITH_ERRORS");
      } else {
        CollectingErrorHandler.logResult("OK");
      }
      for (ValidationMessage notice : result.notices()) {
        CollectingErrorHandler.log(notice.type(), notice.exception());
      }

    } catch (Exception e) {
      CollectingErrorHandler.log("fatal", e.getMessage());
      CollectingErrorHandler.logResult("FATAL_ERROR");
      fatal = true;
    }

    System.exit((handler.getResult().hasErrors() || fatal) ? 1 : 0);
  }
}
