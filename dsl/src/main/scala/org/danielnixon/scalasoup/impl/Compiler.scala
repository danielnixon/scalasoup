package org.danielnixon.scalasoup.impl

import cats.{Id, ~>}
import org.danielnixon.scalasoup.{Document, HasOwner, ParentState}

private[scalasoup] object Compiler {
  def apply(document: Document[ParentState.NoParent]): ModificationA ~> Id = {

    @SuppressWarnings(Array("org.wartremover.warts.Equals", "org.wartremover.warts.Throw"))
    def withOwnerCheck[A, B <: HasOwner](hasOwner: B, op: B => A): A = {
      if (hasOwner.ownerDocument.exists(_.underlying eq document.underlying)) {
        op(hasOwner)
      } else {
        throw new IllegalStateException("Attempt to modify an unrelated document.")
      }
    }

    new (ModificationA ~> Id) {
      override def apply[A](fa: ModificationA[A]): Id[A] = {
        fa match {
          case NodeModification(node, mod) => withOwnerCheck(node, mod)
          case TextNodeModification(node, mod) => withOwnerCheck(node, mod)
          case DataNodeModification(node, mod) => withOwnerCheck(node, mod)
          case ElementModification(element, mod) => withOwnerCheck(element, mod)
          case DocumentModification(mod) => mod(document)
          case AttributeModification(attribute, mod) => withOwnerCheck(attribute, mod)
          case AttributesModification(attributes, mod) => withOwnerCheck(attributes, mod)
        }
      }
    }
  }
}
