/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.{ConnectionResult, XPathCache}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.model.DataModel._
import org.orbeon.saxon.om.NodeInfo

/**
 * Handle replace="text".
 */
class TextReplacer(submission: XFormsModelSubmission, containingDocument: XFormsContainingDocument)
  extends BaseReplacer(submission, containingDocument) {

  private var responseBody: String = _

  def deserialize(
    connectionResult : ConnectionResult,
    p                : XFormsModelSubmission#SubmissionParameters,
    p2               : XFormsModelSubmission#SecondPassParameters
  ): Unit =
    connectionResult.readTextResponseBody match {
      case Some(responseBody) ⇒
        this.responseBody = responseBody
      case None ⇒
        // Non-text/non-XML result

        // Don't store anything for now as per the spec, but we could do something better by going beyond the spec
        // NetUtils.inputStreamToAnyURI(pipelineContext, connectionResult.resultInputStream, NetUtils.SESSION_SCOPE);

        // XForms 1.1: "For a success response including a body that is both a non-XML media type (i.e. with a
        // content type not matching any of the specifiers in [RFC 3023]) and a non-text type (i.e. with a content
        // type not matching text/*), when the value of the replace attribute on element submission is "text",
        // nothing in the document is replaced and submission processing concludes after dispatching
        // xforms-submit-error with appropriate context information, including an error-type of resource-error."
        val message =
          connectionResult.mediatype match {
            case Some(mediatype) ⇒ s"""Mediatype is neither text nor XML for replace="text": $mediatype"""
            case None            ⇒ s"""No mediatype received for replace="text""""
          }

        throw new XFormsSubmissionException(
          submission,
          message,
          "reading response body",
          new XFormsSubmitErrorEvent(
            submission,
            XFormsSubmitErrorEvent.RESOURCE_ERROR,
            connectionResult
          )
        )
    }

  def replace(
    connectionResult : ConnectionResult,
    p                : XFormsModelSubmission#SubmissionParameters,
    p2               : XFormsModelSubmission#SecondPassParameters
  ): Runnable = {
    // XForms 1.1: "If the replace attribute contains the value "text" and the submission response conforms to an
    // XML mediatype (as defined by the content type specifiers in [RFC 3023]) or a text media type (as defined by
    // a content type specifier of text/*), then the response data is encoded as text and replaces the content of
    // the replacement target node."

    // XForms 1.1: "If the processing of the targetref attribute fails, then submission processing ends after
    // dispatching the event xforms-submit-error with an error-type of target-error."
    def throwSubmissionException(message: String) =
      throw new XFormsSubmissionException(
        submission,
        message,
        "processing targetref attribute",
        new XFormsSubmitErrorEvent(
          submission,
          XFormsSubmitErrorEvent.TARGET_ERROR,
          connectionResult
        )
      )

    // Find target location
    val destinationNodeInfo =
      if (submission.getTargetref ne null) {
        // Evaluate destination node
        XPathCache.evaluateSingleWithContext(
          xpathContext = p.xpathContext,
          contextItem  = p.refNodeInfo,
          xpathString  = submission.getTargetref,
          reporter     = containingDocument.getRequestStats.addXPathStat
        ) match {
          case n: NodeInfo ⇒ n
          case _           ⇒ throwSubmissionException("""targetref attribute doesn't point to a node for replace="text".""")
        }
      } else {
        // Use default destination
        submission.findReplaceInstanceNoTargetref(p.refInstanceOpt).rootElement
      }

    def handleSetValueSuccess(oldValue: String) =
      DataModel.logAndNotifyValueChange(
        containingDocument = containingDocument,
        source             = "submission",
        nodeInfo           = destinationNodeInfo,
        oldValue           = oldValue,
        newValue           = responseBody,
        isCalculate        = false,
        collector          = Dispatch.dispatchEvent)(
        containingDocument.getIndentedLogger(XFormsActions.LOGGING_CATEGORY)
      )

    def handleSetValueError(reason: Reason) =
      throwSubmissionException(
        reason match {
          case DisallowedNodeReason ⇒
            """targetref attribute doesn't point to an element without children or to an attribute for replace="text"."""
          case ReadonlyNodeReason   ⇒
            """targetref attribute points to a readonly node for replace="text"."""
        }
      )

    // Set value into the instance
    // NOTE: Here we decided to use the actions logger, by compatibility with xf:setvalue. Anything we would like
    // to log in "submission" mode?
    DataModel.setValueIfChanged(
      nodeInfo  = destinationNodeInfo,
      newValue  = responseBody,
      onSuccess = handleSetValueSuccess,
      onError   = handleSetValueError
    )

    // Dispatch xforms-submit-done
    submission.sendSubmitDone(connectionResult)
  }
}
