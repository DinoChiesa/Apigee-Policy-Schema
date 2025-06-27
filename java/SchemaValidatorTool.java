import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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
    private int errorCount = 0;
    private int warningCount = 0;

    private static void logResult(String msg) {
      System.out.printf("result: %s\n" + msg);
    }

    private static void log(String type, String msg) {
      System.err.printf("[%s] %s", type, msg);
    }

    private static void log(String type, SAXParseException e) {
      log(
          type,
          String.format("%s (%d:%d)", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
    }

    // AI! convert each of the following three methods. Rather than
    // simply incrementing a counter and immediately logging the messages, the
    // method should add the Exception to a list. There should be one list
    // for warnings, and a second list for errors (fatal or otherwise).

    @Override
    public void warning(SAXParseException exception) throws SAXException {
      log("warning", exception);
      warningCount++;
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
      log("error", exception);
      errorCount++;
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
      log("fatal", exception);
      errorCount++;
    }
  }

  public static void main(String[] args) throws Exception {
    String fileName = null;
    String schemaFile = null;
    boolean insecure = false;

    for (String str : args) {
      if ("--insecure".equals(str)) {
        insecure = true;
      }
      if (str.startsWith("--xml=")) {
        fileName = str.replaceFirst("--xml=", "");
      } else if (str.startsWith("--xsd=")) {
        schemaFile = str.replaceFirst("--xsd=", "");
      }
    }

    if (schemaFile == null) {
      logError("error", "specify schema via --schema=[SCHEMA]");
      System.exit(1);
    }

    InputStream inputStream;

    if (readStdin) {
      inputStream = System.in;
    } else {
      inputStream = new FileInputStream(fileName);
    }

    var handler = new CollectingErrorHandler();

    try {
      SchemaFactory sf = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
      if (insecure) {
        sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      }

      Schema schema = sf.newSchema(new File(schemaFile));
      Validator validator = schema.newValidator();

      validator.setErrorHandler(handler);
      validator.validate(new StreamSource(inputStream));

      logResult(handler.withErrors ? "WITH_ERRORS" : "OK");

    } catch (Exception e) {
      logError("fatal", e.getMessage());
      logResult("FATAL_ERROR");

      handler.withErrors = true;
    }

    System.exit(handler.withErrors ? 1 : 0);
  }
}
