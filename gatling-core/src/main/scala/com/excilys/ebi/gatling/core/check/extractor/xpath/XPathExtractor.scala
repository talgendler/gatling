/**
 * Copyright 2011-2013 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.core.check.extractor.xpath

import java.io.{ InputStream, StringReader }

import scala.collection.JavaConversions.asScalaBuffer

import org.jaxen.dom.DOMXPath
import org.w3c.dom.{ Document, Node }
import org.xml.sax.{ EntityResolver, InputSource }

import com.excilys.ebi.gatling.core.check.extractor.Extractor
import com.excilys.ebi.gatling.core.util.IOHelper

import javax.xml.parsers.{ DocumentBuilder, DocumentBuilderFactory }

object XPathExtractor {

	System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.xerces.jaxp.SAXParserFactoryImpl")
	System.setProperty("javax.xml.parsers.DOMParserFactory", "org.apache.xerces.jaxp.DOMParserFactoryImpl")
	private val factory = DocumentBuilderFactory.newInstance
	factory.setExpandEntityReferences(false)
	factory.setNamespaceAware(true)

	val noopEntityResolver = new EntityResolver {
		def resolveEntity(publicId: String, systemId: String) = new InputSource(new StringReader(""))
	}

	val parserHolder = new ThreadLocal[DocumentBuilder] {
		override def initialValue: DocumentBuilder = {
			val documentBuilder = factory.newDocumentBuilder
			documentBuilder.setEntityResolver(noopEntityResolver)
			documentBuilder
		}
	}

	def apply(inputStream: Option[InputStream]): XPathExtractor = {
		val document = inputStream.map {
			IOHelper.use(_) { is =>
				val parser = XPathExtractor.parserHolder.get
				val document = parser.parse(is)
				parser.reset
				document
			}
		}

		new XPathExtractor(document)
	}
}

/**
 * A built-in extractor for extracting values with XPath Expressions
 *
 * It requires a well formatted XML document, otherwise, it will throw an exception
 *
 * @constructor creates a new XPathExtractor
 * @param inputStream the XML document in which the XPath search will be applied
 */
class XPathExtractor(document: Option[Document]) extends Extractor {

	def xpath(expression: String, namespaces: List[(String, String)]) = {
		val xpathExpression = new DOMXPath(expression)
		namespaces.foreach {
			case (prefix, uri) => xpathExpression.addNamespace(prefix, uri)
		}
		xpathExpression
	}

	/**
	 * The actual extraction happens here. The XPath expression is searched for and the occurrence-th
	 * result is returned if existing.
	 *
	 * @param expression a String containing the XPath expression to be searched
	 * @return an option containing the value if found, None otherwise
	 */
	def extractOne(occurrence: Int, namespaces: List[(String, String)])(expression: String): Option[String] = {

		val results = document.map(xpath(expression, namespaces).selectNodes(_).asInstanceOf[java.util.List[Node]])

		results.flatMap(_.lift(occurrence).map(_.getTextContent))
	}

	/**
	 * The actual extraction happens here. The XPath expression is searched for and the occurrence-th
	 * result is returned if existing.
	 *
	 * @param expression a String containing the XPath expression to be searched
	 * @return an option containing the value if found, None otherwise
	 */
	def extractMultiple(namespaces: List[(String, String)])(expression: String): Option[Seq[String]] = document.map(xpath(expression, namespaces).selectNodes(_).asInstanceOf[java.util.List[Node]].map(_.getTextContent))

	def count(namespaces: List[(String, String)])(expression: String): Option[Int] = document.map(xpath(expression, namespaces).selectNodes(_).size)
}