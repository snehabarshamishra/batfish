package org.batfish.z3;

import static org.batfish.common.util.CommonUtil.computeIpOwners;
import static org.batfish.common.util.CommonUtil.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.batfish.common.BatfishException;
import org.batfish.common.Pair;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Edge;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.NetworkFactory;
import org.batfish.datamodel.Topology;
import org.batfish.z3.expr.BooleanExpr;
import org.batfish.z3.expr.HeaderSpaceMatchExpr;
import org.batfish.z3.expr.IpSpaceMatchExpr;
import org.batfish.z3.expr.RangeMatchExpr;
import org.batfish.z3.state.AclPermit;
import org.batfish.z3.state.StateParameter.Type;

public final class SynthesizerInputImpl implements SynthesizerInput {

  public static class Builder {
    private ForwardingAnalysis _forwardingAnalysis;

    private Map<String, Configuration> _configurations;

    private Map<String, Set<String>> _disabledAcls;

    private Map<String, Set<String>> _disabledInterfaces;

    private Set<String> _disabledNodes;

    private Map<String, Set<String>> _disabledVrfs;

    @Nullable private HeaderSpace _headerSpace;

    private boolean _simplify;

    private boolean _specialize;

    private Topology _topology;

    private Set<Type> _vectorizedParameters;

    private Builder() {
      _disabledAcls = ImmutableMap.of();
      _disabledInterfaces = ImmutableMap.of();
      _disabledNodes = ImmutableSet.of();
      _disabledVrfs = ImmutableMap.of();
      _headerSpace = null;
      _simplify = false;
      _specialize = false;
      _vectorizedParameters = ImmutableSet.of();
    }

    public SynthesizerInputImpl build() {
      return new SynthesizerInputImpl(
          _forwardingAnalysis,
          _configurations,
          _disabledAcls,
          _disabledInterfaces,
          _disabledNodes,
          _disabledVrfs,
          _headerSpace != null ? _headerSpace : new HeaderSpace(),
          _simplify,
          _specialize,
          _topology,
          _vectorizedParameters);
    }

    public Builder setForwardingAnalysis(ForwardingAnalysis forwardingAnalysis) {
      _forwardingAnalysis = forwardingAnalysis;
      return this;
    }

    public Builder setConfigurations(Map<String, Configuration> configurations) {
      _configurations = configurations;
      return this;
    }

    public Builder setDisabledAcls(Map<String, Set<String>> disabledAcls) {
      _disabledAcls = disabledAcls;
      return this;
    }

    public Builder setDisabledInterfaces(Map<String, Set<String>> disabledInterfaces) {
      _disabledInterfaces = disabledInterfaces;
      return this;
    }

    public Builder setDisabledNodes(Set<String> disabledNodes) {
      _disabledNodes = disabledNodes;
      return this;
    }

    public Builder setDisabledVrfs(Map<String, Set<String>> disabledVrfs) {
      _disabledVrfs = disabledVrfs;
      return this;
    }

    public Builder setHeaderSpace(HeaderSpace headerSpace) {
      _headerSpace = headerSpace;
      return this;
    }

    public Builder setSimplify(boolean simplify) {
      _simplify = simplify;
      return this;
    }

    public Builder setSpecialize(boolean specialize) {
      _specialize = specialize;
      return this;
    }

    public Builder setTopology(Topology topology) {
      _topology = topology;
      return this;
    }

    public Builder setVectorizedParameters(Set<Type> vectorizedParameters) {
      _vectorizedParameters = vectorizedParameters;
      return this;
    }
  }

  static final IpAccessList DEFAULT_SOURCE_NAT_ACL =
      new NetworkFactory()
          .aclBuilder()
          .setName("~DEFAULT_SOURCE_NAT_ACL~")
          .setLines(
              ImmutableList.of(IpAccessListLine.builder().setAction(LineAction.ACCEPT).build()))
          .build();

  public static Builder builder() {
    return new Builder();
  }

  private final Map<String, Map<String, List<LineAction>>> _aclActions;

  private final Map<String, Map<String, List<BooleanExpr>>> _aclConditions;

  private final Map<String, Map<String, Map<String, Map<String, Map<String, BooleanExpr>>>>>
      _arpTrueEdge;

  private final Map<String, Configuration> _configurations;

  private final Map<String, Set<String>> _disabledAcls;

