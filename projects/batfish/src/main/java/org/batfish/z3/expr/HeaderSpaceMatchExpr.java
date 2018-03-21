package org.batfish.z3.expr;

import static org.batfish.z3.expr.HeaderSpaceMatchExpr.MatchMode.CURRENT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.Protocol;
import org.batfish.datamodel.State;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.TcpFlags;
import org.batfish.z3.BasicHeaderField;
import org.batfish.z3.HeaderField;
import org.batfish.z3.expr.visitors.ExprVisitor;
import org.batfish.z3.expr.visitors.GenericBooleanExprVisitor;

public class HeaderSpaceMatchExpr extends BooleanExpr {

  public static BooleanExpr matchDscp(Set<Integer> dscps) {
    return new OrExpr(
        dscps
            .stream()
            .map(
                dscp ->
                    new EqExpr(
                        new VarIntExpr(BasicHeaderField.DSCP),
                        new LitIntExpr(dscp, BasicHeaderField.DSCP.getSize())))
            .collect(ImmutableList.toImmutableList()));
  }

  public static BooleanExpr matchDstIp(Set<IpWildcard> dstIpWildcards) {
    return matchIp(dstIpWildcards, BasicHeaderField.DST_IP);
  }

  public static BooleanExpr matchDstPort(Set<SubRange> dstPortRanges) {
    return RangeMatchExpr.fromSubRanges(
        BasicHeaderField.DST_PORT, BasicHeaderField.DST_PORT.getSize(), dstPortRanges);
  }

  public static BooleanExpr matchDstProtocol(Set<Protocol> dstProtocols) {
    return matchProtocol(dstProtocols, false, true);
  }

  public static BooleanExpr matchEcn(Set<Integer> dscps) {
    return new OrExpr(
        dscps
            .stream()
            .map(
                dscp ->
                    new EqExpr(
                        new VarIntExpr(BasicHeaderField.ECN),
                        new LitIntExpr(dscp, BasicHeaderField.ECN.getSize())))
            .collect(ImmutableList.toImmutableList()));
  }

  public static BooleanExpr matchFragmentOffset(Set<SubRange> fragmentOffsetRanges) {
    return RangeMatchExpr.fromSubRanges(
        BasicHeaderField.FRAGMENT_OFFSET,
        BasicHeaderField.FRAGMENT_OFFSET.getSize(),
        fragmentOffsetRanges);
  }

  public static BooleanExpr matchIcmpCode(Set<SubRange> icmpCodes) {
    return RangeMatchExpr.fromSubRanges(
        BasicHeaderField.ICMP_CODE, BasicHeaderField.ICMP_CODE.getSize(), icmpCodes);
  }

  public static BooleanExpr matchIcmpType(Set<SubRange> icmpTypes) {
    return RangeMatchExpr.fromSubRanges(
        BasicHeaderField.ICMP_TYPE, BasicHeaderField.ICMP_TYPE.getSize(), icmpTypes);
  }

  public static BooleanExpr matchIp(Set<IpWildcard> ipWildcards, HeaderField ipField) {
    if (ipWildcards.isEmpty()) {
      return TrueExpr.INSTANCE;
    }
    return new OrExpr(
        ipWildcards
            .stream()
            .map(ipWildcard -> matchIp(ipWildcard, ipField))
            .collect(Collectors.toList()));
  }

  public static BooleanExpr matchIp(IpWildcard ipWildcard, HeaderField ipField) {
    if (ipWildcard.isPrefix()) {
      Prefix prefix = ipWildcard.toPrefix();
      long ip = prefix.getStartIp().asLong();
      int ipWildcardBits = Prefix.MAX_PREFIX_LENGTH - prefix.getPrefixLength();
      int ipStart = ipWildcardBits;
      int ipEnd = Prefix.MAX_PREFIX_LENGTH - 1;
      if (ipStart < Prefix.MAX_PREFIX_LENGTH) {
        return new EqExpr(
            ExtractExpr.newExtractExpr(ipField, ipStart, ipEnd),
            new LitIntExpr(ip, ipStart, ipEnd));
      } else {
        return TrueExpr.INSTANCE;
      }
    } else {
      long ip = ipWildcard.getIp().asLong();
      long wildcard = ipWildcard.getWildcard().asLong();
      return new AndExpr(
          IntStream.range(0, Prefix.MAX_PREFIX_LENGTH)
              .filter(bitIndex -> ((1L << bitIndex) & wildcard) == 0)
              .mapToObj(
                  bitIndex ->
                      new EqExpr(
                          ExtractExpr.newExtractExpr(ipField, bitIndex, bitIndex),
                          new LitIntExpr(ip, bitIndex, bitIndex)))
              .collect(Collectors.toList()));
    }
  }

