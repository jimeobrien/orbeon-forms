/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.controls.ContainerControl
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.LHHAC
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes

// Default group handler
class XFormsGroupDefaultHandler extends XFormsGroupHandler {

  private var elementName: String  = null
  private var elementQName: String = null

  override def init(uri: String, localname: String, qName: String, attributes: Attributes, matched: Any): Unit = {
    super.init(uri, localname, qName, attributes, matched)

    // Use explicit container element name if present, otherwise use default
    matched match {
      case control: ContainerControl if control.elementQName ne null ⇒
        val explicitQName = control.elementQName

        elementName  = explicitQName.getName
        elementQName = explicitQName.getQualifiedName
      case _ ⇒
        elementName  = super.getContainingElementName
        elementQName = super.getContainingElementQName // NOTE: this calls back getContainingElementName()
    }
  }

  override protected def getContainingElementName  = elementName
  override protected def getContainingElementQName = elementQName

  def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl) = ()

  override protected def handleLabel(): Unit = {
    // TODO: check why we output our own label here

    val groupControl = currentControlOrNull.asInstanceOf[XFormsSingleNodeControl]
    val effectiveId = getEffectiveId

    reusableAttributes.clear()
    reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, getLabelClasses(groupControl))

    XFormsBaseHandlerXHTML.outputLabelFor(
      handlerContext,
      reusableAttributes,
      effectiveId,
      effectiveId,
      LHHAC.LABEL,
      handlerContext.getLabelElementName,
      getLabelValue(groupControl),
      (groupControl ne null) && groupControl.isHTMLLabel,
      false
    )
  }
}