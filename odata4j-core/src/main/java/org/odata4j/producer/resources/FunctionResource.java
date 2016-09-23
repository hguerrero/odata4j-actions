package org.odata4j.producer.resources;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ContextResolver;

import org.odata4j.core.ODataConstants;
import org.odata4j.core.ODataHttpMethod;
import org.odata4j.core.ODataVersion;
import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityId;
import org.odata4j.core.OEntityIds;
import org.odata4j.core.OEntityKey;
import org.odata4j.core.OFunctionParameter;
import org.odata4j.core.OFunctionParameters;
import org.odata4j.core.OObject;
import org.odata4j.core.OSimpleObject;
import org.odata4j.edm.EdmCollectionType;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmEntityType;
import org.odata4j.edm.EdmFunctionImport;
import org.odata4j.edm.EdmFunctionParameter;
import org.odata4j.edm.EdmProperty.CollectionKind;
import org.odata4j.edm.EdmSimpleType;
import org.odata4j.edm.EdmType;
import org.odata4j.exceptions.MethodNotAllowedException;
import org.odata4j.exceptions.NotImplementedException;
import org.odata4j.format.FormatParser;
import org.odata4j.format.FormatParserFactory;
import org.odata4j.format.FormatType;
import org.odata4j.format.FormatWriter;
import org.odata4j.format.FormatWriterFactory;
import org.odata4j.format.Parameters;
import org.odata4j.format.Settings;
import org.odata4j.producer.BaseResponse;
import org.odata4j.producer.CollectionResponse;
import org.odata4j.producer.ComplexObjectResponse;
import org.odata4j.producer.EntitiesResponse;
import org.odata4j.producer.EntityResponse;
import org.odata4j.producer.ErrorResponse;
import org.odata4j.producer.InfinispanResponse;
import org.odata4j.producer.OBindingResolverExtension;
import org.odata4j.producer.OBindingResolverExtensions;
import org.odata4j.producer.ODataContext;
import org.odata4j.producer.ODataContextImpl;
import org.odata4j.producer.ODataProducer;
import org.odata4j.producer.PropertyResponse;
import org.odata4j.producer.QueryInfo;
import org.odata4j.producer.Responses;
import org.odata4j.producer.SimpleResponse;

/**
 * Handles function calls.
 *
 * <p>Unfortunately the OData URI scheme makes it
 * impossible to differentiate a function call "resource" from an EntitySet.
 * So, we hack:  EntitiesRequestResource and EntityRequestResource
 * delegates to this class if it determines that a function is being referenced.
 *
 * <ul>TODO:
 *   <li>function parameter facets (required, value ranges, etc).  For now, all
 *    validation is up to the function handler in the producer.
 *   <li>non-simple function parameter types
 *   <li>make sure this works for GET and POST
 */
public class FunctionResource extends BaseResource {

  private static final Logger log = Logger.getLogger(FunctionResource.class.getName());


  @GET
  @Produces({
      ODataConstants.APPLICATION_ATOM_XML_CHARSET_UTF8,
      ODataConstants.TEXT_JAVASCRIPT_CHARSET_UTF8,
      ODataConstants.APPLICATION_JAVASCRIPT_CHARSET_UTF8 })
  public Response callBoundFunction(
      @Context HttpHeaders httpHeaders,
      @Context UriInfo uriInfo,
      @Context ContextResolver<ODataProducer> producerResolver,
      @Context SecurityContext securityContext,
      @PathParam("entitySetName") String entitySetName,
      @PathParam("id") String id,
      @PathParam("navProp") String fqFunction,
      @QueryParam("$inlinecount") String inlineCount,
      @QueryParam("$top") String top,
      @QueryParam("$skip") String skip,
      @QueryParam("$filter") String filter,
      @QueryParam("$orderby") String orderBy,
      @QueryParam("$format") String format,
      @QueryParam("$callback") String callback,
      @QueryParam("$skiptoken") String skipToken,
      @QueryParam("$expand") String expand,
      @QueryParam("$select") String select) throws Exception {

    ODataProducer producer = producerResolver.getContext(ODataProducer.class);

    QueryInfo query = new QueryInfo(
        OptionsQueryParser.parseInlineCount(inlineCount),
        OptionsQueryParser.parseTop(top),
        OptionsQueryParser.parseSkip(skip),
        OptionsQueryParser.parseFilter(filter),
        OptionsQueryParser.parseOrderBy(orderBy),
        OptionsQueryParser.parseSkipToken(skipToken),
        OptionsQueryParser.parseCustomOptions(uriInfo),
        OptionsQueryParser.parseExpand(expand),
        OptionsQueryParser.parseSelect(select));

    int separatorPos = fqFunction.indexOf(".");
    String functionName = fqFunction.substring(separatorPos + 1);

    OEntityKey key = null;
    if (id != null){
      key = OEntityKey.parse(id);
    }

    if (producer.getMetadata().containsEdmFunctionImport(functionName)) {
      // functions that return collections of entities should support the
      // same set of query options as entity set queries so give them everything.
      return callFunction(
          ODataHttpMethod.GET,
          httpHeaders,
          uriInfo,
          securityContext,
          producer,
          functionName,
          format,
          callback,
          query,
          entitySetName,
          key,
          null);
    }

    return Response.status(Status.NOT_FOUND).build();
  }