  private final Map<String, Set<String>> _disabledInterfaces;

  private final Set<String> _disabledNodes;

  private final Map<String, Set<String>> _disabledVrfs;

  private final Set<Edge> _edges;

  private final Map<String, Map<String, IpAccessList>> _enabledAcls;

  private final Set<Edge> _enabledEdges;

  private final Map<String, Set<String>> _enabledInterfaces;

  private final Map<String, Map<String, Set<String>>> _enabledInterfacesByNodeVrf;

  private final Set<String> _enabledNodes;

  private final Map<String, Set<String>> _enabledVrfs;

  private final Map<String, Map<String, String>> _incomingAcls;

  private IpAccessListSpecializer _ipAclListSpecializer;

  private final Map<String, Set<Ip>> _ipsByHostname;

  private final IpSpaceSpecializer _ipSpaceSpecializer;

  private final Map<String, Map<String, Map<String, BooleanExpr>>> _neighborUnreachable;

  private final Map<String, Map<String, BooleanExpr>> _nullRoutedIps;

  private final Map<String, Map<String, String>> _outgoingAcls;

  private final Map<String, Map<String, BooleanExpr>> _routableIps;

  private final boolean _simplify;

  private final Map<String, Map<String, List<Entry<AclPermit, BooleanExpr>>>> _sourceNats;

  private final Map<String, Set<String>> _topologyInterfaces;

  private final Set<Type> _vectorizedParameters;

  public SynthesizerInputImpl(
      ForwardingAnalysis forwardingAnalysis,
      Map<String, Configuration> configurations,
      Map<String, Set<String>> disabledAcls,
      Map<String, Set<String>> disabledInterfaces,
      Set<String> disabledNodes,
      Map<String, Set<String>> disabledVrfs,
      HeaderSpace headerSpace,
      boolean simplify,
      boolean specialize,
      Topology topology,
      Set<Type> vectorizedParameters) {
    if (configurations == null) {
      throw new BatfishException("Must supply configurations");
    }
    _ipAclListSpecializer = new IpAccessListSpecializer(headerSpace);
    _ipSpaceSpecializer =
        specialize
            ? new IpSpaceSpecializer(headerSpace.getDstIps(), headerSpace.getNotDstIps())
            : new IpSpaceSpecializer(ImmutableSet.of(), ImmutableSet.of());
    _configurations = ImmutableMap.copyOf(configurations);
    _disabledAcls = ImmutableMap.copyOf(disabledAcls);
    _disabledInterfaces = ImmutableMap.copyOf(disabledInterfaces);
    _disabledNodes = ImmutableSet.copyOf(disabledNodes);
    _disabledVrfs = ImmutableMap.copyOf(disabledVrfs);
    _enabledNodes = computeEnabledNodes();
    _enabledVrfs = computeEnabledVrfs();
    _enabledInterfacesByNodeVrf = computeEnabledInterfacesByNodeVrf();
    _enabledInterfaces = computeEnabledInterfaces();
    _incomingAcls = computeIncomingAcls();
    _outgoingAcls = computeOutgoingAcls();
    _simplify = simplify;
    _vectorizedParameters = vectorizedParameters;
    if (forwardingAnalysis != null) {
      _arpTrueEdge = computeArpTrueEdge(forwardingAnalysis.getArpTrueEdge());
      _neighborUnreachable =
          computeNeighborUnreachable(forwardingAnalysis.getNeighborUnreachable());
      _nullRoutedIps = computeNullRoutedIps(forwardingAnalysis.getNullRoutedIps());
      _routableIps = computeRoutableIps(forwardingAnalysis.getRoutableIps());
      _ipsByHostname = computeIpsByHostname();
      _edges = topology.getEdges();
      _enabledEdges = computeEnabledEdges();
      _topologyInterfaces = computeTopologyInterfaces();
      _sourceNats = computeSourceNats();
    } else {
      _arpTrueEdge = null;
      _neighborUnreachable = null;
      _nullRoutedIps = null;
      _routableIps = null;
      _ipsByHostname = null;
      _edges = null;
      _enabledEdges = null;
      _topologyInterfaces = null;
      _sourceNats = null;
    }
    _enabledAcls = computeEnabledAcls();
    _aclActions = computeAclActions();
    _aclConditions = computeAclConditions();
  }

