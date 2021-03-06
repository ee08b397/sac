package com.erfangc.sac.core.backend.inmemory;

import com.erfangc.sac.core.backend.Backend;
import com.erfangc.sac.interfaces.*;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

public class InMemoryBackend implements Backend {

    private Map<String, Group> groups;
    private Map<String, IdentityPolicy> policies;

    private Map<String, Map<String, String>> groupToPrincipalMap;
    private Map<String, Map<String, Group>> groupToGroupMap;
    private Map<String, Map<String, Group>> principalToGroupMap;
    private Map<String, Map<String, String>> policyToPrincipalMap;
    private Map<String, Map<String, IdentityPolicy>> principalToPolicyMap;
    private Map<String, ImmutableResourcePolicy> resourcePolicyMap;

    public InMemoryBackend() {
        policies = new HashMap<>();
        groups = new HashMap<>();
        groupToGroupMap = new HashMap<>();
        groupToPrincipalMap = new HashMap<>();
        principalToGroupMap = new HashMap<>();
        policyToPrincipalMap = new HashMap<>();
        principalToPolicyMap = new HashMap<>();
        resourcePolicyMap = new HashMap<>();
    }

    private static ImmutableResourcePolicy newPolicy(String resource) {
        return ImmutableResourcePolicy
                .builder()
                .resource(resource)
                .description("Policy to protect " + resource)
                .assignments(emptyList())
                .build();
    }

    @Override
    public void createGroup(Group group) {
        groups.put(group.id(), group);
    }

    @Override
    public Group getGroup(String groupId) {
        if (!groups.containsKey(groupId)) {
            return null;
        }
        final Map<String, Group> g2g = groupToGroupMap.getOrDefault(groupId, new HashMap<>());
        final Map<String, String> g2p = groupToPrincipalMap.getOrDefault(groupId, new HashMap<>());
        List<GroupAssignment> assignments = new ArrayList<>();
        g2g.values().forEach(a -> assignments.add(ImmutableGroupAssignment.builder().principal(a.id()).principalIsGroup(true).groupId(groupId).build()));
        g2p.values().forEach(p -> assignments.add(ImmutableGroupAssignment.builder().principal(p).groupId(groupId).build()));
        return ImmutableGroup.copyOf(groups.get(groupId))
                .withAssignments(assignments);
    }

    @Override
    public void updateGroup(Group group) {
        groups.put(group.id(), group);
    }

    @Override
    public synchronized void deleteGroup(String groupId) {
        final Set<String> principals = groupToPrincipalMap.getOrDefault(groupId, emptyMap()).keySet();
        principals.forEach(principal -> {
            groupToGroupMap.getOrDefault(principal, emptyMap()).remove(groupId);
            principalToGroupMap.getOrDefault(principal, emptyMap()).remove(groupId);
        });
        groups.remove(groupId);
    }

    @Override
    public synchronized void assignPrincipalToGroup(String groupId, String principalId) {
        this.assignPrincipalToGroup(groupId, principalId, false);
    }

    @Override
    public synchronized void assignPrincipalToGroup(String groupId,
                                                    String principalId,
                                                    boolean principalIsGroup) {
        if (principalIsGroup) {
            final Map<String, Group> m1 = groupToGroupMap.getOrDefault(groupId, new HashMap<>());
            m1.put(principalId, this.getGroup(principalId));
            groupToGroupMap.put(groupId, m1);
        } else {
            final Map<String, String> m2 = groupToPrincipalMap.getOrDefault(groupId, new HashMap<>());
            m2.put(principalId, principalId);
            groupToPrincipalMap.put(groupId, m2);
        }
        final Map<String, Group> m3 = principalToGroupMap.getOrDefault(principalId, new HashMap<>());
        m3.put(groupId, this.getGroup(groupId));
        principalToGroupMap.put(principalId, m3);
    }

    @Override
    public synchronized void unassignPrincipalFromGroup(String groupId, String principalId) {
        final Map<String, Group> m1 = groupToGroupMap.getOrDefault(groupId, new HashMap<>());
        m1.remove(principalId);
        groupToGroupMap.put(groupId, m1);
        final Map<String, String> m2 = groupToPrincipalMap.getOrDefault(groupId, new HashMap<>());
        m2.remove(principalId);
        groupToPrincipalMap.put(groupId, m2);
        final Map<String, Group> m3 = principalToGroupMap.getOrDefault(principalId, new HashMap<>());
        m3.remove(groupId);
        principalToGroupMap.put(principalId, m3);
    }

    @Override
    public List<String> getAllPrincipalsForGroup(String groupId) {
        return new ArrayList<>(groupToPrincipalMap.get(groupId).keySet());
    }

    @Override
    public List<String> getGroupMembership(String principalId) {
        return new ArrayList<>(principalToGroupMap.get(principalId).keySet());
    }