  public static BooleanExpr matchIpProtocol(Set<IpProtocol> ipProtocols) {
    return new OrExpr(
        ipProtocols
            .stream()
            .map(IpProtocol::number)
            .map(
                num ->
                    new EqExpr(
                        new VarIntExpr(BasicHeaderField.IP_PROTOCOL),
                        new LitIntExpr(num, BasicHeaderField.IP_PROTOCOL.getSize())))
            .collect(ImmutableList.toImmutableList()));
  }

  public static BooleanExpr matchPacketLength(Set<SubRange> packetLengths) {
    return RangeMatchExpr.fromSubRanges(
        BasicHeaderField.PACKET_LENGTH, BasicHeaderField.PACKET_LENGTH.getSize(), packetLengths);
  }

  public static BooleanExpr matchProtocol(Set<Protocol> protocols, boolean useSrc, boolean useDst) {
    OrExpr matchesSomeProtocol =
        new OrExpr(
            protocols
                .stream()
                .map(
                    protocol -> {
                      int protocolNumber = protocol.getIpProtocol().number();
                      Integer port = protocol.getPort();
                      VarIntExpr protocolVar = new VarIntExpr(BasicHeaderField.IP_PROTOCOL);
                      LitIntExpr protocolLit =
                          new LitIntExpr(protocolNumber, BasicHeaderField.IP_PROTOCOL.getSize());
                      EqExpr matchProtocol = new EqExpr(protocolVar, protocolLit);
                      ImmutableList.Builder<BooleanExpr> matchProtocolAndPort =
                          ImmutableList.builder();
                      matchProtocolAndPort.add(matchProtocol);
                      if (port != null) {

                        VarIntExpr dstPortVar = new VarIntExpr(BasicHeaderField.DST_PORT);
                        VarIntExpr srcPortVar = new VarIntExpr(BasicHeaderField.SRC_PORT);
                        LitIntExpr portLit =
                            new LitIntExpr(port, BasicHeaderField.SRC_PORT.getSize());
                        EqExpr matchDstPort = new EqExpr(dstPortVar, portLit);
                        EqExpr matchSrcPort = new EqExpr(srcPortVar, portLit);
                        BooleanExpr matchSpecifiedPorts;
                        if (useSrc) {
                          if (useDst) {
                            matchSpecifiedPorts =
                                new OrExpr(ImmutableList.of(matchDstPort, matchSrcPort));
                          } else {
                            matchSpecifiedPorts = matchSrcPort;
                          }
                        } else if (useDst) {
                          matchSpecifiedPorts = matchDstPort;
                        } else {
                          throw new BatfishException("useSrc or useDst cannot both be false");
                        }
                        matchProtocolAndPort.add(matchSpecifiedPorts);
                      }
                      return new AndExpr(matchProtocolAndPort.build());
                    })
                .collect(ImmutableList.toImmutableList()));
    return matchesSomeProtocol;
  }

  public BooleanExpr matchSrcIp(Set<IpWildcard> srcIpWildcards) {
    switch (_matchMode) {
      case CURRENT:
        return matchIp(srcIpWildcards, BasicHeaderField.SRC_IP);
      case ORIGINAL:
        return matchIp(srcIpWildcards, BasicHeaderField.ORIG_SRC_IP);
      default:
        throw new BatfishException("Unexpected MatchMode");
    }
  }

  public static BooleanExpr matchSrcOrDstIp(Set<IpWildcard> srcOrDstIppWildcards) {
    return new OrExpr(
        ImmutableList.of(
            matchIp(srcOrDstIppWildcards, BasicHeaderField.SRC_IP),
            matchIp(srcOrDstIppWildcards, BasicHeaderField.DST_IP)));
  }

  public static BooleanExpr matchSrcOrDstPort(Set<SubRange> srcOrDstPorts) {
    return new OrExpr(ImmutableList.of(matchSrcPort(srcOrDstPorts), matchDstPort(srcOrDstPorts)));
  }

  public static BooleanExpr matchSrcOrDstProtocol(Set<Protocol> srcOrDstProtocols) {
    return matchProtocol(srcOrDstProtocols, true, true);
  }