  @POST
  @Produces({
      ODataConstants.APPLICATION_ATOM_XML_CHARSET_UTF8,
      ODataConstants.TEXT_JAVASCRIPT_CHARSET_UTF8,
      ODataConstants.APPLICATION_JAVASCRIPT_CHARSET_UTF8 })
  public Response callBoundAction(
      @Context HttpHeaders httpHeaders,
      @Context UriInfo uriInfo,
      @Context ContextResolver<ODataProducer> producerResolver,
      @Context SecurityContext securityContext,
      @PathParam("entitySetName") String entitySetName,
      @PathParam("id") String id,
      @PathParam("navProp") String fqAction,
      @QueryParam("$inlinecount") String inlineCount,
      @QueryParam("$top") String top,
      @QueryParam("$skip") String skip,
      @QueryParam("$filter") String filter,
      @QueryParam("$orderby") String orderBy,
      @QueryParam("$format") String format,
      @QueryParam("$callback") String callback,
      @QueryParam("$skiptoken") String skipToken,
      @QueryParam("$expand") String expand,
      @QueryParam("$select") String select,
      InputStream payload) throws Exception {
    ODataProducer producer = producerResolver.getContext(ODataProducer.class);

    QueryInfo query = new QueryInfo(
        OptionsQueryParser.parseInlineCount(inlineCount),
        OptionsQueryParser.parseTop(top),
        OptionsQueryParser.parseSkip(skip),
        OptionsQueryParser.parseFilter(filter),
        OptionsQueryParser.parseOrderBy(orderBy),
        OptionsQueryParser.parseSkipToken(skipToken),
        OptionsQueryParser.parseCustomOptions(uriInfo),
        OptionsQueryParser.parseExpand(expand),
        OptionsQueryParser.parseSelect(select));

    int separatorPos = fqAction.indexOf(".");
    String functionName = fqAction.substring(separatorPos + 1);
    OEntityKey key = OEntityKey.parse(id);

    if (producer.getMetadata().containsEdmFunctionImport(functionName)) {
      // functions that return collections of entities should support the
      // same set of query options as entity set queries so give them everything.
      return callFunction(
          ODataHttpMethod.POST,
          httpHeaders,
          uriInfo,
          securityContext,
          producer,
          functionName,
          format,
          callback,
          query,
          entitySetName,
          key,
          payload);
    }

    return Response.status(Status.NOT_FOUND).build();
  }

