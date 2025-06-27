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

  static class CollectingErrorHandler implements ErrorHandler {
    private final List<SAXParseException> warnings = new ArrayList<>();
    private final List<SAXParseException> errors = new ArrayList<>();

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
      warnings.add(exception);
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
      errors.add(exception);
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
      errors.add(exception);
    }

    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    public List<SAXParseException> getErrors() {
      return errors;
    }

    public List<SAXParseException> getWarnings() {
      return warnings;
    }
  }

  public static void main(String[] args) throws Exception {
    String fileName = null;
    String schemaFile = null;
    boolean insecure = false;

    for (String str : args) {
      if ("--insecure".equals(str)) {
        insecure = true;
      } else if (str.startsWith("--xml=")) {
        fileName = str.replaceFirst("--xml=", "");
      } else if (str.startsWith("--xsd=")) {
        schemaFile = str.replaceFirst("--xsd=", "");
      }
    }

    if (schemaFile == null || fileName == null) {
      System.err.println(
          "Usage: java SchemaValidatorTool --xsd=<schema.xsd> --xml=<doc.xml> [--insecure]");
      System.exit(1);
    }

    InputStream inputStream = new FileInputStream(fileName);

    var handler = new CollectingErrorHandler();
    boolean fatal = false;

    try {
      SchemaFactory sf = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
      if (insecure) {
        sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      }

      Schema schema = sf.newSchema(new File(schemaFile));
      Validator validator = schema.newValidator();

      validator.setErrorHandler(handler);
      validator.validate(new StreamSource(inputStream));

      if (handler.hasErrors()) {
        CollectingErrorHandler.logResult("WITH_ERRORS");
        for (SAXParseException e : handler.getErrors()) {
          CollectingErrorHandler.log("error", e);
        }
      } else {
        CollectingErrorHandler.logResult("OK");
      }
      for (SAXParseException e : handler.getWarnings()) {
        CollectingErrorHandler.log("warning", e);
      }

    } catch (Exception e) {
      CollectingErrorHandler.log("fatal", e.getMessage());
      CollectingErrorHandler.logResult("FATAL_ERROR");
      fatal = true;
    }

    System.exit((handler.hasErrors() || fatal) ? 1 : 0);
  }
}
