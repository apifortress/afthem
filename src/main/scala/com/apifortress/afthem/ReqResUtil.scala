/**
  * Copyright 2019 API Fortress
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *
  * @author Simone Pezzano
  */
package com.apifortress.afthem

import javax.servlet.http.HttpServletRequest
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream
import org.apache.http.HttpResponse
import scala.collection.mutable
import java.io.InputStream
import java.net.URL

import com.apifortress.afthem.messages.{BaseMessage, WebParsedRequestMessage, WebParsedResponseMessage}
import com.apifortress.afthem.messages.beans.Header

/**
  * Utils to handle requests and responses
  */
object ReqResUtil {

  /**
    * The name of the content-length header
    */
  val HEADER_CONTENT_LENGTH : String = "content-length"

  /**
    * The name of the content-type header
    */
  val HEADER_CONTENT_TYPE : String = "content-type"

  /**
    * The name of the host header
    */
  val HEADER_HOST : String = "host"

  /**
    * Parses servlet headers into a list of tuples and collects a map of interesting headers
    * @param request an HttpServletRequest to parse the headers from
    * @return a tuple made up of (list of headers, interesting headers)
    */
  def parseHeaders(request: HttpServletRequest, discardHeaders : mutable.MutableList[String] = mutable.MutableList.empty[String]) : (List[Header],Map[String,Any]) = {
    val headers = new mutable.MutableList[Header]
    val interestingHeaders = new mutable.HashMap[String,Any]

    val headerNames = request.getHeaderNames
    while(headerNames.hasMoreElements){
      val headerName = headerNames.nextElement()
      headerName match {
        case HEADER_CONTENT_LENGTH =>
          interestingHeaders.put(HEADER_CONTENT_LENGTH,request.getHeader(HEADER_CONTENT_LENGTH).toInt)
        case HEADER_CONTENT_TYPE =>
          interestingHeaders.put(HEADER_CONTENT_TYPE,request.getHeader(HEADER_CONTENT_TYPE))
        case _ =>
      }
      if (!discardHeaders.contains(headerName.toLowerCase))
        headers+=new Header(headerName,request.getHeader(headerName))
    }
    return (headers.toList,interestingHeaders.toMap)
  }

  /**
    * Parses response headers into a list of tuples and collects a map of interesting headers
    * @param response an HttpResponse object
    * @return a tuple made up of (list of headers, interesting headers)
    */
  def parseHeaders(response: HttpResponse) : (List[Header],Map[String,Any]) = {
    val headers = new mutable.MutableList[Header]
    val interestingHeaders = new mutable.HashMap[String,Any]
    response.getAllHeaders.foreach{ header =>
      headers+=new Header(header.getName,header.getValue)
      header.getName match {
        case HEADER_CONTENT_LENGTH =>
          interestingHeaders.put(HEADER_CONTENT_LENGTH, header.getValue.toInt)
        case HEADER_CONTENT_TYPE =>
          interestingHeaders.put(HEADER_CONTENT_TYPE, header.getValue)
        case _ =>
      }
    }
    return (headers.toList,interestingHeaders.toMap)
  }

  /**
    * Reads a payload from an input stream. It will conform to what's told in the content-length header,
    * if present, or continue to EOF
    * @param inputStream the input stream to read from
    * @param contentLength an optional content-length header value
    * @return a byte array
    */
  def readPayload(inputStream: InputStream, contentLength: Option[Any]) : Array[Byte] = {
    val boundedInputStream = new BoundedInputStream(inputStream)
    var data :Array[Byte] = null
    try {
      if (contentLength.isDefined)
        // If content-length is defined, then we conform to it
        data = IOUtils.readFully(boundedInputStream, contentLength.get.asInstanceOf[Int])
      else
        // Otherwise we read to EOF
        data = IOUtils.toByteArray(boundedInputStream)
    }finally {
      // We want to make sure we're closing the streams
      boundedInputStream.close()
      inputStream.close()
    }
    return data
  }

  /**
    * Given a URL in the form of a string, it extracts the host part and returns it
    * @param url a URL in the form of a string
    * @return the host
    */
  def extractHost(url : String) : String = new URL(url).getHost

  /**
    * Extracts the "accept" header from an HttpServletRequest
    * @param request the request
    * @param default the default value, in case no "accept" header is present
    * @return the value of the accept header
    */
  def extractAccept(request : HttpServletRequest, default : String = "application/json") : String = {
    val accept = request.getHeader("accept")
    if(accept == null)
      return default
    return accept
  }

  /**
    * Extracts the "accept" header from either a WebParsedRequestMessage or a WebParsedResponseMessage
    * @param message the message
    * @param default the default value in case no "accept" header is present
    * @return the value of the accept header
    */
  def extractAcceptFromMessage(message : BaseMessage, default : String = "application/json") : String = {
    val request = message match {
      case message : WebParsedRequestMessage => message.asInstanceOf[WebParsedRequestMessage].request
      case message : WebParsedResponseMessage => message.asInstanceOf[WebParsedResponseMessage].request
    }
    if(request == null)
      return default
    val accept = request.getHeader("accept")
    if (accept == null)
      return default
    return accept
  }

  /**
    * Determines a sanitized mime type, based off a content-type
    * @param contentType a content-type
    * @return a sanitized mime type
    */
  def determineMimeFromContentType(contentType : String) : String = {
    if(contentType.contains("json"))
      return "application/json"
    if(contentType.contains("xml"))
      return "text/xml"
    return "text/plain"
  }
}
