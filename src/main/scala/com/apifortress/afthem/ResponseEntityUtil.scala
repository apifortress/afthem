/*
 *   Copyright 2019 API Fortress
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *   @author Simone Pezzano
 *
 */

package com.apifortress.afthem

import java.nio.charset.StandardCharsets

import com.apifortress.afthem.messages.beans.HttpWrapper
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.exception.ExceptionUtils
import org.springframework.http.ResponseEntity

/**
  * Util to manipulate Spring response entities
  */
object ResponseEntityUtil {

  /**
    * Given a response in the form of an HttpWrapper, it produces a ResponseEntity off it
    * @param response a response in the form of an HttpWrapper
    * @return a response entity
    */
  def createEntity(response: HttpWrapper) : ResponseEntity[Array[Byte]] = {
    var envelopeBuilder = ResponseEntity.status(response.status)
    response.headers.foreach( header=> envelopeBuilder=envelopeBuilder.header(header.key,header.value))
    return envelopeBuilder.body(response.payload)
  }

  /**
    * Given a string to be used as a response body and a status code, it produces a ResponseEntity off it
    * @param data a string to be used as a response
    * @param status a status code
    * @return a response entity
    */
  def createEntity(data : String, status : Int, contentType: String) : ResponseEntity[Array[Byte]] = {
    return ResponseEntity.status(status).header("Content-Type",contentType)
                          .body(data.getBytes(StandardCharsets.UTF_8))
  }

  /**
    * Given an exception to be used as a response body and a status code, it produces a ResponseEntity off it
    * @param exception an exception
    * @param status a status code
    * @return a response entity
    */
  def createEntity(exception : Exception, status : Int) : ResponseEntity[Array[Byte]] = {
    return createEntity(exceptionToJSON(exception),status,"application/json")
  }

  /**
    * Converts an exception to a JSON message
    * @param e an exception
    * @return a JSON message
    */
  def exceptionToJSON(e : Exception): String = {
    return "{ \"status\": \"error\", \"message\": \""+StringEscapeUtils.escapeJavaScript(ExceptionUtils.getMessage(e))+"\"}\n"
  }

}