  public static BooleanExpr matchSrcPort(Set<SubRange> srcPortRanges) {
    return RangeMatchExpr.fromSubRanges(
        BasicHeaderField.SRC_PORT, BasicHeaderField.SRC_PORT.getSize(), srcPortRanges);
  }

  public static BooleanExpr matchSrcProtocol(Set<Protocol> srcProtocols) {
    return matchProtocol(srcProtocols, true, false);
  }

  public static BooleanExpr matchState(Set<State> states) {
    return new OrExpr(
        states
            .stream()
            .map(
                state ->
                    new EqExpr(
                        new VarIntExpr(BasicHeaderField.STATE),
                        new LitIntExpr(state.number(), BasicHeaderField.STATE.getSize())))
            .collect(ImmutableList.toImmutableList()));
  }

  public static BooleanExpr matchTcpFlags(List<TcpFlags> tcpFlags) {
    LitIntExpr one = new LitIntExpr(1, 1);
    LitIntExpr zero = new LitIntExpr(0, 1);
    return new OrExpr(
        tcpFlags
            .stream()
            .map(
                currentTcpFlags -> {
                  ImmutableList.Builder<BooleanExpr> matchCurrentTcpFlags = ImmutableList.builder();
                  if (currentTcpFlags.getUseCwr()) {
                    LitIntExpr bit = currentTcpFlags.getCwr() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_CWR), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUseEce()) {
                    LitIntExpr bit = currentTcpFlags.getEce() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_ECE), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUseUrg()) {
                    LitIntExpr bit = currentTcpFlags.getUrg() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_URG), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUseAck()) {
                    LitIntExpr bit = currentTcpFlags.getAck() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_ACK), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUsePsh()) {
                    LitIntExpr bit = currentTcpFlags.getPsh() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_PSH), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUseRst()) {
                    LitIntExpr bit = currentTcpFlags.getRst() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_RST), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUseSyn()) {
                    LitIntExpr bit = currentTcpFlags.getSyn() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_SYN), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  if (currentTcpFlags.getUseFin()) {
                    LitIntExpr bit = currentTcpFlags.getFin() ? one : zero;
                    EqExpr matchFlag =
                        new EqExpr(new VarIntExpr(BasicHeaderField.TCP_FLAGS_FIN), bit);
                    matchCurrentTcpFlags.add(matchFlag);
                  }
                  return new AndExpr(matchCurrentTcpFlags.build());
                })
            .collect(ImmutableList.toImmutableList()));
  }

  private final BooleanExpr _expr;

  private final MatchMode _matchMode;

  public enum MatchMode {
    CURRENT,
    ORIGINAL
  }

  public HeaderSpaceMatchExpr(HeaderSpace headerSpace) {
    this(headerSpace, CURRENT);
  }

  public HeaderSpaceMatchExpr(HeaderSpace headerSpace, MatchMode matchMode) {
    _matchMode = matchMode;

    ImmutableList.Builder<BooleanExpr> matchBuilder = ImmutableList.builder();

    // ipProtocol
    requireMatch(matchBuilder, headerSpace.getIpProtocols(), HeaderSpaceMatchExpr::matchIpProtocol);
    requireNoMatch(
        matchBuilder, headerSpace.getNotIpProtocols(), HeaderSpaceMatchExpr::matchIpProtocol);

    // protocol
    requireMatch(
        matchBuilder, headerSpace.getDstProtocols(), HeaderSpaceMatchExpr::matchDstProtocol);
    requireNoMatch(
        matchBuilder, headerSpace.getNotDstProtocols(), HeaderSpaceMatchExpr::matchDstProtocol);
    requireMatch(
        matchBuilder, headerSpace.getSrcProtocols(), HeaderSpaceMatchExpr::matchSrcProtocol);
    requireNoMatch(
        matchBuilder, headerSpace.getNotSrcProtocols(), HeaderSpaceMatchExpr::matchSrcProtocol);
    requireMatch(
        matchBuilder,
        headerSpace.getSrcOrDstProtocols(),
        HeaderSpaceMatchExpr::matchSrcOrDstProtocol);

    // ip
    requireMatch(matchBuilder, headerSpace.getSrcIps(), this::matchSrcIp);
    requireNoMatch(matchBuilder, headerSpace.getNotSrcIps(), this::matchSrcIp);
    requireMatch(matchBuilder, headerSpace.getSrcOrDstIps(), HeaderSpaceMatchExpr::matchSrcOrDstIp);
    requireMatch(matchBuilder, headerSpace.getDstIps(), HeaderSpaceMatchExpr::matchDstIp);
    requireNoMatch(matchBuilder, headerSpace.getNotDstIps(), HeaderSpaceMatchExpr::matchDstIp);

    // port
    requireMatch(matchBuilder, headerSpace.getSrcPorts(), HeaderSpaceMatchExpr::matchSrcPort);
    requireNoMatch(matchBuilder, headerSpace.getNotSrcPorts(), HeaderSpaceMatchExpr::matchSrcPort);
    requireMatch(
        matchBuilder, headerSpace.getSrcOrDstPorts(), HeaderSpaceMatchExpr::matchSrcOrDstPort);
    requireMatch(matchBuilder, headerSpace.getDstPorts(), HeaderSpaceMatchExpr::matchDstPort);
    requireNoMatch(matchBuilder, headerSpace.getNotDstPorts(), HeaderSpaceMatchExpr::matchDstPort);

    // dscp
    requireMatch(matchBuilder, headerSpace.getDscps(), HeaderSpaceMatchExpr::matchDscp);
    requireNoMatch(matchBuilder, headerSpace.getNotDscps(), HeaderSpaceMatchExpr::matchDscp);

    // ecn
    requireMatch(matchBuilder, headerSpace.getEcns(), HeaderSpaceMatchExpr::matchEcn);
    requireNoMatch(matchBuilder, headerSpace.getNotEcns(), HeaderSpaceMatchExpr::matchEcn);

    // fragmentOffset
    requireMatch(
        matchBuilder, headerSpace.getFragmentOffsets(), HeaderSpaceMatchExpr::matchFragmentOffset);
    requireNoMatch(
        matchBuilder,
        headerSpace.getNotFragmentOffsets(),
        HeaderSpaceMatchExpr::matchFragmentOffset);

    // connection-tracking state
    requireMatch(matchBuilder, headerSpace.getStates(), HeaderSpaceMatchExpr::matchState);

    // icmpTypes
    requireMatch(matchBuilder, headerSpace.getIcmpTypes(), HeaderSpaceMatchExpr::matchIcmpType);
    requireNoMatch(
        matchBuilder, headerSpace.getNotIcmpTypes(), HeaderSpaceMatchExpr::matchIcmpType);

    // icmpCodes
    requireMatch(matchBuilder, headerSpace.getIcmpCodes(), HeaderSpaceMatchExpr::matchIcmpCode);
    requireNoMatch(
        matchBuilder, headerSpace.getNotIcmpCodes(), HeaderSpaceMatchExpr::matchIcmpCode);

    // packetLengths
    requireMatch(
        matchBuilder, headerSpace.getPacketLengths(), HeaderSpaceMatchExpr::matchPacketLength);
    requireNoMatch(
        matchBuilder, headerSpace.getNotPacketLengths(), HeaderSpaceMatchExpr::matchPacketLength);

    // tcp-flags
    requireMatch(matchBuilder, headerSpace.getTcpFlags(), HeaderSpaceMatchExpr::matchTcpFlags);

    BooleanExpr match = new AndExpr(matchBuilder.build());
    if (headerSpace.getNegate()) {
      _expr = new NotExpr(match);
    } else {
      _expr = match;
    }
  }

  @Override
  public void accept(ExprVisitor visitor) {
    visitor.visitHeaderSpaceMatchExpr(this);
  }

  @Override
  public <R> R accept(GenericBooleanExprVisitor<R> visitor) {
    return visitor.visitHeaderSpaceMatchExpr(this);
  }

  @Override
  public boolean exprEquals(Expr e) {
    return Objects.equals(_expr, ((HeaderSpaceMatchExpr) e)._expr);
  }

  public BooleanExpr getExpr() {
    return _expr;
  }

  @Override
  public int hashCode() {
    return Objects.hash(_expr);
  }

  private <T, C extends Collection<T>> void requireMatch(
      Builder<BooleanExpr> match, C collection, Function<C, BooleanExpr> generator) {
    if (!collection.isEmpty()) {
      match.add(generator.apply(collection));
    }
  }

  private <T, C extends Collection<T>> void requireNoMatch(
      Builder<BooleanExpr> match, C collection, Function<C, BooleanExpr> generator) {
    if (!collection.isEmpty()) {
      match.add(new NotExpr(generator.apply(collection)));
    }
  }
}