  /**
   * Handles function call resource access by gathering function call info from
   * the request and delegating to the producer.
   */
  public static Response callFunction(
      ODataHttpMethod callingMethod,
      HttpHeaders httpHeaders,
      UriInfo uriInfo,
      SecurityContext securityContext,
      ODataProducer producer,
      String functionName,
      String format,
      String callback,
      QueryInfo queryInfo,
      InputStream payload) throws Exception {
    return callFunction(callingMethod, httpHeaders, uriInfo, securityContext, producer, functionName, format, callback, queryInfo, null, null, payload);
  }
  /**
   * Handles function call resource access by gathering function call info from
   * the request and delegating to the producer.
   */
  @SuppressWarnings("rawtypes")
  public static Response callFunction(
      ODataHttpMethod callingMethod,
      HttpHeaders httpHeaders,
      UriInfo uriInfo,
      SecurityContext securityContext,
      ODataProducer producer,
      String functionName,
      String format,
      String callback,
      QueryInfo queryInfo,
      String boundEntitySetName,
      OEntityKey boundEntityKey,
      InputStream payload) throws Exception {

    // do we have this function?
    EdmType bindingType = null;
    if (boundEntitySetName != null) {
      EdmEntitySet entitySet = producer.getMetadata().findEdmEntitySet(boundEntitySetName);
      if (entitySet != null){
        bindingType = entitySet.getType();
        if (boundEntityKey == null) {
          // The binding type is a collection as we don't have the entity key
          bindingType = new EdmCollectionType(CollectionKind.Collection, bindingType);
        }
      }
    }
    EdmFunctionImport function = producer.getMetadata().findEdmFunctionImport(functionName, bindingType);
    if (function == null) {
      return Response.status(Status.NOT_FOUND).build();
    }

    ODataContext context = ODataContextImpl.builder().aspect(httpHeaders).aspect(securityContext).aspect(producer).build();

    // Check HTTP method
    String expectedHttpMethodString = function.getHttpMethod();
    if (expectedHttpMethodString != null && !"".equals(expectedHttpMethodString)) {
      ODataHttpMethod expectedHttpMethod = ODataHttpMethod.fromString(expectedHttpMethodString);
      if (expectedHttpMethod != callingMethod) {
        throw new MethodNotAllowedException("Method " + callingMethod + " not allowed, expecting " + expectedHttpMethodString);
      }
    }

    // Prepare binding resolver
    OBindingResolverExtension resolverExtension = producer.findExtension(OBindingResolverExtension.class);
    if (resolverExtension == null){
      resolverExtension = OBindingResolverExtensions.getPartialBindingResolver();
    }

    // First take the parameters from the query
    Map<String, OFunctionParameter> parameters = getFunctionParameters(function, queryInfo.customOptions, resolverExtension, context, queryInfo);

    // Then try the payload if any
    if (payload != null) {
        final InputStream payloadForProducerCallFunction = payload;
        // put payload into function parameters in a hard way, producer need to take care about it
        OFunctionParameter ofp = new OFunctionParameter() {
            @Override
            public EdmType getType() {
                return EdmSimpleType.BINARY;
            }

            @Override
            public OObject getValue() {
                return new OSimpleObjectFunctionPayload(payloadForProducerCallFunction, EdmSimpleType.BINARY);
            }

            @Override
            public String getName() {
                return "payload";
            }
        };
        // pass payload to producer's callFunction() so they can access it
        parameters.put("payload", ofp);
    }

    // Use the bound parameter if any
    if (boundEntitySetName != null && function.isBindable()) {

      OFunctionParameter boundParam = resolverExtension.resolveBindingParameter(context, function, boundEntitySetName, boundEntityKey, queryInfo);
      parameters.put(boundParam.getName(), boundParam);
    }

    // Execute the call
    BaseResponse response = producer.callFunction(context, function, parameters, queryInfo);

    if (response == null) {
      return Response.status(Status.NO_CONTENT).build();
    }

    ODataVersion version = ODataConstants.DATA_SERVICE_VERSION;

    StringWriter sw = new StringWriter();
    FormatWriter<?> fwBase;

    // hmmh...we are missing an abstraction somewhere..
    if (response instanceof ComplexObjectResponse) {
      FormatWriter<ComplexObjectResponse> fw =
          FormatWriterFactory.getFormatWriter(
              ComplexObjectResponse.class,
              httpHeaders.getAcceptableMediaTypes(),
              format,
              callback);

      fw.write(uriInfo, sw, (ComplexObjectResponse) response);
      fwBase = fw;
    } else if (response instanceof CollectionResponse) {
      CollectionResponse<?> collectionResponse = (CollectionResponse<?>) response;

      if (collectionResponse.getCollection().getType() instanceof EdmEntityType) {
        FormatWriter<EntitiesResponse> fw = FormatWriterFactory.getFormatWriter(
            EntitiesResponse.class,
            httpHeaders.getAcceptableMediaTypes(),
            format,
            callback);

        // collection of entities.
        // Does anyone else see this in the v2 spec?  I sure don't.  This seems
        // reasonable though given that inlinecount and skip tokens might be included...
        ArrayList<OEntity> entities = new ArrayList<OEntity>(collectionResponse.getCollection().size());
        Iterator iter = collectionResponse.getCollection().iterator();
        while (iter.hasNext()) {
          entities.add((OEntity) iter.next());
        }
        EntitiesResponse er = Responses.entities(entities,
            collectionResponse.getEntitySet(),
            collectionResponse.getInlineCount(),
            collectionResponse.getSkipToken());
        fw.write(uriInfo, sw, er);
        fwBase = fw;
      } else {
        // non-entities
        FormatWriter<CollectionResponse> fw = FormatWriterFactory.getFormatWriter(
            CollectionResponse.class,
            httpHeaders.getAcceptableMediaTypes(),
            format,
            callback);
        fw.write(uriInfo, sw, collectionResponse);
        fwBase = fw;
      }
    } else if (response instanceof EntitiesResponse) {
      FormatWriter<EntitiesResponse> fw = FormatWriterFactory.getFormatWriter(
          EntitiesResponse.class,
          httpHeaders.getAcceptableMediaTypes(),
          format,
          callback);

      fw.write(uriInfo, sw, (EntitiesResponse) response);
      fwBase = fw;
    } else if (response instanceof PropertyResponse) {
      FormatWriter<PropertyResponse> fw =
          FormatWriterFactory.getFormatWriter(
              PropertyResponse.class,
              httpHeaders.getAcceptableMediaTypes(),
              format,
              callback);

      fw.write(uriInfo, sw, (PropertyResponse) response);
      fwBase = fw;
    } else if (response instanceof SimpleResponse) {
      FormatWriter<SimpleResponse> fw =
          FormatWriterFactory.getFormatWriter(
              SimpleResponse.class,
              httpHeaders.getAcceptableMediaTypes(),
              format,
              callback);

      fw.write(uriInfo, sw, (SimpleResponse) response);
      fwBase = fw;
    } else if (response instanceof EntityResponse) {
      FormatWriter<EntityResponse> fw =
          FormatWriterFactory.getFormatWriter(
              EntityResponse.class,
              httpHeaders.getAcceptableMediaTypes(),
              format,
              callback);

      fw.write(uriInfo, sw, (EntityResponse) response);
      fwBase = fw;
    } else if (response instanceof InfinispanResponse) {

        // response coming from InfinispanProducer
        final InfinispanResponse ir = (InfinispanResponse) response;

        if (ir.getStatus().equals(Status.NO_CONTENT)) {
            // 204 set by producer, HTTP DELETE (remove operation), return the entity for the last time
            return Response.ok(null,
                    "application/json;charset=UTF-8")
                    .status(Status.NO_CONTENT)
                    .header(ODataConstants.Headers.DATA_SERVICE_VERSION, version.asString)
                    .build();
        }

        if (ir.getStatus().equals(Status.NOT_FOUND)) {
            // 404 set by producer, Infinispan cache returned null, entry not found
            return Response.ok(handleIspnStream("Entry was not found."),
                    "application/json;charset=UTF-8")
                    .status(Status.NOT_FOUND)
                    .header(ODataConstants.Headers.DATA_SERVICE_VERSION, version.asString)
                    .build();
        }

        // case for putting entries
        // [ODATA SPEC] set up "location" header with link on which is entry ready for access
        if (ir.getStatus().equals(Status.CREATED)) {

            final String locationOfCreatedEntry = uriInfo.getBaseUri().toString() +
                    function.getEntitySet().getName() + "_get?key='" + parameters.get("key").getValue() + "'";

            StreamingOutput stream;
            if (ir.getValue() == null) {
                // IGNORE_RETURN_VALUES flag enabled
                stream = handleIspnStream("Entry created -- ready for access here: " + locationOfCreatedEntry);
            } else {
                // return full, just created entry
                stream = handleIspnStream(ir.getValue().toString());
            }

            return Response.ok(stream, "application/json;charset=UTF-8")
                    .status(Status.CREATED)
                    .header(ODataConstants.Headers.DATA_SERVICE_VERSION, version.asString)
                    .header("location", locationOfCreatedEntry)
                    .build();
        }

        Response responseForReturn = Response.ok(handleIspnStream(ir.getValue().toString()),
                "application/json;charset=UTF-8")
                .status(ir.getStatus())
                .header(ODataConstants.Headers.DATA_SERVICE_VERSION, version.asString)
                .build();

        // return immediately
        return responseForReturn;

    } else if (response instanceof ErrorResponse) {
        ErrorResponse errorResponse = (ErrorResponse) response;
        // returning 500
        // TODO -- get innerError as well? (+ try it)
        Response finalResponse = Response.serverError()
                .entity("Error: " + errorResponse.getError().getMessage())
                .header(ODataConstants.Headers.DATA_SERVICE_VERSION, version.asString)
                .build();
        return finalResponse;

    } else {
        // TODO add in other response types.
        throw new NotImplementedException("Unknown BaseResponse type: " + response.getClass().getName());
    }

    String entity = sw.toString();
    return Response.ok(entity, fwBase.getContentType())
        .header(ODataConstants.Headers.DATA_SERVICE_VERSION, version.asString)
        .build();
  }

