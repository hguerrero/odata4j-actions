package org.odata4j.format.json;

import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.odata4j.core.OFunctionParameter;
import org.odata4j.core.OFunctionParameters;
import org.odata4j.core.OSimpleObject;
import org.odata4j.core.OSimpleObjects;
import org.odata4j.edm.EdmFunctionParameter;
import org.odata4j.edm.EdmSimpleType;
import org.odata4j.edm.EdmType;
import org.odata4j.exceptions.NotImplementedException;
import org.odata4j.format.FormatParser;
import org.odata4j.format.Parameters;
import org.odata4j.format.Settings;
import org.odata4j.format.json.JsonStreamReaderFactory.JsonStreamReader;
import org.odata4j.format.json.JsonStreamReaderFactory.JsonStreamReader.JsonEvent;

public class JsonParametersFormatParser extends JsonFormatParser implements FormatParser<Parameters> {

  public JsonParametersFormatParser(Settings settings) {
    super(settings);
  }

  @Override
  public Parameters parse(Reader reader) {
    JsonStreamReader jsr = JsonStreamReaderFactory.createJsonStreamReader(reader);
    try {
      ParametersImpl parameters = new ParametersImpl();

      if (jsr.hasNext()){

        // skip the StartObject event
        ensureStartObject(jsr.nextEvent());
  
        while (jsr.hasNext()) {
          JsonEvent event = jsr.nextEvent();
          if (event.isStartProperty()) {
            String parameterName = event.asStartProperty().getName();
            EdmFunctionParameter efp = parseFunction.getParameter(parameterName);
            if (efp != null) {
              OFunctionParameter param = readParameter(efp, parameterName, jsr);
              if (param != null) {
                parameters.addParameter(param);
              }
            }
          } else if (event.isEndObject()) {
            break;
          }
        }
      }
      return parameters;

      // no interest in the closing events
    } finally {
      jsr.close();
    }
  }

  protected OFunctionParameter readParameter(EdmFunctionParameter efp, String paramName, JsonStreamReader jsr) {

    EdmType type = efp.getType();
    JsonEvent event = jsr.nextEvent();

    if (type.isSimple()) {
      OSimpleObject<?> object =  OSimpleObjects.create((EdmSimpleType<?>)type, event.asEndProperty().getValue());
      OFunctionParameter functionParameter = OFunctionParameters.create(paramName, object);
      return functionParameter;
    } 

    throw new NotImplementedException("Using type " + type.getClass().getName() + " as parameter in a function/action is not supported");

  }
  
  static class ParametersImpl implements Parameters {

    private Map<String, OFunctionParameter> parameters = new HashMap<String, OFunctionParameter>();
    
    @Override
    public Collection<OFunctionParameter> getParameters() {
      return parameters.values();
    }
    
    public void addParameter(OFunctionParameter parameter) {
      parameters.put(parameter.getName(), parameter);
    }
  }
}
