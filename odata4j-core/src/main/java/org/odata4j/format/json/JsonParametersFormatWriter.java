package org.odata4j.format.json;

import java.io.Writer;

import javax.ws.rs.core.UriInfo;

import org.odata4j.core.ODataConstants;
import org.odata4j.core.OFunctionParameter;
import org.odata4j.core.OSimpleObject;
import org.odata4j.format.Parameters;

public class JsonParametersFormatWriter extends JsonFormatWriter<Parameters> {

  public JsonParametersFormatWriter(String jsonpCallback) {
    super(jsonpCallback);
  }

  @Override
  protected void writeContent(UriInfo uriInfo, JsonWriter jw, Parameters parameters) {
    boolean isFirst = true;
    jw.startObject();
    for (OFunctionParameter param : parameters.getParameters()) {
      if (isFirst) {
        isFirst = false;
      } else {
        jw.writeSeparator();
      }

      jw.writeName(param.getName());
      if (param.getType().isSimple()){
        writeValue(jw, param.getType(), ((OSimpleObject<?>)param.getValue()).getValue());
      } 
    }
    jw.endObject();
  }

  @Override
  public void write(UriInfo uriInfo, Writer w, Parameters target) {

    JsonWriter jw = new JsonWriter(w);
    if (getJsonpCallback() != null) {
      jw.startCallback(getJsonpCallback());
    }

    writeContent(uriInfo, jw, target);
  }
  
  /**
   * Returns the content-type. According to OData specification,
   * function parameters must be send in a verbose-json mode. 
   * 
   */
  @Override
  public String getContentType() {
    return getJsonpCallback() == null
        ? ODataConstants.APPLICATION_JAVASCRIPT_VERBOSE_CHARSET_UTF8
        : ODataConstants.TEXT_JAVASCRIPT_CHARSET_UTF8;
  }
}
