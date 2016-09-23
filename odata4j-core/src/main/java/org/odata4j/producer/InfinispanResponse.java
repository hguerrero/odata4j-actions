package org.odata4j.producer;

import javax.ws.rs.core.Response.Status;

import org.odata4j.edm.EdmSimpleType;

/**
 * An <code>InfinispanResponse</code> is a response to a client request expecting a single EdmSimpleType value.
 *
 * <p>The {@link org.odata4j.producer.Responses} static factory class can be used to create <code>InfinispanResponse</code> instances.</p>
 */
public interface InfinispanResponse extends BaseResponse {

  /**
   * Gets the type of the value
   * @return the type of the value
   */
  EdmSimpleType<?> getType();

  /**
   * Gets the value.
   *
   * @return the property value
   */
  Object getValue();

  /**
   * Gets the (optional) name of the value
   *  
   * @return the property name if available or null
   */
  String getName();

  /**
   * Gets status which was set by producer and will be returned to the consumer.
   *
   * @return status of the response
   */
  Status getStatus();

}
