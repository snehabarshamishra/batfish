package org.batfish.z3;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.ForwardingAction;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.z3.expr.AndExpr;
import org.batfish.z3.expr.BasicRuleStatement;
import org.batfish.z3.expr.BooleanExpr;
import org.batfish.z3.expr.QueryStatement;
import org.batfish.z3.expr.RuleStatement;
import org.batfish.z3.expr.SaneExpr;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.Accept;
import org.batfish.z3.state.Debug;
import org.batfish.z3.state.Drop;
import org.batfish.z3.state.DropAcl;
import org.batfish.z3.state.DropAclIn;
import org.batfish.z3.state.DropAclOut;
import org.batfish.z3.state.DropNoRoute;
import org.batfish.z3.state.DropNullRoute;
import org.batfish.z3.state.NeighborUnreachable;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.NodeDrop;
import org.batfish.z3.state.NodeDropAcl;
import org.batfish.z3.state.NodeDropAclIn;
import org.batfish.z3.state.NodeDropAclOut;
import org.batfish.z3.state.NodeDropNoRoute;
import org.batfish.z3.state.NodeDropNullRoute;
import org.batfish.z3.state.NodeNeighborUnreachable;
import org.batfish.z3.state.Query;

public class StandardReachabilityQuerySynthesizer extends ReachabilityQuerySynthesizer {

  public static class Builder
      extends ReachabilityQuerySynthesizer.Builder<
          StandardReachabilityQuerySynthesizer, StandardReachabilityQuerySynthesizer.Builder> {

    private Set<ForwardingAction> _actions;

    private Set<String> _finalNodes;

    @Override
    public StandardReachabilityQuerySynthesizer build() {
      return new StandardReachabilityQuerySynthesizer(
          _actions,
          _headerSpace,
          _finalNodes,
          _ingressNodeVrfs,
          _srcNatted,
          _transitNodes,
          _nonTransitNodes);
    }

    @Override
    public Builder getThis() {
      return this;
    }

    @Override
    public Builder setActions(Set<ForwardingAction> actions) {
      _actions = actions;
      return this;
    }

    @Override
    public Builder setFinalNodes(Set<String> finalNodes) {
      _finalNodes = finalNodes;
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final Set<ForwardingAction> _actions;

  private final Set<String> _finalNodes;

  private StandardReachabilityQuerySynthesizer(
      @Nonnull Set<ForwardingAction> actions,
      @Nonnull HeaderSpace headerSpace,
      @Nonnull Set<String> finalNodes,
      @Nonnull Map<String, Set<String>> ingressNodeVrfs,
      Boolean srcNatted,
      @Nonnull Set<String> transitNodes,
      @Nonnull Set<String> nonTransitNodes) {
    super(headerSpace, ingressNodeVrfs, srcNatted, transitNodes, nonTransitNodes);
    _actions = actions;
    _finalNodes = finalNodes;
  }

  /** Create query condition for action at final node(s) */
  private List<StateExpr> computeFinalActions() {
    ImmutableList.Builder<StateExpr> finalActionsBuilder = ImmutableList.builder();
    for (ForwardingAction action : _actions) {
      switch (action) {
        case ACCEPT:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr accept = new NodeAccept(finalNode);
              finalActionsBuilder.add(accept);
            }
          } else {
            finalActionsBuilder.add(Accept.INSTANCE);
          }
          break;

        case DEBUG:
          finalActionsBuilder.add(Debug.INSTANCE);
          break;

        case DROP:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeDrop(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(Drop.INSTANCE);
          }
          break;

        case DROP_ACL:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeDropAcl(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(DropAcl.INSTANCE);
          }
          break;

        case DROP_ACL_IN:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeDropAclIn(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(DropAclIn.INSTANCE);
          }
          break;

        case DROP_ACL_OUT:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeDropAclOut(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(DropAclOut.INSTANCE);
          }
          break;

        case DROP_NO_ROUTE:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeDropNoRoute(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(DropNoRoute.INSTANCE);
          }
          break;

        case DROP_NULL_ROUTE:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeDropNullRoute(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(DropNullRoute.INSTANCE);
          }
          break;

        case NEIGHBOR_UNREACHABLE_OR_EXITS_NETWORK:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              StateExpr drop = new NodeNeighborUnreachable(finalNode);
              finalActionsBuilder.add(drop);
            }
          } else {
            finalActionsBuilder.add(NeighborUnreachable.INSTANCE);
          }
          break;

        case FORWARD:
        default:
          throw new BatfishException("unsupported action");
      }
    }
    return finalActionsBuilder.build();
  }

  @Override
  public ReachabilityProgram getReachabilityProgram(SynthesizerInput input) {
    ImmutableList.Builder<RuleStatement> rules = ImmutableList.builder();
    List<StateExpr> finalActions = computeFinalActions();
    ImmutableList.Builder<BooleanExpr> queryPreconditions =
        ImmutableList.<BooleanExpr>builder().add(SaneExpr.INSTANCE).add(getSrcNattedConstraint());

    finalActions
        .stream()
        .map(
            finalAction ->
                new BasicRuleStatement(
                    new AndExpr(queryPreconditions.build()),
                    ImmutableSet.of(finalAction),
                    Query.INSTANCE))
        .forEach(rules::add);
    addOriginateRules(rules);
    return ReachabilityProgram.builder()
        .setInput(input)
        .setQueries(ImmutableList.of(new QueryStatement(Query.INSTANCE)))
        .setRules(rules.build())
        .build();
  }
}
