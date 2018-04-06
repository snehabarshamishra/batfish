package org.batfish.datamodel.expr;

import java.util.Set;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.IpAccessList;

public abstract class AclLineExpr {
  /**
   * Check the given flow, source interface, and/or other ACLs for a match against this ACL line
   * expression.
   *
   * @param flow current packet's flow
   * @param srcInterface source interface for this flow
   * @param availableAcls ACLs this ACL line expression could possibly reference
   * @return true if the inputted flow, interface, and/or ACLs match the ACL line expression.
   */
  public abstract boolean match(Flow flow, String srcInterface, Set<IpAccessList> availableAcls);

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(getClass() == o.getClass())) {
      return false;
    }
    return exprEquals(o);
  }

  public abstract boolean exprEquals(Object o);

  public abstract int hashCode();

  public abstract String toString();
}