  private Map<String, Map<String, List<LineAction>>> computeAclActions() {
    return toImmutableMap(
        _enabledAcls,
        Entry::getKey,
        enabledAclsByHostnameEntry ->
            toImmutableMap(
                enabledAclsByHostnameEntry.getValue(),
                Entry::getKey,
                enabledAclsByAclNameEntry ->
                    enabledAclsByAclNameEntry
                        .getValue()
                        .getLines()
                        .stream()
                        .map(IpAccessListLine::getAction)
                        .collect(ImmutableList.toImmutableList())));
  }

  private Map<String, Map<String, List<BooleanExpr>>> computeAclConditions() {
    return toImmutableMap(
        _enabledAcls,
        Entry::getKey,
        e ->
            toImmutableMap(
                e.getValue(),
                Entry::getKey,
                e2 ->
                    e2.getValue()
                        .getLines()
                        .stream()
                        .map(HeaderSpaceMatchExpr::new)
                        .collect(ImmutableList.toImmutableList())));
  }

  private Map<String, Map<String, Map<String, Map<String, Map<String, BooleanExpr>>>>>
      computeArpTrueEdge(Map<Edge, IpSpace> arpTrueEdge) {
    Map<String, Map<String, Map<String, Map<String, Map<String, BooleanExpr>>>>> output =
        new HashMap<>();
    arpTrueEdge.forEach(
        (edge, ipSpace) -> {
          ipSpace = _ipSpaceSpecializer.specialize(ipSpace);
          if (ipSpace instanceof EmptyIpSpace) {
            return;
          }
          String hostname = edge.getNode1();
          String outInterface = edge.getInt1();
          String vrf = _configurations.get(hostname).getInterfaces().get(outInterface).getVrfName();
          String recvNode = edge.getNode2();
          String recvInterface = edge.getInt2();
          output
              .computeIfAbsent(hostname, n -> new HashMap<>())
              .computeIfAbsent(vrf, n -> new HashMap<>())
              .computeIfAbsent(outInterface, n -> new HashMap<>())
              .computeIfAbsent(recvNode, n -> new HashMap<>())
              .put(recvInterface, new IpSpaceMatchExpr(ipSpace, false, true));
        });

    // freeze
    return toImmutableMap(
        output,
        Entry::getKey, /* node */
        outputByHostnameEntry ->
            toImmutableMap(
                outputByHostnameEntry.getValue(),
                Entry::getKey, /* vrf */
                outputByVrfEntry ->
                    toImmutableMap(
                        outputByVrfEntry.getValue(),
                        Entry::getKey /* outInterface */,
                        outputByOutInterfaceEntry ->
                            toImmutableMap(
                                outputByOutInterfaceEntry.getValue(),
                                Entry::getKey /* recvNode */,
                                outputByRecvNodeEntry ->
                                    toImmutableMap(
                                        outputByRecvNodeEntry.getValue(),
                                        Entry::getKey /* recvInterface */,
                                        Entry::getValue)))));
  }