    /**
     * Specific handling of streams for Infinispan.
     *
     * @param stringToWrite this string will be written to Writer.
     * @return stream for setting it to the Response.
     */
  private static StreamingOutput handleIspnStream(final String stringToWrite) {
      StreamingOutput stream = new StreamingOutput() {
          @Override
          public void write(OutputStream os) throws IOException,
                  WebApplicationException {
              Writer writer = new BufferedWriter(new OutputStreamWriter(os));
              writer.write(stringToWrite);
              writer.flush();
              writer.close(); // important for performance, don't let open stream, we will probably NOT send anything else
          }
      };
      return stream;
  }

  /**
   * Takes a Map<String,String> filled with the request URIs custom parameters and
   * turns them into a map of strongly-typed OFunctionParameter objects.
   *
   * @param function  the current function
   * @param opts  the query string
   * @param resolver  a binding resolver
   * @param context  the current context
   * @param queryInfo  the current queryInfo
   * @return a map of function parameters
   */
  private static Map<String, OFunctionParameter> getFunctionParameters(
      EdmFunctionImport function,
      Map<String, String> opts,
      OBindingResolverExtension resolver,
      ODataContext context,
      QueryInfo queryInfo) {
    Map<String, OFunctionParameter> m = new HashMap<String, OFunctionParameter>();
    for (EdmFunctionParameter p : function.getParameters()) {
      String val = opts.get(p.getName());
      if (function.isBindable() && p.isBound() && val != null){
        String entitySetName = null;
        OEntityKey entityKey = null;
        if (p.getType() instanceof EdmCollectionType){
          entitySetName = val;
        } else {
          OEntityId entityId = OEntityIds.parse(val);
          entitySetName = entityId.getEntitySetName();
          entityKey = entityId.getEntityKey();
        }
        m.put(p.getName(), resolver.resolveBindingParameter(context, function, entitySetName, entityKey, queryInfo));
      } else {
        m.put(p.getName(), val == null ? null : OFunctionParameters.parse(p.getName(), p.getType(), val));
      }
    }
    return m;
  }

