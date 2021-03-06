package org.batfish.representation.juniper;

import com.google.common.collect.Iterables;
import java.util.Collections;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.SubRange;

public class FwFromIcmpCode extends FwFrom {

  /** */
  private static final long serialVersionUID = 1L;

  private SubRange _icmpCodeRange;

  public FwFromIcmpCode(SubRange icmpCodeRange) {
    _icmpCodeRange = icmpCodeRange;
  }

  @Override
  public void applyTo(IpAccessListLine line, JuniperConfiguration jc, Warnings w, Configuration c) {
    line.setIcmpCodes(Iterables.concat(line.getIcmpCodes(), Collections.singleton(_icmpCodeRange)));
  }
}