  private Map<String, Map<String, IpAccessList>> computeEnabledAcls() {
    if (_topologyInterfaces != null) {
      return toImmutableMap(
          _topologyInterfaces,
          Entry::getKey, /* node */
          topologyInterfacesEntry -> {
            String hostname = topologyInterfacesEntry.getKey();
            Configuration c = _configurations.get(hostname);
            return topologyInterfacesEntry
                .getValue()
                .stream()
                .flatMap(
                    ifaceName -> {
                      Interface i = c.getInterfaces().get(ifaceName);
                      ImmutableList.Builder<Pair<String, IpAccessList>> interfaceAcls =
                          ImmutableList.builder();
                      IpAccessList aclIn = i.getIncomingFilter();
                      IpAccessList aclOut = i.getOutgoingFilter();
                      if (aclIn != null) {
                        aclIn = _ipAclListSpecializer.specialize(aclIn);
                        interfaceAcls.add(new Pair<>(aclIn.getName(), aclIn));
                      }
                      if (aclOut != null) {
                        aclOut = _ipAclListSpecializer.specialize(aclOut);
                        interfaceAcls.add(new Pair<>(aclOut.getName(), aclOut));
                      }
                      i.getSourceNats()
                          .forEach(
                              sourceNat -> {
                                IpAccessList sourceNatAcl = sourceNat.getAcl();
                                if (sourceNatAcl != null) {
                                  interfaceAcls.add(
                                      new Pair<>(sourceNatAcl.getName(), sourceNatAcl));
                                } else {
                                  interfaceAcls.add(
                                      new Pair<>(
                                          DEFAULT_SOURCE_NAT_ACL.getName(),
                                          DEFAULT_SOURCE_NAT_ACL));
                                }
                              });

                      return interfaceAcls.build().stream();
                    })
                .collect(ImmutableSet.toImmutableSet())
                .stream()
                .collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond));
          });
    } else {
      return _configurations
          .entrySet()
          .stream()
          .filter(e -> !_disabledNodes.contains(e.getKey()))
          .collect(
              ImmutableMap.toImmutableMap(
                  Entry::getKey,
                  e -> {
                    String hostname = e.getKey();
                    Set<String> disabledAcls = _disabledAcls.get(hostname);
                    return e.getValue()
                        .getIpAccessLists()
                        .entrySet()
                        .stream()
                        .filter(e2 -> disabledAcls == null || !disabledAcls.contains(e2.getKey()))
                        .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));
                  }));
    }
  }

  private Set<Edge> computeEnabledEdges() {
    return _edges
        .stream()
        .filter(
            e -> {
              Set<String> enabledInterfaces1 = _enabledInterfaces.get(e.getNode1());
              Set<String> enabledInterfaces2 = _enabledInterfaces.get(e.getNode2());
              return enabledInterfaces1 != null
                  && enabledInterfaces1.contains(e.getInt1())
                  && enabledInterfaces2 != null
                  && enabledInterfaces2.contains(e.getInt2());
            })
        .collect(ImmutableSet.toImmutableSet());
  }

  private Map<String, Set<String>> computeEnabledInterfaces() {
    return toImmutableMap(
        _enabledInterfacesByNodeVrf,
        Entry::getKey,
        enabledInterfacesByNodeVrfEntry ->
            enabledInterfacesByNodeVrfEntry
                .getValue()
                .entrySet()
                .stream()
                .flatMap(
                    enabledInterfacesByVrfEntry -> enabledInterfacesByVrfEntry.getValue().stream())
                .collect(ImmutableSet.toImmutableSet()));
  }

  private Map<String, Map<String, Set<String>>> computeEnabledInterfacesByNodeVrf() {
    return toImmutableMap(
        _enabledVrfs,
        Entry::getKey,
        enabledVrfsEntry -> {
          String hostname = enabledVrfsEntry.getKey();
          Set<String> disabledInterfaces = _disabledInterfaces.get(hostname);
          Configuration c = _configurations.get(hostname);
          return toImmutableMap(
              enabledVrfsEntry.getValue(),
              Function.identity(),
              vrfName ->
                  c.getVrfs()
                      .get(vrfName)
                      .getInterfaces()
                      .entrySet()
                      .stream()
                      .filter(
                          interfaceEntry ->
                              disabledInterfaces == null
                                  || !disabledInterfaces.contains(interfaceEntry.getKey()))
                      .filter(interfaceEntry -> interfaceEntry.getValue().getActive())
                      .filter(interfaceEntry -> !interfaceEntry.getValue().getBlacklisted())
                      .map(Entry::getKey)
                      .collect(ImmutableSet.toImmutableSet()));
        });
  }

  private Set<String> computeEnabledNodes() {
    return ImmutableSet.copyOf(Sets.difference(_configurations.keySet(), _disabledNodes));
  }

  private Map<String, Set<String>> computeEnabledVrfs() {
    return toImmutableMap(
        _enabledNodes,
        Function.identity(),
        hostname -> {
          Set<String> disabledVrfs = _disabledVrfs.get(hostname);
          return _configurations
              .get(hostname)
              .getVrfs()
              .keySet()
              .stream()
              .filter(vrfName -> disabledVrfs == null || !disabledVrfs.contains(vrfName))
              .collect(ImmutableSet.toImmutableSet());
        });
  }

  private Map<String, Map<String, String>> computeIncomingAcls() {
    return toImmutableMap(
        _enabledInterfaces,
        Entry::getKey,
        enabledInterfacesEntry -> {
          Configuration c = _configurations.get(enabledInterfacesEntry.getKey());
          return enabledInterfacesEntry
              .getValue()
              .stream()
              .filter(ifaceName -> c.getInterfaces().get(ifaceName).getIncomingFilterName() != null)
              .collect(
                  ImmutableMap.toImmutableMap(
                      Function.identity(),
                      ifaceName -> c.getInterfaces().get(ifaceName).getIncomingFilterName()));
        });
  }

  private Map<String, Set<Ip>> computeIpsByHostname() {
    Map<String, Map<String, Interface>> enabledInterfaces =
        toImmutableMap(
            _enabledInterfaces,
            Entry::getKey,
            enabledInterfacesEntry -> {
              Configuration c = _configurations.get(enabledInterfacesEntry.getKey());
              return toImmutableMap(
                  enabledInterfacesEntry.getValue(), Function.identity(), c.getInterfaces()::get);
            });
    Map<Ip, Set<String>> ipOwners = computeIpOwners(true, enabledInterfaces);
    Map<String, Set<Ip>> map = new HashMap<>();
    /*
     * ipOwners may not contain all nodes (i.e. a node may not own any IPs),
     * so first initialize to make sure there is an entry for each node.
     */
    _enabledInterfaces.keySet().forEach(node -> map.put(node, new HashSet<>()));
    ipOwners.forEach(
        (ip, owners) -> {
          for (String owner : owners) {
            map.get(owner).add(ip);
          }
        });
    // freeze
    return toImmutableMap(map, Entry::getKey, e -> ImmutableSet.copyOf(e.getValue()));
  }

  private Map<String, Map<String, Map<String, BooleanExpr>>> computeNeighborUnreachable(
      Map<String, Map<String, Map<String, IpSpace>>> neighborUnreachable) {
    return toImmutableMap(
        neighborUnreachable,
        Entry::getKey /* hostname */,
        neighborUnreachableByHostnameEntry ->
            toImmutableMap(
                neighborUnreachableByHostnameEntry.getValue(),
                Entry::getKey /* vrf */,
                neighborUnreachableByVrfEntry ->
                    toImmutableMap(
                        neighborUnreachableByVrfEntry.getValue(),
                        Entry::getKey /* interface */,
                        neighborUnreachableByOutInterfaceEntry ->
                            new IpSpaceMatchExpr(
                                neighborUnreachableByOutInterfaceEntry.getValue(), false, true))));
  }

  private Map<String, Map<String, BooleanExpr>> computeNullRoutedIps(
      Map<String, Map<String, IpSpace>> nullRoutedIps) {
    return toImmutableMap(
        nullRoutedIps,
        Entry::getKey /* hostname */,
        nullRoutedIpsByHostnameEntry ->
            toImmutableMap(
                nullRoutedIpsByHostnameEntry.getValue(),
                Entry::getKey /* vrf */,
                nullRoutedIpsByVrfEntry -> {
                  IpSpace ipSpace =
                      _ipSpaceSpecializer.specialize(nullRoutedIpsByVrfEntry.getValue());
                  return new IpSpaceMatchExpr(ipSpace, false, true);
                }));
  }

  private Map<String, Map<String, String>> computeOutgoingAcls() {
    return toImmutableMap(
        _enabledInterfaces,
        Entry::getKey,
        enabledInterfacesEntry -> {
          Configuration c = _configurations.get(enabledInterfacesEntry.getKey());
          return enabledInterfacesEntry
              .getValue()
              .stream()
              .filter(ifaceName -> c.getInterfaces().get(ifaceName).getOutgoingFilterName() != null)
              .collect(
                  ImmutableMap.toImmutableMap(
                      Function.identity(),
                      ifaceName -> c.getInterfaces().get(ifaceName).getOutgoingFilterName()));
        });
  }

  private Map<String, Map<String, BooleanExpr>> computeRoutableIps(
      Map<String, Map<String, IpSpace>> routableIps) {
    return toImmutableMap(
        routableIps,
        Entry::getKey /* hostname */,
        routableIpsByHostnameEntry ->
            toImmutableMap(
                routableIpsByHostnameEntry.getValue(),
                Entry::getKey /* vrf */,
                routableIpsByVrfEntry -> {
                  IpSpace ipSpace =
                      _ipSpaceSpecializer.specialize(routableIpsByVrfEntry.getValue());
                  return new IpSpaceMatchExpr(ipSpace, false, true);
                }));
  }

  private Map<String, Map<String, List<Entry<AclPermit, BooleanExpr>>>> computeSourceNats() {
    return toImmutableMap(
        _topologyInterfaces,
        Entry::getKey,
        topologyInterfacesEntryByHostname -> {
          String hostname = topologyInterfacesEntryByHostname.getKey();
          Set<String> ifaces = topologyInterfacesEntryByHostname.getValue();
          Configuration c = _configurations.get(hostname);
          return toImmutableMap(
              ifaces,
              Function.identity(),
              ifaceName ->
                  c.getInterfaces()
                      .get(ifaceName)
                      .getSourceNats()
                      .stream()
                      .map(
                          sourceNat -> {
                            IpAccessList acl = sourceNat.getAcl();
                            String aclName =
                                acl == null ? DEFAULT_SOURCE_NAT_ACL.getName() : acl.getName();
                            AclPermit preconditionPreTransformationState =
                                new AclPermit(hostname, aclName);
                            BooleanExpr transformationConstraint =
                                new RangeMatchExpr(
                                    TransformationHeaderField.NEW_SRC_IP,
                                    TransformationHeaderField.NEW_SRC_IP.getSize(),
                                    ImmutableSet.of(
                                        Range.closed(
                                            sourceNat.getPoolIpFirst().asLong(),
                                            sourceNat.getPoolIpLast().asLong())));
                            return Maps.immutableEntry(
                                preconditionPreTransformationState, transformationConstraint);
                          })
                      .collect(ImmutableList.toImmutableList()));
        });
  }

  private Map<String, Set<String>> computeTopologyInterfaces() {
    Map<String, Set<String>> topologyEdges = new HashMap<>();
    _enabledEdges.forEach(
        enabledEdge ->
            topologyEdges
                .computeIfAbsent(enabledEdge.getNode1(), n -> new HashSet<>())
                .add(enabledEdge.getInt1()));
    // freeze
    return toImmutableMap(topologyEdges, Entry::getKey, e -> ImmutableSet.copyOf(e.getValue()));
  }

  @Override
  public Map<String, Map<String, List<LineAction>>> getAclActions() {
    return _aclActions;
  }

  /**
   * Mapping: hostname -> aclName -> lineNumber -> lineConditions <br>
   * lineConditions is a boolean expression representing the constraints on a header necessary for
   * that line to be matched.
   */
  @Override
  public Map<String, Map<String, List<BooleanExpr>>> getAclConditions() {
    return _aclConditions;
  }

  public Map<String, Map<String, Map<String, Map<String, Map<String, BooleanExpr>>>>>
      getArpTrueEdge() {
    return _arpTrueEdge;
  }

  @Override
  public Set<Edge> getEnabledEdges() {
    return _enabledEdges;
  }

  @Override
  public Map<String, Set<String>> getEnabledInterfaces() {
    return _enabledInterfaces;
  }

  @Override
  public Map<String, Map<String, Set<String>>> getEnabledInterfacesByNodeVrf() {
    return _enabledInterfacesByNodeVrf;
  }

  @Override
  public Set<String> getEnabledNodes() {
    return _enabledNodes;
  }

  @Override
  public Map<String, Set<String>> getEnabledVrfs() {
    return _enabledVrfs;
  }

  @Override
  public Map<String, Map<String, String>> getIncomingAcls() {
    return _incomingAcls;
  }

  @Override
  public Map<String, Set<Ip>> getIpsByHostname() {
    return _ipsByHostname;
  }

  public Map<String, Map<String, Map<String, BooleanExpr>>> getNeighborUnreachable() {
    return _neighborUnreachable;
  }

  @Override
  public Map<String, Map<String, BooleanExpr>> getNullRoutedIps() {
    return _nullRoutedIps;
  }

  @Override
  public Map<String, Map<String, String>> getOutgoingAcls() {
    return _outgoingAcls;
  }

  @Override
  public Map<String, Map<String, BooleanExpr>> getRoutableIps() {
    return _routableIps;
  }

  @Override
  public boolean getSimplify() {
    return _simplify;
  }

  @Override
  public Map<String, Map<String, List<Entry<AclPermit, BooleanExpr>>>> getSourceNats() {
    return _sourceNats;
  }

  @Override
  public Map<String, Set<String>> getTraversableInterfaces() {
    return _topologyInterfaces;
  }

  @Override
  public Set<Type> getVectorizedParameters() {
    return _vectorizedParameters;
  }
}