  /**
   * Takes the payload and turns it into a map of strongly-typed
   * OFunctionParameter objects.
   *
   * @param dataServices  the service metadata
   * @param function  the function being called
   * @param payload  the post payload
   */
  private static Map<String, OFunctionParameter> getFunctionParameters(EdmDataServices dataServices, EdmFunctionImport function, InputStream payload) {
    Map<String, OFunctionParameter> m = new HashMap<String, OFunctionParameter>();
    Settings settings = new Settings(ODataConstants.DATA_SERVICE_VERSION, dataServices, null, null, false, null, function);
    FormatParser<Parameters> parser = FormatParserFactory.getParser(Parameters.class, FormatType.JSON, settings);
    Parameters params = parser.parse(new InputStreamReader(payload));
    for (OFunctionParameter param : params.getParameters()){
      m.put(param.getName(), param);
    }
    return m;
  }

  public static class OSimpleObjectFunctionPayload implements OSimpleObject {

      private Object value;
      private EdmSimpleType type;

      public OSimpleObjectFunctionPayload(Object value, EdmSimpleType type) {
          this.value = value;
          this.type = type;
      }

      /**
       * Gets the value.
       * Is carrying payload, which is encoded JSON string for wire transfer by http client.
       */
      @Override
      public Object getValue() {
          return value;
      }

      /**
       * Gets the edm type, which will be an edm simple type
       */
      @Override
      public EdmSimpleType getType() {
          return type;
      }
  }
}