    @Override
    public Node getGroupTree(String groupId) {
        Node root = new Node().setChildren(new ArrayList<>()).setName(groupId);
        Stack<Node> stack = new Stack<>();
        stack.add(root);
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            final Node node = stack.pop();
            seen.add(node.getName());
            if (groupToGroupMap.containsKey(node.getName())) {
                final Set<String> childGids = groupToGroupMap.get(node.getName()).keySet();
                for (String childGid : childGids) {
                    if (!seen.contains(childGid)) {
                        final Node childNode = new Node().setName(childGid).setChildren(new ArrayList<>());
                        node.getChildren().add(childNode);
                        stack.add(childNode);
                    }
                }
            }
        }
        return root;
    }

    @Override
    public void createPolicy(IdentityPolicy identityPolicy) {
        policies.put(identityPolicy.id(), identityPolicy);
    }

    @Override
    public IdentityPolicy getPolicy(String policyId) {
        return policies.get(policyId);
    }

    @Override
    public void updatePolicy(IdentityPolicy identityPolicy) {
        policies.put(identityPolicy.id(), identityPolicy);
    }

    @Override
    public synchronized void deletePolicy(String policyId) {
        policies.remove(policyId);
        // remove any associated policy maps
        policyToPrincipalMap.remove(policyId);
        // rebuild the principalToPolicy map, this is very inefficient
        final Map<String, Map<String, IdentityPolicy>> updatedPrincipalToPolicyMap = principalToPolicyMap
                .entrySet()
                .stream()
                .collect(
                        toMap(
                                Map.Entry::getKey,
                                s -> {
                                    final Map<String, IdentityPolicy> value = new HashMap<>(s.getValue());
                                    value.remove(policyId);
                                    return value;
                                })
                );
        principalToPolicyMap = updatedPrincipalToPolicyMap;
    }

    @Override
    public synchronized void assignPolicy(String policyId, String principalId) {
        final Map<String, String> m1 = policyToPrincipalMap.getOrDefault(policyId, new HashMap<>());
        final Map<String, IdentityPolicy> m2 = principalToPolicyMap.getOrDefault(principalId, new HashMap<>());
        m1.put(policyId, principalId);
        m2.put(policyId, getPolicy(policyId));
        policyToPrincipalMap.put(policyId, m1);
        principalToPolicyMap.put(principalId, m2);
    }

    @Override
    public synchronized void unAssignPolicy(String policyId, String principalId) {
        final Map<String, String> m1 = policyToPrincipalMap.getOrDefault(policyId, new HashMap<>());
        final Map<String, IdentityPolicy> m2 = principalToPolicyMap.getOrDefault(principalId, new HashMap<>());
        m1.remove(policyId);
        m2.remove(policyId);
    }

    @Override
    public List<String> getGroupMembershipTransitively(String principalId) {
        final Map<String, Group> m1 = principalToGroupMap.get(principalId);
        // BFS to to construct a list of group membership and transitive group memberships for this principal
        // gids = groupIds, represents the group Ids the BFS has seen so far, used to both keep track of the results of the
        // traversal but also mark visited 'nodes' and prevent them from being processed again
        Set<String> gids = new HashSet<>();
        if (m1 != null) {
            Queue<String> queue = m1.values().stream().map(Group::id).collect(toCollection(ArrayDeque::new));
            while (!queue.isEmpty()) {
                final String gid = queue.poll();
                gids.add(gid);
                if (principalToGroupMap.containsKey(gid)) {
                    for (String cGid : principalToGroupMap.get(gid).keySet()) {
                        if (!gids.contains(cGid)) {
                            queue.add(cGid);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(gids);
    }

    @Override
    public List<IdentityPolicy> fetchIdentityPoliciesTransitivelyForPrincipal(String principalId) {
        Set<IdentityPolicy> ret = principalToPolicyMap.containsKey(principalId) ? new HashSet<>(principalToPolicyMap
                .get(principalId)
                .values()) : new HashSet<>();
        final List<String> gids = getGroupMembershipTransitively(principalId);
        gids.forEach(gid -> ret.addAll(new ArrayList<>(principalToPolicyMap.getOrDefault(gid, emptyMap()).values())));
        return new ArrayList<>(ret);
    }

    @Override
    public synchronized void grantActions(String resource, String principal, Set<String> actions) {
        final ImmutableResourcePolicy policy = resourcePolicyMap.getOrDefault(resource, newPolicy(resource));
        // for each attached assignment, determine if the principal is already granted the actions
        final List<ResourcePolicyAssignment> resourcePolicyAssignments = policy.assignments().orElse(new ArrayList<>());
        boolean found = false;
        List<ResourcePolicyAssignment> results = new ArrayList<>();
        for (ResourcePolicyAssignment assignment : resourcePolicyAssignments) {
            if (assignment.principal().equals(principal)) {
                final Set<String> union = new HashSet<>(assignment.actions());
                union.addAll(actions);
                ImmutableResourcePolicyAssignment updated = ImmutableResourcePolicyAssignment
                        .copyOf(assignment)
                        .withActions(union);
                results.add(updated);
                found = true;
            } else {
                results.add(assignment);
            }
        }
        if (!found) {
            results.add(ImmutableResourcePolicyAssignment.builder().actions(actions).principal(principal).build());
        }
        resourcePolicyMap.put(resource, policy.withAssignments(results));
    }

    @Override
    public synchronized void revokeActions(String resource, String principal, Set<String> actions) {
        final ImmutableResourcePolicy policy = resourcePolicyMap.getOrDefault(resource, newPolicy(resource));
        // for each attached assignment, determine if the principal is already granted the actions
        final List<ResourcePolicyAssignment> resourcePolicyAssignments = policy.assignments().orElse(new ArrayList<>());
        List<ResourcePolicyAssignment> results = new ArrayList<>();
        for (ResourcePolicyAssignment assignment : resourcePolicyAssignments) {
            if (assignment.principal().equals(principal)) {
                final HashSet<String> removed = new HashSet<>(assignment.actions());
                removed.removeAll(actions);
                results.add(ImmutableResourcePolicyAssignment.copyOf(assignment).withActions(removed));
            } else {
                results.add(assignment);
            }
        }
        resourcePolicyMap.put(resource, policy.withAssignments(results));
    }

    @Override
    public ResourcePolicy getResourcePolicy(String resource) {
        return resourcePolicyMap.get(resource);
    }
}
